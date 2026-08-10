package com.guidematch.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DB의 {@code ingest_sources}를 {@code state/ingested-sources.jsonl}로 내보낸다.
 *
 * <p>이 파일이 <b>다음 실행의 커서</b>다. Codex는 이걸 읽고 "아직 안 한 범위"를 스스로 고른다.
 * 커서를 파일이 아니라 DB에 두고 매번 내보내는 이유: 파일이 지워지거나 맥을 바꿔도 진행
 * 상황이 살아남아야 하고, 파일과 DB가 어긋났을 때 <b>DB가 항상 이겨야</b> 하기 때문이다.
 */
@Service
public class IngestStateExporter {

    private static final Logger log = LoggerFactory.getLogger(IngestStateExporter.class);

    private final IngestSourceRepository sourceRepo;
    private final IngestRunRepository runRepo;
    private final PlaceRepository placeRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public IngestStateExporter(IngestSourceRepository sourceRepo, IngestRunRepository runRepo,
                               PlaceRepository placeRepo) {
        this.sourceRepo = sourceRepo;
        this.runRepo = runRepo;
        this.placeRepo = placeRepo;
    }

    /**
     * 레지스트리 장소 이름을 {@code state/registry-places.jsonl}로 내보낸다.
     *
     * <p><b>역방향 시딩의 질의어 목록이다.</b> 에이전트가 "이미 가진 Kakao 장소명으로
     * TourAPI를 역조회"하려면 그 이름을 어디선가 읽어야 하는데, {@code ingested-sources.jsonl}에는
     * URL만 있고 이름이 없다. 이 파일이 없으면 프롬프트의 역방향 시딩 지시가 <b>수행 불가능한
     * 지시</b>가 되고, 그러면 에이전트는 결국 areaBasedList2 페이징으로 되돌아간다
     * (= 제목순 표본 잘림이 재발한다).
     *
     * <p>어느 외부 ID를 이미 갖고 있는지도 함께 싣는다 — 둘 다 가진 장소는 역조회할 이유가 없다.
     */
    private void exportRegistryPlaces(Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        int written = 0;
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (Place p : placeRepo.findAll()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name_ko", p.getNameKo());
                row.put("city", p.getCity());
                row.put("district", p.getDistrict());
                row.put("place_kind", p.getPlaceKind() == null ? null : p.getPlaceKind().name());
                row.put("has_kakao_id", p.getKakaoPlaceId() != null);
                row.put("has_tour_api_id", p.getTourApiContentId() != null);
                w.write(mapper.writeValueAsString(row));
                w.newLine();
                written++;
            }
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("레지스트리 장소 {}건 → {}", written, file);
    }

    /**
     * 완료되지 않은 적재를 {@code state/stalled-runs.jsonl}로 내보낸다.
     *
     * <p>로그 경고는 사람이 볼 때만 쓸모가 있다. <b>다음 Codex 세션이 스스로 재적재를 고르려면
     * 파일로 있어야 한다</b> — 커서를 DB에 두고 매번 파일로 내보내는 이 파이프라인의 규약 그대로다.
     *
     * <p><b>정상 상태에서 이 파일은 0바이트다.</b> 항상 쓰기 때문에 "비어 있다"가
     * "중단된 적재가 없다"는 적극적 신호가 된다 — 파일이 아예 없는 것과는 다르다.
     */
    private void exportStalledRuns(Path file) throws IOException {
        // 원자적 교체 — 쓰는 도중에 읽히면 반쪽 파일이 커서가 된다
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        int stalled = 0;
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (IngestRun r : runRepo.findByStatus(IngestRun.Status.STARTED)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("run_id", r.getRunId());
                row.put("source_kind", r.getSourceKind());
                row.put("scope", r.getScope());
                row.put("prompt_version", r.getPromptVersion());
                w.write(mapper.writeValueAsString(row));
                w.newLine();
                stalled++;
            }
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (stalled > 0) log.warn("미완료 적재 {}건을 {}에 기록했다", stalled, file);
    }

    public int export(Path stateFile) throws IOException {
        Files.createDirectories(stateFile.getParent());
        int written = 0;
        // 원자적 교체 — 쓰는 도중에 Codex가 읽어 반쪽 파일을 커서로 삼는 일을 막는다
        Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");

        // (소스 × 범위)별 요약을 같은 순회에서 모은다. 별도 질의를 하지 않는 이유:
        // 두 파일이 서로 다른 시점의 DB를 보면 커서가 자기모순에 빠진다.
        Map<String, ScopeProgress> byScope = new LinkedHashMap<>();

        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (IngestSource s : sourceRepo.findAll()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("url", s.getUrl());
                row.put("source_kind", s.getSourceKind());
                row.put("scope_key", s.getScopeKey());
                row.put("last_seen_run", s.getLastSeenRun());
                row.put("last_seen_at", s.getLastSeenAt().toString());
                w.write(mapper.writeValueAsString(row));
                w.newLine();
                written++;
                accumulate(byScope, s);
            }
        }
        Files.move(tmp, stateFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("state 내보내기 완료 {}건 → {}", written, stateFile);

        exportScopeProgress(stateFile.resolveSibling("scope-progress.jsonl"), byScope);
        exportStalledRuns(stateFile.resolveSibling("stalled-runs.jsonl"));
        exportRegistryPlaces(stateFile.resolveSibling("registry-places.jsonl"));
        return written;
    }

    /**
     * (소스 × 범위) 하나의 누적 상태.
     *
     * <p>시각을 문자열이 아니라 {@link Instant}로 들고 있는 이유: {@code Instant.toString()}은
     * 나노초가 0이면 소수부를 아예 빼고 쓴다. 그래서 문자열로 비교하면
     * {@code "…05:00:00Z"}가 {@code "…05:00:00.5Z"}보다 <b>뒤</b>로 정렬된다('.' &lt; 'Z').
     * 직렬화는 쓸 때 한 번만 한다.
     */
    private static final class ScopeProgress {
        final String scopeKey;
        final String sourceKind;
        int urls;
        Instant lastSeenAt;
        String lastSeenRun;

        ScopeProgress(IngestSource s) {
            this.scopeKey = s.getScopeKey();
            this.sourceKind = s.getSourceKind();
            this.lastSeenAt = s.getLastSeenAt();
            this.lastSeenRun = s.getLastSeenRun();
        }

        Map<String, Object> toRow() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scope_key", scopeKey);
            m.put("source_kind", sourceKind);
            m.put("urls", urls);
            m.put("last_seen_run", lastSeenRun);
            m.put("last_seen_at", lastSeenAt.toString());
            return m;
        }
    }

    private void accumulate(Map<String, ScopeProgress> byScope, IngestSource s) {
        String key = s.getSourceKind() + "|" + s.getScopeKey();
        ScopeProgress agg = byScope.computeIfAbsent(key, k -> new ScopeProgress(s));
        agg.urls++;
        // 그 범위에서 가장 최근에 본 시각이 남아야 갱신 주기 판정이 맞는다
        if (s.getLastSeenAt().isAfter(agg.lastSeenAt)) {
            agg.lastSeenAt = s.getLastSeenAt();
            agg.lastSeenRun = s.getLastSeenRun();
        }
    }

    /**
     * 계획용 요약 커서 — Codex는 매 실행에서 <b>이 파일만</b> 읽고 다음 범위를 고른다.
     *
     * <p>URL 단위 파일을 계획에 쓰면 커버리지가 늘수록 수천 줄이 되어, 에이전트가 예산을
     * 읽는 데 쓰거나 잘라 읽고 이미 한 범위를 다시 고른다. 요약은 (소스 × 범위) 하나당
     * 한 줄이라 전부 채워도 수십 줄에 그친다. URL 파일은 범위 안 중복 회피용으로 남는다.
     */
    private void exportScopeProgress(Path file, Map<String, ScopeProgress> byScope) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (ScopeProgress p : byScope.values()) {
                w.write(mapper.writeValueAsString(p.toRow()));
                w.newLine();
            }
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("범위 진행 요약 {}건 → {}", byScope.size(), file);
    }
}
