package com.guidematch.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 적재기 테스트 — 여기서 지키려는 성질은 셋이다.
 * <ol>
 *   <li><b>멱등성</b>: 같은 파일을 두 번 적재해도 행이 안 늘고, 값이 바뀌면 최신이 이긴다</li>
 *   <li><b>부분 실패 격리</b>: 깨진 줄 하나가 나머지 줄을 죽이지 않는다</li>
 *   <li><b>보수성</b>: 확신 없는 사실(낮은 confidence·모르는 fact_kind)은 들이지 않는다</li>
 * </ol>
 */
class IngestServiceTest {

    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final PlaceAliasRepository aliasRepo = mock(PlaceAliasRepository.class);
    private final PlaceInsightRepository insightRepo = mock(PlaceInsightRepository.class);
    private final PlaceCandidateUnresolvedRepository unresolvedRepo =
            mock(PlaceCandidateUnresolvedRepository.class);
    private final IngestRunRepository runRepo = mock(IngestRunRepository.class);
    private final IngestSourceRepository sourceRepo = mock(IngestSourceRepository.class);

    private final PlaceResolver resolver = new PlaceResolver(placeRepo, aliasRepo, 200, 2000);

    /**
     * 청크 트랜잭션 경계. 콜백을 그 자리에서 실행하도록 스텁하지 않으면
     * <b>적재 본문이 아예 안 돌아 모든 테스트가 "아무 일도 안 함"으로 통과</b>한다.
     */
    private final org.springframework.transaction.support.TransactionTemplate txTemplate =
            mock(org.springframework.transaction.support.TransactionTemplate.class);

    private final IngestService service = new IngestService(
            resolver, placeRepo, aliasRepo, insightRepo, unresolvedRepo, runRepo, sourceRepo,
            txTemplate, 0.5);

    /** record_id → 저장된 인사이트. 실제 DB의 unique 제약을 흉내 낸다. */
    private final Map<String, PlaceInsight> insightStore = new HashMap<>();

    @TempDir
    Path runDir;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        doAnswer(inv -> {
            ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>)
                    inv.getArgument(0)).accept(null);
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        // 스냅샷 로드용 — 테스트는 빈 레지스트리에서 시작한다
        when(placeRepo.findAll()).thenReturn(List.of());
        when(aliasRepo.findAll()).thenReturn(List.of());
        when(sourceRepo.findAll()).thenReturn(List.of());

        long[] nextId = {1L};
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> {
            Place p = inv.getArgument(0);
            if (p.getId() == null) ReflectionTestUtils.setField(p, "id", nextId[0]++);
            return p;
        });
        when(placeRepo.findByNameNormalized(anyString())).thenReturn(List.of());
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());
        when(placeRepo.findByKakaoPlaceId(anyString())).thenReturn(Optional.empty());
        when(runRepo.save(any(IngestRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(runRepo.findByRunId(anyString())).thenReturn(Optional.empty());
        when(sourceRepo.findByUrlHash(anyString())).thenReturn(Optional.empty());

        when(insightRepo.findByRecordId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(insightStore.get(inv.<String>getArgument(0))));
        when(insightRepo.save(any(PlaceInsight.class))).thenAnswer(inv -> {
            PlaceInsight i = inv.getArgument(0);
            insightStore.put(i.getRecordId(), i);
            return i;
        });
    }

    // ── 픽스처 ──────────────────────────────────────────────────────

    private void manifest() throws IOException {
        write("manifest.json", """
            {"schema_version":"1.0","run_id":"2026-07-31T03-00Z-kakao-seoul-seongdong",
             "source":{"kind":"kakao_local"},
             "extractor":{"agent":"codex-cli","prompt_version":"insight-v1"},
             "scope":{"city":"seoul","district":"성동구"}}
            """);
    }

    private String placeLine() {
        return """
            {"record_id":"sha256:p1","record_type":"place","name_raw":"어니언 성수",\
            "aliases":["Onion Seongsu"],"city":"seoul","district":"성동구",\
            "lat":37.5445,"lng":127.0557,"external_ids":{"kakao_place_id":"1234567"},\
            "category_raw":"음식점 > 카페","source":{"url":"https://example.com/a"},"confidence":0.9}""";
    }

    private String insightLine(String recordId, String factKind, int minutes, double confidence) {
        return """
            {"record_id":"%s","record_type":"insight",\
            "place_ref":{"name_raw":"어니언 성수","lat":37.5445,"lng":127.0557,\
            "external_ids":{"kakao_place_id":"1234567"}},\
            "fact_kind":"%s","value":{"minutes":%d},\
            "note_i18n":{"ko":"주말 오후 대기 30분 내외"},\
            "evidence":{"url":"https://example.com/a","published_at":"2026-04-12"},\
            "confidence":%s}""".formatted(recordId, factKind, minutes, confidence);
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(runDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    // ── 기본 경로 ────────────────────────────────────────────────────

    @Test
    void ingestsPlacesAndInsights() throws IOException {
        manifest();
        write("places.jsonl", placeLine());
        write("insights.jsonl", insightLine("sha256:i1", "wait_time", 30, 0.7));

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.placesResolved()).isEqualTo(1);
        assertThat(counts.insightsUpserted()).isEqualTo(1);
        assertThat(insightStore).hasSize(1);
        assertThat(insightStore.get("sha256:i1").getFactKind()).isEqualTo(FactKind.WAIT_TIME);
        assertThat(insightStore.get("sha256:i1").getNoteI18n()).containsKey("ko");
    }

    @Test
    void scopeKey_withoutDistrict_isCityOnly() throws IOException {
        // 제주·경주·강릉처럼 구가 없는 도시. 여기서 "Jeju/"나 "Jeju/null"이 나오면
        // Codex가 targets.yml의 `district: ~`를 보고 만드는 "Jeju"와 영원히 안 맞고,
        // 21개 타깃 중 8개가 계속 "안 한 것"으로 남아 매 실행 다시 수집된다.
        write("manifest.json", """
            {"schema_version":"1.0","run_id":"2026-07-31T03-00Z-tour-jeju",
             "source":{"kind":"tour_api"},
             "extractor":{"agent":"codex-cli","prompt_version":"insight-v2"},
             "scope":{"city":"Jeju","district":null}}
            """);
        write("places.jsonl", placeLine());

        service.ingest(runDir);

        ArgumentCaptor<IngestSource> saved = ArgumentCaptor.forClass(IngestSource.class);
        verify(sourceRepo).save(saved.capture());
        assertThat(saved.getValue().getScopeKey()).isEqualTo("Jeju");
    }

    // ── 멱등성 ──────────────────────────────────────────────────────

    @Test
    void reingestingSameFile_doesNotDuplicate() throws IOException {
        manifest();
        write("places.jsonl", placeLine());
        write("insights.jsonl", insightLine("sha256:i1", "wait_time", 30, 0.7));

        service.ingest(runDir);
        service.ingest(runDir);

        assertThat(insightStore)
                .as("record_id가 같으면 행이 늘지 않아야 한다")
                .hasSize(1);
    }

    @Test
    void reingestingChangedValue_overwrites_keepingOneRow() throws IOException {
        manifest();
        write("places.jsonl", placeLine());
        write("insights.jsonl", insightLine("sha256:i1", "wait_time", 30, 0.7));
        service.ingest(runDir);

        // 같은 record_id, value만 다름 — 블로그 글이 수정된 상황
        write("insights.jsonl", insightLine("sha256:i1", "wait_time", 45, 0.7));
        service.ingest(runDir);

        assertThat(insightStore).hasSize(1);
        assertThat(insightStore.get("sha256:i1").getValue())
                .as("최신 값이 이긴다 — 이력은 남기지 않는다")
                .containsEntry("minutes", 45);
    }

    // ── 부분 실패 격리 ────────────────────────────────────────────────

    @Test
    void brokenLine_doesNotKillTheRest_andIsRecorded() throws IOException {
        manifest();
        write("places.jsonl", placeLine());
        write("insights.jsonl", String.join("\n",
                insightLine("sha256:i1", "wait_time", 30, 0.7),
                "{ 이건 JSON이 아니다 ",
                insightLine("sha256:i2", "best_time", 10, 0.8)));

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.insightsUpserted())
                .as("깨진 줄 하나가 나머지를 죽이면 안 된다")
                .isEqualTo(2);
        assertThat(insightStore).containsKeys("sha256:i1", "sha256:i2");
    }

    @Test
    void unknownFactKind_isRejectedWithReason() throws IOException {
        manifest();
        write("places.jsonl", placeLine());
        write("insights.jsonl", insightLine("sha256:i9", "vibe_check_9000", 1, 0.9));

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.rejects()).isEqualTo(1);
        assertThat(insightStore).isEmpty();

        Path rejects = runDir.resolve("_rejects.jsonl");
        assertThat(Files.readString(rejects))
                .as("사유가 남아야 프롬프트를 고칠 수 있다")
                .contains("vibe_check_9000");
    }

    // ── 보수성 ──────────────────────────────────────────────────────

    @Test
    void lowConfidence_isSkipped_notStored() throws IOException {
        manifest();
        write("places.jsonl", placeLine());
        write("insights.jsonl", insightLine("sha256:i1", "wait_time", 30, 0.4));

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.insightsSkipped()).isEqualTo(1);
        assertThat(insightStore)
                .as("추측성 사실로 자산을 오염시키느니 비어 있는 게 낫다")
                .isEmpty();
    }

    @Test
    void unresolvablePlace_goesToBucket_notDropped() throws IOException {
        manifest();
        // 외부 ID 없고 이름도 처음 보는 장소 — 노드를 만들 자격이 없다
        write("places.jsonl", """
            {"record_id":"sha256:p9","name_raw":"어디선가 본 카페","city":"seoul",\
            "lat":37.5,"lng":127.0,"external_ids":{},"source":{"url":"https://example.com/z"}}""");

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.placesUnresolved()).isEqualTo(1);
        ArrayList<PlaceCandidateUnresolved> saved = new ArrayList<>();
        verify(unresolvedRepo).save(argThat(x -> saved.add(x)));
        assertThat(saved.get(0).getRawJson())
                .as("원본을 보관해야 나중에 해결할 수 있다")
                .contains("어디선가 본 카페");
    }

    @Test
    void missingInsightsFile_isNotAnError() throws IOException {
        manifest();
        write("places.jsonl", placeLine());

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.placesResolved()).isEqualTo(1);
        assertThat(counts.insightsUpserted()).isZero();
    }

    // ── 조용한 유실 막기 (Task 8) ───────────────────────────────────

    /**
     * 줄 하나가 깨져도 나머지는 살아남고, <b>깨진 줄은 반드시 세어지고 파일에 남는다.</b>
     *
     * <p>예전에는 {@code log.warn}만 하고 rejects 카운터도 파일도 안 건드렸다.
     * 그러면 {@code rejects=0} · {@code _rejects.jsonl} 0바이트 · {@code exit 0}이라
     * 밖에서는 완전한 성공과 구분되지 않는다.
     */
    @Test
    void 줄_처리_실패는_rejects에_기록된다() throws IOException {
        manifest();
        write("places.jsonl", placeLine() + "\n{ 이건 JSON이 아니다\n");

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.rejects()).as("깨진 줄이 세어져야 한다").isEqualTo(1);
        assertThat(counts.placesResolved()).as("앞 줄은 살아남는다").isEqualTo(1);
        assertThat(Files.readString(runDir.resolve("_rejects.jsonl"), StandardCharsets.UTF_8))
                .contains("줄 처리 실패");
    }

    /**
     * 죽은 런은 DB에 {@code STARTED}로 남는다 — 샌드박스 teardown을 견디는 유일한 신호다.
     * 조회 자체가 사라지면 중단 감지 수단이 통째로 없어지므로 호출을 고정한다.
     */
    @Test
    void 중단된_런을_조회해_경고한다() throws IOException {
        manifest();
        write("places.jsonl", placeLine());
        IngestRun dead = new IngestRun("2026-08-05T04-37Z-tour_api-seoul-junggu",
                "tour_api", Map.of(), "insight-v2");
        when(runRepo.findByStatus(IngestRun.Status.STARTED)).thenReturn(List.of(dead));

        service.ingest(runDir);

        verify(runRepo).findByStatus(IngestRun.Status.STARTED);
    }

    /** 경고 기능이 본 작업을 죽이면 본말전도다. */
    @Test
    void 중단_런_조회가_실패해도_적재는_계속된다() throws IOException {
        manifest();
        write("places.jsonl", placeLine());
        when(runRepo.findByStatus(any())).thenThrow(new IllegalStateException("DB 일시 오류"));

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.placesResolved()).isEqualTo(1);
    }
}
