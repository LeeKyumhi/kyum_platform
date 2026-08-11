package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 코스 추천 ↔ 인사이트를 잇는 조인 테스트.
 *
 * <p><b>여기가 조용히 깨지면 알아챌 방법이 없다.</b> 코스 추천은 Kakao의 place id로 조회하고,
 * 우리 레지스트리는 {@code places.kakao_place_id}로 답한다. 두 문자열이 어긋나면 조회가
 * 전부 빗나가 {@code insights}가 <b>영원히 빈 배열</b>로 나오는데, 그 모습이 "아직 수집이
 * 안 된 장소"와 완전히 똑같아서 버그로 보이지 않는다. 그래서 조인을 직접 고정한다.
 */
class PlaceInsightLookupTest {

    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final PlaceInsightRepository insightRepo = mock(PlaceInsightRepository.class);
    private final PlaceInsightLookup lookup = new PlaceInsightLookup(placeRepo, insightRepo);

    private Place place(long id, String kakaoPlaceId) {
        Place p = new Place("어니언 성수", "Seoul", "성동구", 37.5445, 127.0557, kakaoPlaceId, null,
                "음식점 > 카페 > 커피전문점", "서울 성동구 아차산로9길 8");
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private PlaceInsight insight(long placeId, FactKind kind, Map<String, String> notes, double conf) {
        return new PlaceInsight("sha256:x" + kind, placeId, kind,
                Map.of("minutes", 30), notes, conf,
                "https://example.com/a", null, null, "kakao_local", "insight-v1", "run-1");
    }

    /** 코스 추천이 넘기는 값(Kakao place id)으로 실제로 조회가 성사되는지. */
    @Test
    void joinsOnKakaoPlaceId_theExactValueCourseRecommendPasses() {
        Place p = place(42L, "1234567");
        when(placeRepo.findAllByKakaoPlaceIdIn(List.of("1234567"))).thenReturn(List.of(p));
        when(insightRepo.findByPlaceIdIn(any()))
                .thenReturn(List.of(insight(42L, FactKind.WAIT_TIME, Map.of("ko", "주말 30분"), 0.7)));

        Map<String, List<PlaceInsightLookup.InsightView>> result =
                lookup.byKakaoPlaceIds(List.of("1234567"), "ko");

        assertThat(result)
                .as("Kakao id 키로 돌아와야 코스 추천이 정차지에 붙일 수 있다")
                .containsKey("1234567");
        assertThat(result.get("1234567")).hasSize(1);
        assertThat(result.get("1234567").get(0).kind()).isEqualTo("wait_time");
        assertThat(result.get("1234567").get(0).note()).isEqualTo("주말 30분");
    }

    @Test
    void sortsByConfidenceDescending() {
        Place p = place(42L, "1234567");
        when(placeRepo.findAllByKakaoPlaceIdIn(any())).thenReturn(List.of(p));
        when(insightRepo.findByPlaceIdIn(any())).thenReturn(List.of(
                insight(42L, FactKind.VIBE, Map.of("ko", "조용함"), 0.5),
                insight(42L, FactKind.WAIT_TIME, Map.of("ko", "30분"), 0.9)));

        List<PlaceInsightLookup.InsightView> views = lookup.byKakaoPlaceIds(List.of("1234567"), "ko").get("1234567");

        assertThat(views).extracting(PlaceInsightLookup.InsightView::kind)
                .containsExactly("wait_time", "vibe");
    }

    /** 번역이 아직 안 붙은 사실이라도 한국어로 보여주는 편이 아무것도 안 보이는 것보다 낫다. */
    @Test
    void fallsBackToKorean_whenRequestedLangMissing() {
        Place p = place(42L, "1234567");
        when(placeRepo.findAllByKakaoPlaceIdIn(any())).thenReturn(List.of(p));
        when(insightRepo.findByPlaceIdIn(any()))
                .thenReturn(List.of(insight(42L, FactKind.WAIT_TIME, Map.of("ko", "주말 30분"), 0.7)));

        List<PlaceInsightLookup.InsightView> views = lookup.byKakaoPlaceIds(List.of("1234567"), "en").get("1234567");

        assertThat(views.get(0).note()).isEqualTo("주말 30분");
    }

    /** 아직 수집 안 된 장소 — 여기서 예외가 나거나 null이 새면 코스 추천 전체가 죽는다. */
    @Test
    void unknownPlace_returnsEmptyMap_withoutQueryingInsights() {
        when(placeRepo.findAllByKakaoPlaceIdIn(any())).thenReturn(List.of());

        assertThat(lookup.byKakaoPlaceIds(List.of("999"), "ko")).isEmpty();
        verify(insightRepo, never()).findByPlaceIdIn(any());
    }

    @Test
    void blankAndNullIds_areFilteredOut_noQueryWhenNothingLeft() {
        assertThat(lookup.byKakaoPlaceIds(java.util.Arrays.asList(null, "", "  "), "ko")).isEmpty();
        verify(placeRepo, never()).findAllByKakaoPlaceIdIn(any());
    }

    /** 정차지가 몇 곳이든 쿼리는 2회 — 시드니 왕복 250ms에서 N+1은 즉사다. */
    @Test
    void queriesExactlyTwice_regardlessOfStopCount() {
        when(placeRepo.findAllByKakaoPlaceIdIn(any())).thenReturn(List.of(
                place(1L, "a"), place(2L, "b"), place(3L, "c"), place(4L, "d"), place(5L, "e")));
        when(insightRepo.findByPlaceIdIn(any())).thenReturn(List.of());

        lookup.byKakaoPlaceIds(List.of("a", "b", "c", "d", "e"), "ko");

        verify(placeRepo, times(1)).findAllByKakaoPlaceIdIn(any());
        verify(insightRepo, times(1)).findByPlaceIdIn(any());
    }

    // ── byPlaceIds — 레지스트리 정차지용 (Task 4) ─────────────────────

    /** 레지스트리 정차지는 place_id를 이미 알고 있다 — kakao id를 거칠 이유가 없다. 쿼리 1회. */
    @Test
    void byPlaceIds는_쿼리_한_번으로_인사이트를_붙인다() {
        when(insightRepo.findByPlaceIdIn(any())).thenReturn(List.of(
                insight(10L, FactKind.VIBE, Map.of("ko", "조용하다"), 0.9),
                insight(10L, FactKind.BEST_TIME, Map.of("ko", "아침"), 0.5),
                insight(11L, FactKind.CAUTION, Map.of("ko", "월요일 휴관"), 0.7)));

        Map<Long, List<PlaceInsightLookup.InsightView>> out =
                lookup.byPlaceIds(List.of(10L, 11L), "ko");

        assertThat(out.keySet()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(out.get(10L)).extracting(PlaceInsightLookup.InsightView::kind)
                .containsExactly("vibe", "best_time");   // 신뢰도 내림차순
        // 1회차(kakao id → place) 조회가 통째로 빠졌는지 — 이게 "쿼리 1회"의 실질이다
        verify(placeRepo, never()).findAllByKakaoPlaceIdIn(any());
    }

    @Test
    void byPlaceIds는_빈_입력에_쿼리를_날리지_않는다() {
        assertThat(lookup.byPlaceIds(List.of(), "ko")).isEmpty();
        verify(insightRepo, never()).findByPlaceIdIn(any());
    }

    /** 요청 언어 → ko → 아무거나. 번역이 아직 없는 사실도 한국어로는 보여준다. */
    @Test
    void byPlaceIds도_언어_폴백을_한다() {
        when(insightRepo.findByPlaceIdIn(any())).thenReturn(List.of(
                insight(10L, FactKind.VIBE, Map.of("ko", "조용하다"), 0.9)));

        assertThat(lookup.byPlaceIds(List.of(10L), "en").get(10L).get(0).note())
                .isEqualTo("조용하다");
    }

    /** null이 섞여도 죽지 않는다 — Kakao 폴백 정차지는 placeId가 null이다. */
    @Test
    void byPlaceIds는_null_id를_걸러낸다() {
        assertThat(lookup.byPlaceIds(java.util.Arrays.asList(null, null), "ko")).isEmpty();
        verify(insightRepo, never()).findByPlaceIdIn(any());
    }

    // ── 출처(publisher) 투영 ──────────────────────────────────────────
    //
    // TourAPI 계약이 attribution_required: true다. 출처를 못 싣으면 인사이트를
    // 화면에 띄우는 것 자체가 의무 위반이 된다. 엔티티에는 evidence_publisher가
    // 있는데 뷰가 떨어뜨리면, 화면에서는 "출처가 없는 자료"와 구분되지 않는다.

    private PlaceInsight insightBy(long placeId, FactKind kind, String publisher) {
        return new PlaceInsight("sha256:p" + kind, placeId, kind,
                Map.of("spot", "정문"), Map.of("ko", "정문 앞"), 0.8,
                "https://example.com/a", publisher, null, "tour_api", "insight-v4", "run-1");
    }

    @Test
    void byPlaceIds는_출처_발행처를_뷰에_싣는다() {
        when(insightRepo.findByPlaceIdIn(any()))
                .thenReturn(List.of(insightBy(10L, FactKind.PHOTO_SPOT, "한국관광공사")));

        assertThat(lookup.byPlaceIds(List.of(10L), "ko").get(10L).get(0).publisher())
                .isEqualTo("한국관광공사");
    }

    /** 출처가 없는 사실도 조회는 되어야 한다 — 배지를 안 붙일 뿐이다(판단은 호출부). */
    @Test
    void 출처가_없으면_publisher는_null이고_조회는_정상이다() {
        when(insightRepo.findByPlaceIdIn(any()))
                .thenReturn(List.of(insightBy(10L, FactKind.VIBE, null)));

        List<PlaceInsightLookup.InsightView> views = lookup.byPlaceIds(List.of(10L), "ko").get(10L);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).publisher()).isNull();
    }

    @Test
    void byKakaoPlaceIds도_출처를_싣는다() {
        when(placeRepo.findAllByKakaoPlaceIdIn(any())).thenReturn(List.of(place(42L, "1234567")));
        when(insightRepo.findByPlaceIdIn(any()))
                .thenReturn(List.of(insightBy(42L, FactKind.BEST_TIME, "한국관광공사")));

        assertThat(lookup.byKakaoPlaceIds(List.of("1234567"), "ko").get("1234567").get(0).publisher())
                .isEqualTo("한국관광공사");
    }
}
