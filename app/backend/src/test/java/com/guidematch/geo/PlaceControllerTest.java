package com.guidematch.geo;

import com.guidematch.knowledge.GuideCourseSignalLookup;
import com.guidematch.knowledge.PlaceInsightLookup;
import com.guidematch.knowledge.PlaceInsightLookup.InsightView;
import com.guidematch.knowledge.TravelerSignalLookup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 장소 목록(코스 짜기 팔레트)의 추천순 정렬과 이유.
 *
 * <p>Kakao가 주는 순서는 거리·정확도일 뿐 "왜 여기를 가야 하는지"를 말해주지 못한다.
 * 우리가 가진 근거(인증 가이드·여행자·공공기관)로 앞에 세우고, <b>앞에 세운 이유를 그대로 보여준다</b>.
 * 순서와 설명이 같은 규칙에서 나오지 않으면 "왜 이게 위에 있지?"라는 질문에 답할 수 없다.
 */
class PlaceControllerTest {

    private final KakaoLocalClient kakao = mock(KakaoLocalClient.class);
    private final TranslationService translationService = mock(TranslationService.class);
    private final PlaceInsightLookup insightLookup = mock(PlaceInsightLookup.class);
    private final GuideCourseSignalLookup guideCourseLookup = mock(GuideCourseSignalLookup.class);
    private final TravelerSignalLookup travelerLookup = mock(TravelerSignalLookup.class);

    private final PlaceController controller = new PlaceController(
            kakao, translationService, insightLookup, guideCourseLookup, travelerLookup);

    private KakaoLocalClient.Place place(String id, String name) {
        return new KakaoLocalClient.Place(id, name, "관광명소", "AT4", null,
                "서울 중구", 37.56, 126.97, "http://place/" + id, null, List.of());
    }

    private void kakaoReturns(KakaoLocalClient.Place... places) {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.searchByCategory(anyString(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(places));
    }

    private List<String> idsOf(PlaceController.PlacesResponse res) {
        return res.places().stream().map(KakaoLocalClient.Place::id).toList();
    }

    private PlaceController.PlacesResponse call() {
        return controller.places("Seoul", "attraction", null, "ko");
    }

    @Test
    void 인증_가이드가_담은_장소를_앞으로_올린다() {
        kakaoReturns(place("a", "가"), place("b", "나"), place("c", "다"));
        when(guideCourseLookup.verifiedGuideCounts(anyCollection())).thenReturn(Map.of("c", 2));

        assertThat(idsOf(call())).containsExactly("c", "a", "b");
    }

    @Test
    void 여행자가_담은_장소를_앞으로_올린다() {
        kakaoReturns(place("a", "가"), place("b", "나"));
        when(travelerLookup.travelerCounts(anyCollection())).thenReturn(Map.of("b", 4));

        assertThat(idsOf(call())).containsExactly("b", "a");
    }

    /** 근거가 없으면 Kakao가 준 순서 그대로 — 우리가 근거를 못 대는 구간의 최선은 거리·정확도다. */
    @Test
    void 근거가_없으면_Kakao_순서를_유지한다() {
        kakaoReturns(place("a", "가"), place("b", "나"), place("c", "다"));

        assertThat(idsOf(call())).containsExactly("a", "b", "c");
    }

    @Test
    void 올린_이유를_장소에_함께_싣는다() {
        kakaoReturns(place("a", "가"));
        when(travelerLookup.travelerCounts(anyCollection())).thenReturn(Map.of("a", 7));

        List<CourseReasons.Reason> reasons = call().places().get(0).reasons();

        assertThat(reasons).extracting(CourseReasons.Reason::kind).containsExactly("traveler_saved");
        assertThat(reasons.get(0).count()).isEqualTo(7);
    }

    /** 목록에는 이전 정차지가 없다 — 여기서 "도보 N분"이 나오면 기준 없는 숫자가 된다. */
    @Test
    void 목록에서는_도보_근거를_만들지_않는다() {
        kakaoReturns(place("a", "가"));
        when(travelerLookup.travelerCounts(anyCollection())).thenReturn(Map.of("a", 7));

        assertThat(call().places().get(0).reasons())
                .extracting(CourseReasons.Reason::kind).doesNotContain("walk");
    }

    @Test
    void 공공기관_자료는_출처와_함께_이유가_된다() {
        kakaoReturns(place("a", "가"));
        when(insightLookup.byKakaoPlaceIds(anyCollection(), anyString())).thenReturn(Map.of(
                "a", List.of(new InsightView("photo_spot", Map.of(), "정문", 0.9, "한국관광공사"))));

        CourseReasons.Reason r = call().places().get(0).reasons().get(0);

        assertThat(r.kind()).isEqualTo("official");
        assertThat(r.source()).isEqualTo("한국관광공사");
    }

    /**
     * 근거 집계가 죽어도 장소 목록은 나가야 한다. 여기는 <b>비로그인도 쓰는 공개 경로</b>라
     * 예외가 새면 로그인조차 안 한 사용자에게 탐색 화면이 통째로 깨진다.
     */
    @Test
    void 근거_집계가_터져도_장소_목록은_정상_응답한다() {
        kakaoReturns(place("a", "가"), place("b", "나"));
        when(guideCourseLookup.verifiedGuideCounts(anyCollection())).thenThrow(new RuntimeException("DB 죽음"));
        when(travelerLookup.travelerCounts(anyCollection())).thenThrow(new RuntimeException("DB 죽음"));

        PlaceController.PlacesResponse res = call();

        assertThat(idsOf(res)).containsExactly("a", "b");
        assertThat(res.places().get(0).reasons()).isEmpty();
    }

    @Test
    void 장소가_없으면_근거_조회도_하지_않는다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.searchByCategory(anyString(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());

        assertThat(call().places()).isEmpty();
        verify(guideCourseLookup, never()).verifiedGuideCounts(anyCollection());
        verify(travelerLookup, never()).travelerCounts(anyCollection());
    }

    /** 알 수 없는 도시 — 여기서 500이 나면 탐색 화면이 통째로 죽는다. */
    @Test
    void 알_수_없는_도시는_빈_목록을_돌려준다() {
        assertThat(controller.places("Atlantis", "attraction", null, "ko").places()).isEmpty();
    }
}
