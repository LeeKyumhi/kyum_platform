package com.guidematch.geo;

import com.guidematch.knowledge.Place;
import com.guidematch.knowledge.PlaceKind;
import com.guidematch.knowledge.PlaceRepository;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 정차지 선정.
 *
 * <p><b>가장 중요한 것</b>: 레지스트리가 채울 수 있으면 Kakao를 <b>부르지 않는다</b>는 것.
 * 이게 깨지면 응답은 멀쩡하고 결과도 그럴듯한데 지식베이스는 아무 일도 안 하게 된다 —
 * 이 기능 전체가 무의미해지는 조용한 실패다.
 */
class CoursePlannerTest {

    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final KakaoLocalClient kakao = mock(KakaoLocalClient.class);
    private final CoursePlanner planner = new CoursePlanner(placeRepo, kakao);

    private static final KoreanCity SEOUL = KoreanCity.LIST.stream()
            .filter(c -> c.key().equals("Seoul")).findFirst().orElseThrow();

    private static Place place(long id, String name, PlaceKind kind, double lat, double lng) {
        return place(id, name, kind, lat, lng, "여행 > 관광,명소", "k" + id);
    }

    private static Place place(long id, String name, PlaceKind kind, double lat, double lng,
                               String categoryRaw, String kakaoId) {
        Place p = new Place(name, "Seoul", "중구", lat, lng, kakaoId, null, categoryRaw, "서울 중구 어딘가");
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "placeKind", kind);
        return p;
    }

    private static KakaoLocalClient.Place kakaoPlace(String id, String name, double lat, double lng) {
        return new KakaoLocalClient.Place(id, name, "여행 > 관광,명소", "AT4", null,
                "서울 중구", lat, lng, "http://place.map.kakao.com/" + id, null, java.util.List.of());
    }

    @Test
    void 레지스트리가_슬롯을_다_채우면_Kakao를_부르지_않는다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(eq("Seoul"), eq("중구"), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749),
                place(2, "남대문시장", PlaceKind.MARKET, 37.5595, 126.9773)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "market"));

        assertThat(plan.stops()).hasSize(2);
        assertThat(plan.stops()).extracting(CoursePlanner.PlannedStop::source).containsOnly("registry");
        assertThat(plan.stops()).extracting(CoursePlanner.PlannedStop::placeId)
                .containsExactlyInAnyOrder(1L, 2L);
        verify(kakao, never()).searchByCategory(anyString(), anyDouble(), anyDouble(), anyInt());
        verify(kakao, never()).searchByKeyword(anyString(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void 레지스트리에_없는_슬롯만_Kakao로_채운다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));
        when(kakao.searchByCategory(eq("CE7"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(kakaoPlace("kk9", "어느 카페", 37.5660, 126.9850)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "cafe"));

        assertThat(plan.stops()).extracting(CoursePlanner.PlannedStop::source)
                .containsExactly("registry", "kakao");
        // 레지스트리가 채운 facet은 검색하지 않는다
        verify(kakao, never()).searchByCategory(eq("AT4"), anyDouble(), anyDouble(), anyInt());
    }

    /** ★ 같은 장소가 두 출처에서 오면 한 번만 나와야 한다 — kakao id 일치. */
    @Test
    void 중복제거_1단_kakao_id가_같으면_한_번만_나온다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));
        // 같은 kakao id("k1")를 카페 슬롯에서 다시 만난다 — 좌표·이름은 일부러 다르게 둬서
        // 2단·3단이 아니라 1단(id)이 잡는지 확인한다
        when(kakao.searchByCategory(eq("CE7"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(kakaoPlace("k1", "덕수궁 카페", 37.5700, 126.9900)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "cafe"));

        assertThat(plan.stops()).hasSize(1);
        assertThat(plan.stops().get(0).source()).isEqualTo("registry");
    }

    /** 중복제거 2단 — id가 달라도 정규화 이름이 같으면 같은 장소로 본다. */
    @Test
    void 중복제거_2단_이름이_같으면_한_번만_나온다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "남산골한옥마을", PlaceKind.ATTRACTION, 37.5594, 126.9940)));
        // 공백만 다른 표기 + 100m 밖 좌표 → 3단이 아니라 2단(이름)이 잡아야 한다
        when(kakao.searchByCategory(eq("CE7"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(kakaoPlace("zz", "남산골 한옥마을", 37.5620, 126.9980)));

        assertThat(planner.plan(SEOUL, "중구", List.of("attraction", "cafe")).stops()).hasSize(1);
    }

    /** 중복제거 3단 — 이름도 id도 다르지만 100m 안이면 같은 지점으로 본다. */
    @Test
    void 중복제거_3단_100m_이내면_한_번만_나온다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));
        when(kakao.searchByCategory(eq("CE7"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(kakaoPlace("zz", "덕수궁 대한문", 37.5657, 126.9750)));

        assertThat(planner.plan(SEOUL, "중구", List.of("attraction", "cafe")).stops()).hasSize(1);
    }

    /** ★ Kakao 키가 없어도 레지스트리는 동작해야 한다. 지금까지는 무조건 빈 목록이었다. */
    @Test
    void Kakao가_없어도_레지스트리로_코스가_나온다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749),
                place(2, "남대문시장", PlaceKind.MARKET, 37.5595, 126.9773)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "market"));

        assertThat(plan.stops()).hasSize(2);
        verify(kakao, never()).geocodeRegion(anyString());
    }

    /** ★ 구 앵커가 Kakao에 물려 있으면 키 없이 구 단위가 죽는다. 무게중심으로 푼다. */
    @Test
    void Kakao가_없으면_구_앵커는_레지스트리_무게중심이다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "북쪽", PlaceKind.ATTRACTION, 37.60, 127.00),
                place(2, "남쪽", PlaceKind.ATTRACTION, 37.50, 127.00)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction"));

        assertThat(plan.anchorLat()).isEqualTo(37.55, Offset.offset(0.0001));
        assertThat(plan.anchorLng()).isEqualTo(127.00, Offset.offset(0.0001));
    }

    /** 구에 레지스트리 장소가 하나도 없으면 도시 중심으로 떨어진다 (Kakao 없음). */
    @Test
    void 레지스트리가_빈_구에서는_도시_중심을_쓴다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of());

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction"));

        assertThat(plan.stops()).isEmpty();
        assertThat(plan.anchorLat()).isEqualTo(SEOUL.lat());
    }

    /** EVENT·SHOP·LODGING·OTHER는 조회 자체에 들어가지 않는다 — 축제가 코스에 안 오르는 근거. */
    @Test
    void 정차지_후보_종류만_조회한다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of());

        planner.plan(SEOUL, "중구", List.of("attraction", "cafe"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<PlaceKind>> kinds = ArgumentCaptor.forClass(Collection.class);
        verify(placeRepo).findCandidates(anyString(), anyString(), kinds.capture());
        assertThat(kinds.getValue())
                .containsExactlyInAnyOrder(PlaceKind.ATTRACTION, PlaceKind.NATURE, PlaceKind.CAFE)
                .doesNotContain(PlaceKind.EVENT, PlaceKind.SHOP, PlaceKind.LODGING, PlaceKind.OTHER);
    }

    /** 같은 장소를 두 슬롯에 넣지 않는다 (기존 동작 유지). */
    @Test
    void 같은_장소는_두_번_나오지_않는다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "attraction"));

        assertThat(plan.stops()).hasSize(1);
    }

    /**
     * 구를 지정하지 않으면 도시 전체로 조회한다 — {@code district = null} 경로.
     * 이 경로는 구를 지정한 요청만 테스트하면 영영 안 밟힌다.
     */
    @Test
    void 구를_지정하지_않으면_도시_전체를_조회한다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(eq("Seoul"), isNull(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, null, List.of("attraction"));

        assertThat(plan.resolvedDistrict()).isNull();
        assertThat(plan.stops()).hasSize(1);
        assertThat(plan.anchorLat()).isEqualTo(SEOUL.lat());
    }

    // ── 표시용 카테고리·링크 ────────────────────────────────────────

    /**
     * ★ TourAPI 장소의 category_raw는 {@code A02>A0206>A02060500} 같은 <b>분류코드</b>다.
     *
     * <p>컨트롤러의 {@code " > "} 자르기는 공백이 없어 통째로 통과시키므로, 그대로 두면
     * 사용자 화면에 코드가 보이고 Google 번역 캐시에도 코드가 쌓인다. 실측상 인사이트를 가진
     * 장소가 전부 이 부류라 {@code theme=culture} 추천에서 곧바로 드러난다.
     */
    @Test
    void tourApi_분류코드는_종류_라벨로_바뀐다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "국립극장 공연예술박물관", PlaceKind.CULTURE, 37.5520, 127.0050,
                        "A02>A0206>A02060100", null)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("culture"));

        assertThat(plan.stops().get(0).category()).isEqualTo("문화시설");
    }

    /** Kakao 카테고리 경로는 읽을 수 있으므로 원문을 유지한다(컨트롤러가 마지막 segment만 취한다). */
    @Test
    void kakao_카테고리_경로는_원문을_유지한다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "한국은행 화폐박물관", PlaceKind.CULTURE, 37.5600, 126.9800,
                        "문화,예술 > 문화시설 > 박물관", "k1")));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("culture"));

        assertThat(plan.stops().get(0).category()).isEqualTo("문화,예술 > 문화시설 > 박물관");
    }

    /** Kakao id가 있으면 장소 링크를 복원한다. 없으면 null이고 프론트가 좌표로 만든다. */
    @Test
    void 레지스트리_정차지도_kakao_id가_있으면_장소링크가_붙는다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749, "여행 > 관광,명소", "10305191"),
                place(2, "간송미술관", PlaceKind.CULTURE, 37.5921, 126.9990, "A02>A0206>A02060500", null)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "culture"));

        assertThat(plan.stops()).filteredOn(s -> s.name().equals("덕수궁"))
                .extracting(CoursePlanner.PlannedStop::placeUrl)
                .containsExactly("https://place.map.kakao.com/10305191");
        assertThat(plan.stops()).filteredOn(s -> s.name().equals("간송미술관"))
                .extracting(CoursePlanner.PlannedStop::placeUrl)
                .containsOnlyNulls();
    }

    /** 이 도시에 없는 구 이름은 무시하고 도시 전체로 떨어진다 (기존 동작 유지). */
    @Test
    void 유효하지_않은_구는_무시한다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(eq("Seoul"), isNull(), anyCollection())).thenReturn(List.of());

        CoursePlanner.Plan plan = planner.plan(SEOUL, "해운대구", List.of("attraction"));

        assertThat(plan.resolvedDistrict()).isNull();
    }
}
