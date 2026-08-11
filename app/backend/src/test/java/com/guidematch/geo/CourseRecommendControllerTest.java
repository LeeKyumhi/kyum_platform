package com.guidematch.geo;

import com.guidematch.knowledge.GuideCourseSignalLookup;
import com.guidematch.knowledge.PlaceInsightLookup;
import com.guidematch.knowledge.PlaceInsightLookup.InsightView;
import com.guidematch.knowledge.SignalRecorder;
import com.guidematch.knowledge.TravelerSignalLookup;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 추천 응답이 정차지에 실어 보내는 것들 — 식별자와 근거.
 *
 * <p><b>여기가 이 사이클에서 유일하게 🎫를 검증할 수 있는 지점이다.</b> 코스 데이터가 거의 없어서
 * 실제 화면에서는 "🎫가 깨진 것"과 "담은 가이드가 없는 것"이 똑같이 아무것도 안 보이는 모습이다.
 * 규칙2(0은 표시하지 않는다)가 정확히 그 둘을 같은 화면으로 만든다. 그래서 조인이 성사되는 조건을
 * 여기서 심어놓고 근거가 실제로 뜨는지 고정한다 — 없으면 "지금 만들고 나중에 켜진다"가
 * 조용히 "영원히 안 켜진다"가 된다.
 */
class CourseRecommendControllerTest {

    private final CoursePlanner planner = mock(CoursePlanner.class);
    private final TranslationService translationService = mock(TranslationService.class);
    private final PlaceInsightLookup insightLookup = mock(PlaceInsightLookup.class);
    private final GuideCourseSignalLookup guideCourseLookup = mock(GuideCourseSignalLookup.class);
    private final TravelerSignalLookup travelerLookup = mock(TravelerSignalLookup.class);
    private final SignalRecorder signalRecorder = mock(SignalRecorder.class);

    private final CourseRecommendController controller = new CourseRecommendController(
            planner, translationService, insightLookup, guideCourseLookup, travelerLookup, signalRecorder);

    private CoursePlanner.PlannedStop stop(Long placeId, String kakaoId, String name) {
        return new CoursePlanner.PlannedStop(placeId, kakaoId, name, "관광명소",
                "서울 종로구", 37.5796, 126.9770, "http://place/" + kakaoId, "registry");
    }

    private void planned(CoursePlanner.PlannedStop... stops) {
        when(planner.plan(any(), any(), anyList()))
                .thenReturn(new CoursePlanner.Plan(List.of(stops), 37.5, 127.0, "종로구"));
        when(planner.isKakaoEnabled()).thenReturn(true);
    }

    private CourseRecommendController.RecommendResponse recommend() {
        return controller.recommend("Seoul", "종로구", "mixed", "ko", 7L);
    }

    // ── 식별자 (버그 픽스) ────────────────────────────────────────────

    /**
     * 식별자를 안 실어 보내면 프론트가 {@code rec-3-경복궁} 같은 값을 합성해 쓰고,
     * 그게 그대로 {@code tour_course_waypoints.place_id}에 저장돼 왔다.
     * 카카오맵 링크가 깨지는 것은 물론이고 🎫의 원천 테이블이 조인 불가능한 값으로 오염된다.
     */
    @Test
    void 정차지는_kakao_place_id를_그대로_싣는다() {
        planned(stop(42L, "1234567", "경복궁"));

        CourseRecommendController.Stop s = recommend().stops().get(0);

        assertThat(s.kakaoPlaceId()).isEqualTo("1234567");
        assertThat(s.placeId()).isEqualTo(42L);
    }

    /** Kakao 폴백 정차지는 레지스트리 id가 없다 — null이어야지 합성값이 들어가면 안 된다. */
    @Test
    void 레지스트리에_없는_정차지는_placeId가_null이다() {
        planned(stop(null, "999", "어딘가"));

        assertThat(recommend().stops().get(0).placeId()).isNull();
        assertThat(recommend().stops().get(0).kakaoPlaceId()).isEqualTo("999");
    }

    // ── 🎫 점등 ──────────────────────────────────────────────────────

    @Test
    void 인증_가이드가_담은_장소는_근거로_표시된다() {
        planned(stop(42L, "1234567", "경복궁"));
        when(guideCourseLookup.verifiedGuideCounts(anyCollection())).thenReturn(Map.of("1234567", 3));

        List<CourseReasons.Reason> reasons = recommend().stops().get(0).reasons();

        assertThat(reasons).extracting(CourseReasons.Reason::kind).contains("guide_course");
        assertThat(reasons.get(0).count()).isEqualTo(3);
    }

    /** 🎫 조회는 kakao place id로 간다 — 레지스트리 id로 물으면 영원히 빗나간다. */
    @Test
    void 가이드코스_조회는_kakao_id로_배치_한_번만_한다() {
        planned(stop(42L, "a"  , "가"), stop(43L, "b", "나"), stop(null, "c", "다"));

        recommend();

        verify(guideCourseLookup, times(1)).verifiedGuideCounts(List.of("a", "b", "c"));
    }

    @Test
    void 담은_가이드가_없으면_가이드코스_근거가_없다() {
        planned(stop(42L, "1234567", "경복궁"));
        when(guideCourseLookup.verifiedGuideCounts(anyCollection())).thenReturn(Map.of());

        assertThat(recommend().stops().get(0).reasons())
                .extracting(CourseReasons.Reason::kind).doesNotContain("guide_course");
    }

    /** 🎫 집계가 통째로 실패해도 추천은 나가야 한다 — 근거는 부가 정보지 본체가 아니다. */
    @Test
    void 가이드코스_집계가_터져도_추천은_정상_응답한다() {
        planned(stop(42L, "1234567", "경복궁"));
        when(guideCourseLookup.verifiedGuideCounts(anyCollection()))
                .thenThrow(new RuntimeException("DB 죽음"));

        CourseRecommendController.RecommendResponse res = recommend();

        assertThat(res.stops()).hasSize(1);
        assertThat(res.stops().get(0).reasons())
                .extracting(CourseReasons.Reason::kind).doesNotContain("guide_course");
    }

    // ── 🏛 출처 ──────────────────────────────────────────────────────

    @Test
    void 공공기관_자료는_출처와_함께_근거가_된다() {
        planned(stop(42L, "1234567", "경복궁"));
        when(insightLookup.byPlaceIds(anyList(), anyString())).thenReturn(Map.of(
                42L, List.of(new InsightView("photo_spot", Map.of(), "정문 앞", 0.9, "한국관광공사"))));

        CourseReasons.Reason r = recommend().stops().get(0).reasons().get(0);

        assertThat(r.kind()).isEqualTo("official");
        assertThat(r.source()).isEqualTo("한국관광공사");
    }

    // ── 신호 ────────────────────────────────────────────────────────

    /**
     * {@code courseRef}는 지금까지 컨트롤러 안에서 만들어 SHOWN 신호에만 쓰고 버려졌다.
     * 프론트가 ADDED를 기록하려면 같은 키를 알아야 SHOWN↔ADDED를 짝지을 수 있다.
     */
    @Test
    void 응답에_courseRef가_실려서_ADDED_신호와_짝지을_수_있다() {
        planned(stop(42L, "1234567", "경복궁"));

        assertThat(recommend().courseRef()).isEqualTo("Seoul/종로구/mixed");
    }

    /**
     * 드래그는 순수 프론트 동작이라 서버는 아무것도 모른다 — 일정 전체 PUT은 한참 뒤에 오고
     * 그 payload에는 "추천에서 왔다"는 표식도 courseRef도 없다. 그래서 담는 순간
     * 별도로 알려주는 지점이 필요하다.
     */
    @Test
    void 담기_신호_엔드포인트가_ADDED를_기록한다() {
        controller.recordAdded(
                new CourseRecommendController.AddedSignalRequest(42L, "1234567", "Seoul/종로구/mixed"),
                7L);

        verify(signalRecorder).recordAdded(
                new SignalRecorder.StopRef(42L, "1234567"), "Seoul/종로구/mixed", 7L);
    }

    // ── 테마 반영 ────────────────────────────────────────────────────
    //
    // 테마를 골랐는데 정차지 5곳 중 2곳만 그 종류면 사용자에게는 "선택이 무시됐다"로 읽힌다.
    // 그렇다고 5곳을 전부 같은 종류로 채우면 "밥 5끼 코스"가 되어 동선이 성립하지 않는다.
    // 그래서 규칙은 "다수결" — 테마가 절반을 넘고, 쉬어가는 자리는 1~2곳만.

    private List<String> slotsFor(String theme) {
        planned(stop(42L, "1234567", "경복궁"));
        controller.recommend("Seoul", "종로구", theme, "ko", 7L);
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(planner).plan(any(), any(), captor.capture());
        return captor.getValue();
    }

    private long countOf(List<String> slots, String facet) {
        return slots.stream().filter(facet::equals).count();
    }

    @Test
    void 맛집_테마는_맛집이_과반이다() {
        List<String> slots = slotsFor("food");
        assertThat(countOf(slots, "food"))
                .as("맛집을 골랐는데 맛집이 과반이 아니면 선택이 무시된 것으로 보인다")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void 카페_테마는_카페가_과반이다() {
        assertThat(countOf(slotsFor("cafe"), "cafe")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void 역사문화_테마는_문화가_과반이다() {
        assertThat(countOf(slotsFor("culture"), "culture")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void 전통시장_테마는_시장이_과반이다() {
        assertThat(countOf(slotsFor("market"), "market")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void 핵심명소_테마는_명소가_과반이다() {
        assertThat(countOf(slotsFor("attraction"), "attraction")).isGreaterThanOrEqualTo(3);
    }

    /** 한 종류만 5곳이면 코스가 아니라 목록이다 — 쉬어가는 자리를 반드시 남긴다. */
    @Test
    void 어떤_테마도_한_종류로만_채우지_않는다() {
        for (String theme : List.of("food", "cafe", "culture", "market", "attraction")) {
            reset(planner);
            List<String> slots = slotsFor(theme);
            assertThat(slots).as(theme + " 테마").hasSize(5);
            assertThat(countOf(slots, theme))
                    .as(theme + " 테마가 5칸을 독점하면 동선이 성립하지 않는다")
                    .isLessThanOrEqualTo(4);
        }
    }

    /** 믹스는 골고루가 정체성이다 — 여기까지 과반으로 만들면 테마 구분이 사라진다. */
    @Test
    void 믹스_테마는_서로_다른_종류로_섞는다() {
        assertThat(slotsFor("mixed")).doesNotHaveDuplicates();
    }

    /** 모르는 테마로 500이 나거나 빈 코스가 되면 안 된다 — mixed로 떨어진다. */
    @Test
    void 알_수_없는_테마는_믹스로_떨어진다() {
        assertThat(slotsFor("무슨테마")).hasSize(5).doesNotHaveDuplicates();
    }

    /** 알 수 없는 도시 — 여기서 500이 나면 언어/도시 조합 하나로 화면 전체가 죽는다. */
    @Test
    void 알_수_없는_도시는_빈_결과를_돌려준다() {
        CourseRecommendController.RecommendResponse res =
                controller.recommend("Atlantis", null, "mixed", "ko", 7L);

        assertThat(res.stops()).isEmpty();
        verifyNoInteractions(guideCourseLookup);
    }
}
