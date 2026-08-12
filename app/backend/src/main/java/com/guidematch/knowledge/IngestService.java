package com.guidematch.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Codex가 만든 실행 디렉터리 하나를 읽어 Postgres에 적재한다.
 *
 * <p><b>트랜잭션을 배치 전체에 걸지 않는다.</b> 줄 단위로 커밋해야 한 줄이 깨져도 나머지
 * 1,829줄이 살아남는다. 배치 전체를 한 트랜잭션으로 묶으면 마지막 줄의 오류가 앞의 모든
 * 성과를 되돌린다 — 25분짜리 수집을 통째로 버리는 셈이다.
 *
 * <p>파싱은 레코드 바인딩이 아니라 트리(JsonNode)로 한다. 계약에 없는 필드가 하나 섞였다고
 * 줄 전체를 버리면 프롬프트를 조금 고칠 때마다 적재가 멈춘다. 대신 필요한 필드만 꺼내 쓰고,
 * 못 꺼내면 <b>구체적인 사유와 함께</b> {@code _rejects.jsonl}로 보낸다.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private final PlaceResolver resolver;
    private final PlaceRepository placeRepo;
    private final PlaceAliasRepository aliasRepo;
    private final PlaceInsightRepository insightRepo;
    private final PlaceCandidateUnresolvedRepository unresolvedRepo;
    private final IngestRunRepository runRepo;
    private final IngestSourceRepository sourceRepo;

    /** 이 미만은 적재하지 않는다. 추측성 사실이 자산을 오염시키는 것보다 비어 있는 게 낫다. */
    private final double minConfidence;

    /**
     * 줄 묶음 하나를 한 트랜잭션으로 처리하기 위한 것.
     *
     * <p>{@code @Transactional}을 이 클래스의 메서드에 붙이면 자기 호출이라 프록시를 우회해
     * 아무 효과가 없다 — 그러면 저장이 줄마다 각자 트랜잭션이 되고
     * {@code hibernate.jdbc.batch_size}는 묶을 대상이 없어 <b>아무 일도 하지 않는다.</b>
     */
    private final TransactionTemplate txTemplate;

    /** 한 트랜잭션에 묶는 줄 수. 죽어도 손실이 이 크기를 넘지 않고, 재적재는 멱등이다. */
    private static final int CHUNK_LINES = 100;

    public IngestService(PlaceResolver resolver,
                         PlaceRepository placeRepo,
                         PlaceAliasRepository aliasRepo,
                         PlaceInsightRepository insightRepo,
                         PlaceCandidateUnresolvedRepository unresolvedRepo,
                         IngestRunRepository runRepo,
                         IngestSourceRepository sourceRepo,
                         TransactionTemplate txTemplate,
                         @Value("${ingest.min-confidence:0.5}") double minConfidence) {
        this.resolver = resolver;
        this.placeRepo = placeRepo;
        this.aliasRepo = aliasRepo;
        this.txTemplate = txTemplate;
        this.insightRepo = insightRepo;
        this.unresolvedRepo = unresolvedRepo;
        this.runRepo = runRepo;
        this.sourceRepo = sourceRepo;
        this.minConfidence = minConfidence;
    }

    public record Counts(
            int placesResolved,
            int placesUnresolved,
            int insightsUpserted,
            int insightsSkipped,
            int rejects
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("places_resolved", placesResolved);
            m.put("places_unresolved", placesUnresolved);
            m.put("insights_upserted", insightsUpserted);
            m.put("insights_skipped", insightsSkipped);
            m.put("rejects", rejects);
            return m;
        }
    }

    public Counts ingest(Path runDir) throws IOException {
        if (!Files.isDirectory(runDir)) {
            throw new IOException("실행 디렉터리가 없습니다: " + runDir);
        }

        JsonNode manifest = readManifest(runDir);
        String runId = text(manifest.path("run_id"), runDir.getFileName().toString());
        String sourceKind = text(manifest.path("source").path("kind"), null);
        String promptVersion = text(manifest.path("extractor").path("prompt_version"), null);
        String scopeKey = scopeKeyOf(manifest);

        warnAboutStalledRuns(runId);

        IngestRun run = runRepo.findByRunId(runId).orElseGet(() -> runRepo.save(
                new IngestRun(runId, sourceKind, jsonToMap(manifest.path("scope")), promptVersion)));

        Path rejects = runDir.resolve("_rejects.jsonl");
        Counter c = new Counter();

        // 레지스트리 스냅샷 — 읽기 왕복이 장소당 4회에서 0회가 된다.
        // 범위가 아니라 전체를 올리는 이유는 RegistrySnapshot 주석 참고(유니크 제약이 전역이다).
        RegistrySnapshot snapshot = RegistrySnapshot.loadAll(placeRepo, aliasRepo, sourceRepo);
        log.info("스냅샷 로드 완료 — 장소 {}건", snapshot.placeCount());

        try {
            // 장소를 먼저 적재해야 인사이트가 붙을 노드가 존재한다
            eachLine(runDir.resolve("places.jsonl"), rejects, c, (lineNo, raw, node) ->
                    ingestPlace(raw, node, runId, sourceKind, scopeKey, rejects, c, snapshot));

            eachLine(runDir.resolve("insights.jsonl"), rejects, c, (lineNo, raw, node) ->
                    ingestInsight(raw, node, runId, sourceKind, promptVersion, scopeKey, rejects, c, snapshot));

            markSeenUrls(snapshot, runId);

            Counts counts = c.snapshot();
            run.complete(counts.toMap());
            runRepo.save(run);
            log.info("적재 완료 run={} {}", runId, counts.toMap());
            return counts;
        } catch (RuntimeException e) {
            run.fail(e.toString());
            runRepo.save(run);
            throw e;
        }
    }

    /**
     * 이번 실행에서 다시 본 URL들의 커서를 왕복 한 번(청크당)으로 옮긴다.
     *
     * <p><b>왜 필요한가</b>: {@code touchSource}가 이미 아는 URL을 건너뛰어 왕복을 아끼는데,
     * 그대로 두면 {@code last_seen_at}이 멈춘다. 그 값은 {@code scope-progress.jsonl}로 나가
     * 에이전트의 갱신 주기 판정에 쓰이므로, 멈추면 <b>같은 범위가 계속 다시 뽑히는</b>
     * 조용한 고장이 된다. 이 프로젝트가 반복해서 밟은 커서 고장과 같은 부류다.
     */
    private void markSeenUrls(RegistrySnapshot snapshot, String runId) {
        List<String> hashes = List.copyOf(snapshot.touchedSourceHashes());
        if (hashes.isEmpty()) return;
        java.time.Instant now = java.time.Instant.now();
        // IN 목록이 무한정 커지지 않게 나눠 친다
        for (int i = 0; i < hashes.size(); i += 500) {
            List<String> slice = hashes.subList(i, Math.min(i + 500, hashes.size()));
            txTemplate.executeWithoutResult(st -> sourceRepo.markSeen(slice, runId, now));
        }
    }

    /**
     * 이전 실행이 쓰기 도중에 죽었는지 알린다.
     *
     * <p>codex 샌드박스는 세션이 끝나면 셸째로 프로세스를 없앤다. 그때 유실은 항상 파일의
     * <b>뒷부분</b>이고, {@code _rejects.jsonl}은 0바이트에 exit code는 0이라 밖에서는
     * 성공과 구분되지 않는다. DB에 {@code STARTED}로 남은 런이 그걸 아는 유일한 방법이다.
     *
     * <p>여기서 예외가 나도 적재를 막지 않는다 — 경고 기능이 본 작업을 죽이면 본말전도다.
     */
    private void warnAboutStalledRuns(String currentRunId) {
        try {
            List<IngestRun> stalled = runRepo.findByStatus(IngestRun.Status.STARTED).stream()
                    .filter(r -> !r.getRunId().equals(currentRunId))
                    .toList();
            if (stalled.isEmpty()) return;

            String ids = stalled.stream().map(IngestRun::getRunId).collect(Collectors.joining(", "));
            log.warn("⚠ 완료되지 않은 이전 적재 {}건 — 뒷부분이 유실됐을 수 있다: {}", stalled.size(), ids);
            System.out.println("⚠ 미완료 적재 " + stalled.size() + "건: " + ids
                    + " — 해당 run 디렉터리로 ingest.sh를 다시 돌리면 멱등하게 복구된다");
        } catch (Exception e) {
            log.warn("중단된 적재 확인 실패 — 무시하고 진행: {}", e.toString());
        }
    }

    // ── 장소 ────────────────────────────────────────────────────────

    private void ingestPlace(String raw, JsonNode n, String runId, String manifestSource,
                             String scopeKey, Path rejects, Counter c, RegistrySnapshot snapshot) {
        String nameRaw = text(n.path("name_raw"), null);
        if (nameRaw == null) {
            reject(rejects, raw, "name_raw 없음", c);
            return;
        }
        String sourceKind = text(n.path("source").path("kind"), manifestSource);

        PlaceClue clue = new PlaceClue(
                nameRaw,
                strings(n.path("aliases")),
                text(n.path("city"), null),
                text(n.path("district"), null),
                decimal(n.path("lat")),
                decimal(n.path("lng")),
                text(n.path("external_ids").path("kakao_place_id"), null),
                text(n.path("external_ids").path("tour_api_content_id"), null),
                text(n.path("category_raw"), null),
                text(n.path("address_raw"), null),
                sourceKind,
                // 사진과 발행처는 <b>같은 레코드의 다른 위치</b>에서 온다(image_url / source.publisher).
                // 둘 중 하나만 실려 오면 Place.applyImage가 사진을 버린다 — 출처 없이는 띄울 수 없다.
                text(n.path("image_url"), null),
                text(n.path("source").path("publisher"), null));

        PlaceResolver.Resolution r = resolver.resolve(clue, snapshot);
        if (r.isResolved()) {
            // 바뀐 게 없으면 저장하지 않는다 — detached 엔티티의 save()는 merge라
            // 행마다 SELECT를 한 번씩 낸다(Sydney 왕복 250ms). 재적재는 대개 여기서 0왕복이다.
            if (r.needsSave()) placeRepo.save(r.place());
            c.placesResolved++;
        } else {
            // 추측해서 합치지 않는다 — 원본 그대로 보관해두면 나중에 언제든 해결할 수 있다
            unresolvedRepo.save(new PlaceCandidateUnresolved(
                    text(n.path("record_id"), null), raw, r.unresolvedReason(), runId));
            c.placesUnresolved++;
        }
        touchSource(n.path("source"), sourceKind, scopeKey, runId, snapshot);
    }

    // ── 인사이트 ─────────────────────────────────────────────────────

    private void ingestInsight(String raw, JsonNode n, String runId, String manifestSource,
                               String promptVersion, String scopeKey, Path rejects, Counter c,
                               RegistrySnapshot snapshot) {
        String recordId = text(n.path("record_id"), null);
        if (recordId == null) {
            reject(rejects, raw, "record_id 없음 — 멱등성을 보장할 수 없음", c);
            return;
        }

        Optional<FactKind> kind = FactKind.fromWire(text(n.path("fact_kind"), null));
        if (kind.isEmpty()) {
            // 추출기가 새 값을 발명한 경우. 열거형이 무너지면 질의가 불가능해지므로 버린다
            reject(rejects, raw, "알 수 없는 fact_kind: " + text(n.path("fact_kind"), "(없음)"), c);
            return;
        }

        double confidence = n.path("confidence").asDouble(0.0);
        if (confidence < minConfidence) {
            c.insightsSkipped++;
            return;
        }

        JsonNode ref = n.path("place_ref");
        String nameRaw = text(ref.path("name_raw"), null);
        if (nameRaw == null) {
            reject(rejects, raw, "place_ref.name_raw 없음", c);
            return;
        }

        String sourceKind = text(n.path("source_kind"), manifestSource);
        PlaceResolver.Resolution r = resolver.resolve(new PlaceClue(
                nameRaw, List.of(),
                text(ref.path("city"), null), text(ref.path("district"), null),
                decimal(ref.path("lat")), decimal(ref.path("lng")),
                text(ref.path("external_ids").path("kakao_place_id"), null),
                text(ref.path("external_ids").path("tour_api_content_id"), null),
                // 인사이트의 place_ref는 장소를 <b>가리키기만</b> 한다 — 카테고리·주소·사진은
                // 장소 레코드가 정한다. 여기서 실어 보내면 사실 한 줄이 장소 속성을 바꾸게 된다.
                null, null, sourceKind, null, null), snapshot);

        if (!r.isResolved()) {
            // 장소를 모르는 사실은 붙일 데가 없다. 버리지 말고 보관 — 나중에 장소가 확정되면 살릴 수 있다
            unresolvedRepo.save(new PlaceCandidateUnresolved(
                    recordId, raw, "insight place_ref: " + r.unresolvedReason(), runId));
            c.placesUnresolved++;
            return;
        }
        Place place = r.needsSave() ? placeRepo.save(r.place()) : r.place();

        JsonNode evidence = n.path("evidence");
        PlaceInsight fresh = new PlaceInsight(
                recordId, place.getId(), kind.get(),
                jsonToMap(n.path("value")),
                noteI18n(n.path("note_i18n")),
                confidence,
                text(evidence.path("url"), null),
                text(evidence.path("publisher"), null),
                date(evidence.path("published_at")),
                sourceKind,
                text(n.path("extracted_by").path("prompt_version"), promptVersion),
                runId);

        // 같은 record_id면 최신 값이 이긴다 (이력은 남기지 않는다 — 설계 확정 사항)
        insightRepo.findByRecordId(recordId).ifPresentOrElse(
                existing -> {
                    existing.overwriteFrom(fresh);
                    insightRepo.save(existing);
                },
                () -> insightRepo.save(fresh));
        c.insightsUpserted++;

        touchSource(evidence, sourceKind, scopeKey, runId, snapshot);
    }

    // ── 소스 커서 ────────────────────────────────────────────────────

    /** 이 URL을 봤다고 기록한다 — 다음 실행의 커서가 된다. */
    private void touchSource(JsonNode sourceNode, String sourceKind, String scopeKey, String runId,
                             RegistrySnapshot snapshot) {
        String url = text(sourceNode.path("url"), null);
        if (url == null) return;
        String hash = sha256(url);

        // 이미 본 URL이면 갱신할 값이 last_seen_run/at뿐이다. 재적재 때마다 조회+수정으로
        // 왕복을 두 번 쓰느니 건너뛴다 — 커서의 의미("이 URL을 봤다")는 이미 기록돼 있고,
        // ingested-sources.jsonl도 그 사실만 쓴다.
        if (snapshot.isLoaded()) {
            // 이미 있는 URL이면 여기서 갱신하지 않는다 — 행마다 조회+수정이면 왕복이 두 배다.
            // 대신 해시를 모아 실행 끝에 벌크 UPDATE 한 번으로 커서를 옮긴다(markSeenUrls).
            if (!snapshot.hasSourceHash(hash)) {
                sourceRepo.save(new IngestSource(hash, url, sourceKind, scopeKey, runId));
                snapshot.rememberSourceHash(hash);
            }
            snapshot.rememberTouched(hash);
            return;
        }
        sourceRepo.findByUrlHash(hash).ifPresentOrElse(
                s -> {
                    s.touch(runId);
                    sourceRepo.save(s);
                },
                () -> sourceRepo.save(new IngestSource(hash, url, sourceKind, scopeKey, runId)));
    }

    // ── 파일 · 파싱 도우미 ────────────────────────────────────────────

    private interface LineHandler {
        void handle(int lineNo, String raw, JsonNode node);
    }

    /**
     * 줄 단위 스트리밍. 파일 전체를 메모리에 올리지 않고, 한 줄의 실패가 다음 줄에 번지지 않는다.
     * 파일이 없으면 조용히 건너뛴다 — 장소만 있고 인사이트가 없는 실행도 정상이다.
     */
    private void eachLine(Path file, Path rejects, Counter c, LineHandler handler) throws IOException {
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<String> chunk = new ArrayList<>(CHUNK_LINES);
            int lineNo = 0, chunkStart = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                chunk.add(line);
                if (chunk.size() >= CHUNK_LINES) {
                    handleChunk(file, chunk, chunkStart, rejects, c, handler);
                    chunk.clear();
                    chunkStart = lineNo + 1;
                }
            }
            if (!chunk.isEmpty()) handleChunk(file, chunk, chunkStart, rejects, c, handler);
        }
    }

    /**
     * 줄 묶음 하나를 <b>한 트랜잭션</b>에서 처리한다.
     *
     * <p>배치 전체를 한 트랜잭션으로 묶지 않는 이유는 그대로다 — 마지막 줄의 오류가 앞의 성과를
     * 통째로 되돌리면 25분짜리 수집을 버리는 셈이다. {@link #CHUNK_LINES}줄 단위면 최악의
     * 손실이 그만큼이고, 재적재는 멱등이라 그마저 복구된다.
     *
     * <p>대신 이 묶음이 있어야 {@code hibernate.jdbc.batch_size}가 실제로 동작한다.
     * 트랜잭션이 줄마다 끊기면 묶을 대상이 없어 설정이 <b>아무 일도 하지 않는다.</b>
     *
     * <p>줄 단위 try/catch는 트랜잭션 <b>안</b>에 둔다 — 한 줄의 예외로 묶음 전체가 롤백되면
     * 부분 실패 격리라는 성질이 사라진다.
     */
    private void handleChunk(Path file, List<String> lines, int firstLineNo,
                             Path rejects, Counter c, LineHandler handler) {
        txTemplate.executeWithoutResult(status -> {
            int lineNo = firstLineNo;
            for (String raw : lines) {
                try {
                    handler.handle(lineNo, raw, mapper.readTree(raw));
                } catch (Exception e) {
                    // log.warn만 하면 rejects=0 · _rejects.jsonl 0바이트 · exit 0으로
                    // 유실이 완전히 은폐된다. 실제로 그 상태를 한 번 겪었다.
                    log.warn("{}:{} 줄 처리 실패 — {}", file.getFileName(), lineNo, e.toString());
                    reject(rejects, raw, "줄 처리 실패: " + e, c);
                }
                lineNo++;
            }
        });
    }

    private JsonNode readManifest(Path runDir) throws IOException {
        Path f = runDir.resolve("manifest.json");
        if (!Files.exists(f)) return mapper.createObjectNode();
        return mapper.readTree(Files.readString(f, StandardCharsets.UTF_8));
    }

    /**
     * 거절된 줄은 버리지 않고 사유와 함께 남긴다 — 프롬프트가 어디서 깨지는지 알려주는
     * 유일한 단서이고, 이게 없으면 추출 품질을 개선할 방법이 없다.
     */
    private void reject(Path rejects, String raw, String reason, Counter c) {
        c.rejects++;
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("reason", reason);
            entry.put("line", raw);
            Files.writeString(rejects, mapper.writeValueAsString(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("_rejects.jsonl 기록 실패: {}", e.toString());
        }
    }

    private static String scopeKeyOf(JsonNode manifest) {
        String city = text(manifest.path("scope").path("city"), "");
        String district = text(manifest.path("scope").path("district"), "");
        return district.isEmpty() ? city : city + "/" + district;
    }

    private static String text(JsonNode n, String fallback) {
        if (n == null || n.isMissingNode() || n.isNull()) return fallback;
        String s = n.asText();
        return s.isBlank() ? fallback : s;
    }

    private static Double decimal(JsonNode n) {
        return (n == null || n.isMissingNode() || n.isNull() || !n.isNumber()) ? null : n.asDouble();
    }

    private static LocalDate date(JsonNode n) {
        String s = text(n, null);
        if (s == null) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> strings(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        arr.forEach(x -> {
            String s = text(x, null);
            if (s != null) out.add(s);
        });
        return out;
    }

    private Map<String, Object> jsonToMap(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull() || !n.isObject()) return null;
        return mapper.convertValue(n, LinkedHashMap.class);
    }

    private Map<String, String> noteI18n(JsonNode n) {
        if (n == null || !n.isObject()) return null;
        Map<String, String> out = new HashMap<>();
        n.fields().forEachRemaining(e -> {
            String v = text(e.getValue(), null);
            if (v != null) out.put(e.getKey(), v);
        });
        return out.isEmpty() ? null : out;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Counter {
        int placesResolved, placesUnresolved, insightsUpserted, insightsSkipped, rejects;

        Counts snapshot() {
            return new Counts(placesResolved, placesUnresolved, insightsUpserted, insightsSkipped, rejects);
        }
    }
}
