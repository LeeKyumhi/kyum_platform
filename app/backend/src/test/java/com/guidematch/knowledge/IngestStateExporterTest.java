package com.guidematch.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 커서 내보내기 — 특히 <b>계획용 요약</b>({@code scope-progress.jsonl}).
 *
 * <p>이 파일이 틀리면 Codex가 이미 끝낸 (소스 × 범위)를 다시 고르거나, 아직 안 한 걸
 * 건너뛴다. 둘 다 조용히 일어나고 몇 주 뒤에야 "인사이트가 왜 0이지"로 드러난다.
 */
class IngestStateExporterTest {

    private IngestSourceRepository sourceRepo;
    private IngestRunRepository runRepo;
    private PlaceRepository placeRepo;
    private IngestStateExporter exporter;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sourceRepo = mock(IngestSourceRepository.class);
        runRepo = mock(IngestRunRepository.class);
        when(runRepo.findByStatus(IngestRun.Status.STARTED)).thenReturn(List.of());
        placeRepo = mock(PlaceRepository.class);
        when(placeRepo.findAll()).thenReturn(List.of());
        exporter = new IngestStateExporter(sourceRepo, runRepo, placeRepo);
    }

    private IngestSource source(String url, String kind, String scope, String run, String seenAt) {
        IngestSource s = new IngestSource("h:" + url, url, kind, scope, run);
        ReflectionTestUtils.setField(s, "lastSeenAt", Instant.parse(seenAt));
        ReflectionTestUtils.setField(s, "lastSeenRun", run);
        return s;
    }

    private List<JsonNode> readLines(Path file) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank()) rows.add(mapper.readTree(line));
        }
        return rows;
    }

    @Test
    @DisplayName("요약은 (소스 × 범위)당 한 줄이고 URL 수를 센다")
    void scopeProgress_isOneLinePerSourceAndScope(@TempDir Path dir) throws Exception {
        when(sourceRepo.findAll()).thenReturn(List.of(
                source("https://a/1", "kakao_local", "Seoul/중구", "run-a", "2026-07-31T05:00:00Z"),
                source("https://a/2", "kakao_local", "Seoul/중구", "run-a", "2026-07-31T05:01:00Z"),
                source("https://b/1", "tour_api", "Seoul/중구", "run-b", "2026-07-31T06:00:00Z"),
                source("https://a/3", "kakao_local", "Seoul/종로구", "run-c", "2026-07-31T07:00:00Z")));

        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        int written = exporter.export(stateFile);

        assertThat(written).isEqualTo(4);
        List<JsonNode> progress = readLines(stateFile.resolveSibling("scope-progress.jsonl"));

        // 같은 범위라도 소스가 다르면 별개 줄이어야 한다. 이게 합쳐지면 Codex가
        // "중구는 끝났다"고 판단해 tour_api를 영영 안 돌리고, 인사이트가 0으로 남는다.
        assertThat(progress).hasSize(3);
        assertThat(progress).anySatisfy(n -> {
            assertThat(n.get("scope_key").asText()).isEqualTo("Seoul/중구");
            assertThat(n.get("source_kind").asText()).isEqualTo("kakao_local");
            assertThat(n.get("urls").asInt()).isEqualTo(2);
        });
        assertThat(progress).anySatisfy(n -> {
            assertThat(n.get("scope_key").asText()).isEqualTo("Seoul/중구");
            assertThat(n.get("source_kind").asText()).isEqualTo("tour_api");
            assertThat(n.get("urls").asInt()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("last_seen_at은 그 범위에서 가장 최근 값이다 (갱신 주기 판정의 근거)")
    void scopeProgress_keepsMostRecentSeenAt(@TempDir Path dir) throws Exception {
        // 일부러 최신 → 과거 순으로 준다. 마지막 값을 그냥 덮어쓰면 과거가 남는다.
        when(sourceRepo.findAll()).thenReturn(List.of(
                source("https://a/1", "kakao_local", "Seoul/중구", "run-new", "2026-07-31T09:00:00Z"),
                source("https://a/2", "kakao_local", "Seoul/중구", "run-old", "2026-07-20T09:00:00Z")));

        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        exporter.export(stateFile);

        List<JsonNode> progress = readLines(stateFile.resolveSibling("scope-progress.jsonl"));
        assertThat(progress).hasSize(1);
        assertThat(progress.get(0).get("last_seen_at").asText()).startsWith("2026-07-31T09:00");
        assertThat(progress.get(0).get("last_seen_run").asText()).isEqualTo("run-new");
    }

    @Test
    @DisplayName("초 미만 차이도 제대로 비교한다 — 문자열 비교였다면 뒤집힌다")
    void scopeProgress_comparesInstantsNotStrings() throws Exception {
        // Instant.toString()은 나노초가 0이면 소수부를 생략한다.
        // 그래서 문자열로 비교하면 "…00Z"가 "…00.5Z"보다 뒤로 정렬된다('.' < 'Z').
        Path dir = Files.createTempDirectory("state-order");
        when(sourceRepo.findAll()).thenReturn(List.of(
                source("https://a/1", "tour_api", "Jeju", "run-half", "2026-07-31T05:00:00.500Z"),
                source("https://a/2", "tour_api", "Jeju", "run-whole", "2026-07-31T05:00:01Z")));

        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        exporter.export(stateFile);

        List<JsonNode> progress = readLines(stateFile.resolveSibling("scope-progress.jsonl"));
        assertThat(progress.get(0).get("last_seen_run").asText()).isEqualTo("run-whole");
    }

    @Test
    @DisplayName("district 없는 도시의 scope_key는 도시명뿐 — 구분자를 붙이지 않는다")
    void scopeProgress_cityOnlyScopeKeyIsPreserved() throws Exception {
        // 적재기(IngestService.scopeKeyOf)가 district가 비면 city만 쓴다.
        // 여기서 "Jeju/"나 "Jeju/null"이 되면 제주·경주·강릉 등 8개 범위가
        // 영원히 "안 한 것"으로 남아 매번 다시 수집된다.
        Path dir = Files.createTempDirectory("state-city");
        when(sourceRepo.findAll()).thenReturn(List.of(
                source("https://a/1", "tour_api", "Jeju", "run-j", "2026-07-31T05:00:00Z")));

        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        exporter.export(stateFile);

        List<JsonNode> progress = readLines(stateFile.resolveSibling("scope-progress.jsonl"));
        assertThat(progress.get(0).get("scope_key").asText()).isEqualTo("Jeju");
    }

    @Test
    @DisplayName("소스가 하나도 없으면 두 파일 다 빈 채로 만들어진다")
    void export_withNoSources_writesEmptyFiles(@TempDir Path dir) throws Exception {
        when(sourceRepo.findAll()).thenReturn(List.of());

        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        assertThat(exporter.export(stateFile)).isZero();

        // 파일이 아예 없으면 Codex가 "커서를 못 읽었다"와 "아직 아무것도 안 했다"를
        // 구분하지 못한다. 빈 파일은 후자를 명확히 말해준다.
        assertThat(stateFile).exists();
        assertThat(stateFile.resolveSibling("scope-progress.jsonl")).exists();
        assertThat(readLines(stateFile.resolveSibling("scope-progress.jsonl"))).isEmpty();
    }

    @Test
    @DisplayName("기존 커서 파일을 덮어쓴다 — 남은 옛 줄이 끼어들면 안 된다")
    void export_replacesStaleCursor(@TempDir Path dir) throws Exception {
        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        Files.createDirectories(stateFile.getParent());
        Files.writeString(stateFile.resolveSibling("scope-progress.jsonl"),
                "{\"scope_key\":\"Seoul/없는구\",\"source_kind\":\"kakao_local\",\"urls\":99}\n");

        when(sourceRepo.findAll()).thenReturn(List.of(
                source("https://a/1", "tour_api", "Jeju", "run-x", "2026-07-31T09:00:00Z")));
        exporter.export(stateFile);

        List<JsonNode> progress = readLines(stateFile.resolveSibling("scope-progress.jsonl"));
        assertThat(progress).hasSize(1);
        assertThat(progress.get(0).get("scope_key").asText()).isEqualTo("Jeju");
    }

    // ── 중단된 적재 노출 (Task 8) ───────────────────────────────────

    /**
     * codex가 적재 JVM을 중간에 죽이면 exit code도 마커 파일도 안 남는다.
     * DB의 {@code STARTED}가 유일한 흔적이고, 다음 세션이 그걸 보려면 파일이어야 한다.
     */
    @Test
    @DisplayName("완료되지 않은 적재를 stalled-runs.jsonl로 내보낸다")
    void export_writesStalledRuns(@TempDir Path dir) throws Exception {
        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        when(sourceRepo.findAll()).thenReturn(List.of());
        IngestRun dead = new IngestRun("2026-08-05T04-37Z-tour_api-seoul-junggu",
                "tour_api", java.util.Map.of("city", "Seoul", "district", "중구"), "insight-v2");
        when(runRepo.findByStatus(IngestRun.Status.STARTED)).thenReturn(List.of(dead));

        exporter.export(stateFile);

        List<JsonNode> rows = readLines(stateFile.resolveSibling("stalled-runs.jsonl"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("run_id").asText())
                .isEqualTo("2026-08-05T04-37Z-tour_api-seoul-junggu");
        assertThat(rows.get(0).get("scope").get("district").asText()).isEqualTo("중구");
    }

    /**
     * 사진 갱신은 <b>이 파일이 말해주지 않으면 불가능하다.</b>
     *
     * <p>역방향 시딩은 {@code has_tour_api_id: true}인 장소를 건너뛴다("이미 붙어 있으니
     * 역조회할 이유가 없다"). 그런데 v5로 사진이 생기면서 <b>tour_api id는 있는데 사진은
     * 없는</b> 장소가 다수가 됐다(실측 21/53이 id 보유, 사진은 1건). 에이전트가 그런 장소를
     * 알아보려면 사진 유무와 <b>contentId 값 자체</b>가 필요하다 — 값이 있어야
     * {@code detailCommon2}를 검색 없이 곧바로 부를 수 있다.
     */
    @Test
    @DisplayName("레지스트리 목록에 사진 유무와 tour_api contentId가 실린다")
    void registryPlaces_carryImageStateAndContentId(@TempDir Path dir) throws Exception {
        Place withPhoto = new Place("한국금융사박물관", "Seoul", "중구", 37.5, 126.9,
                "12110587", "130157", "관광명소", "서울 중구");
        withPhoto.applyImage("https://tong.visitkorea.or.kr/x.jpg", "한국관광공사");
        Place needsPhoto = new Place("남대문시장", "Seoul", "중구", 37.5, 126.9,
                "8113954", "126510", "전통시장", "서울 중구");
        when(placeRepo.findAll()).thenReturn(List.of(withPhoto, needsPhoto));
        when(sourceRepo.findAll()).thenReturn(List.of());

        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        exporter.export(stateFile);

        List<JsonNode> rows = readLines(stateFile.resolveSibling("registry-places.jsonl"));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("has_image").asBoolean()).isTrue();
        assertThat(rows.get(0).get("tour_api_content_id").asText()).isEqualTo("130157");
        assertThat(rows.get(1).get("has_image").asBoolean()).isFalse();
        assertThat(rows.get(1).get("tour_api_content_id").asText()).isEqualTo("126510");
    }

    /**
     * 정상이면 <b>빈 파일</b>이어야 한다 — 파일이 아예 없는 것과 다르다.
     * 항상 쓰기 때문에 "비어 있다"가 "중단된 적재가 없다"는 적극적 신호가 된다.
     */
    @Test
    @DisplayName("중단된 적재가 없으면 stalled-runs.jsonl은 빈 파일로 남는다")
    void export_writesEmptyStalledFile_whenNothingStalled(@TempDir Path dir) throws Exception {
        Path stateFile = dir.resolve("state").resolve("ingested-sources.jsonl");
        when(sourceRepo.findAll()).thenReturn(List.of());

        exporter.export(stateFile);

        Path stalled = stateFile.resolveSibling("stalled-runs.jsonl");
        assertThat(Files.exists(stalled)).as("파일은 있어야 한다").isTrue();
        assertThat(readLines(stalled)).isEmpty();
    }
}
