package com.guidematch.geo;

import com.guidematch.knowledge.PlaceInsightLookup.InsightView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추천 이유(증거 사다리)의 선택 규칙.
 *
 * <p>규칙 셋이 신빙성의 전부다 — 그래서 컨트롤러 안이 아니라 순수 함수로 떼어내 여기서 고정한다.
 * <ol>
 *   <li>있는 것 중 가장 강한 것만: 사회적 근거 1개(🎫 &gt; 🏛) + 개인 근거 1개(📍) = 최대 2개</li>
 *   <li><b>0은 절대 표시하지 않는다</b> — "0명이 담았어요"는 신뢰를 깎는다. 근거가 없으면 빈 목록</li>
 *   <li>출처를 못 밝히는 자료는 근거로 쓰지 않는다 (TourAPI attribution_required)</li>
 * </ol>
 */
class CourseReasonsTest {

    private InsightView insight(String kind, String publisher, double confidence) {
        return new InsightView(kind, Map.of(), "메모", confidence, publisher);
    }

    // ── 🎫 전문가 ────────────────────────────────────────────────────

    @Test
    void 인증_가이드가_담았으면_그것이_가장_강한_근거다() {
        List<CourseReasons.Reason> reasons =
                CourseReasons.build(3, 0, List.of(insight("photo_spot", "한국관광공사", 0.9)), null);

        assertThat(reasons).extracting(CourseReasons.Reason::kind).containsExactly("guide_course");
        assertThat(reasons.get(0).count()).isEqualTo(3);
    }

    /**
     * 규칙2. 0명일 때 "0명이 담았어요"가 나가면 역효과다 —
     * 아예 만들지 않아야 프론트가 실수로라도 못 띄운다.
     */
    @Test
    void 담은_가이드가_0명이면_근거를_만들지_않는다() {
        assertThat(CourseReasons.build(0, 0, List.of(), null)).isEmpty();
    }

    // ── 🏛 공공기관 ──────────────────────────────────────────────────

    @Test
    void 가이드_근거가_없으면_공공기관_자료가_사회적_근거를_맡는다() {
        List<CourseReasons.Reason> reasons =
                CourseReasons.build(0, 0, List.of(insight("photo_spot", "한국관광공사", 0.9)), null);

        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).kind()).isEqualTo("official");
        assertThat(reasons.get(0).source()).isEqualTo("한국관광공사");
        assertThat(reasons.get(0).factKind()).isEqualTo("photo_spot");
    }

    /** 규칙3. 출처를 못 밝히면 배지를 달 수 없다 — TourAPI attribution_required 의무. */
    @Test
    void 출처가_없는_인사이트는_근거로_쓰지_않는다() {
        assertThat(CourseReasons.build(0, 0, List.of(insight("vibe", null, 0.9)), null)).isEmpty();
    }

    @Test
    void 출처가_여럿이면_신뢰도가_가장_높은_것을_쓴다() {
        List<CourseReasons.Reason> reasons = CourseReasons.build(0, 0, List.of(
                insight("best_time", "한국관광공사", 0.9),
                insight("caution", "서울관광재단", 0.5)), null);

        assertThat(reasons.get(0).factKind()).isEqualTo("best_time");
    }

    /** 신뢰도가 더 높아도 출처가 없으면 건너뛰고 그다음 것을 쓴다. */
    @Test
    void 출처_없는_고신뢰_인사이트는_건너뛴다() {
        List<CourseReasons.Reason> reasons = CourseReasons.build(0, 0, List.of(
                insight("vibe", null, 0.95),
                insight("photo_spot", "한국관광공사", 0.4)), null);

        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).source()).isEqualTo("한국관광공사");
    }

    // ── 📍 당신(동선) ────────────────────────────────────────────────

    @Test
    void 이전_정차지에서의_도보_시간이_개인_근거가_된다() {
        List<CourseReasons.Reason> reasons = CourseReasons.build(0, 0, List.of(), 400);

        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).kind()).isEqualTo("walk");
        assertThat(reasons.get(0).walkMinutes()).isEqualTo(6);   // 400m ÷ 4km/h ≈ 6분
    }

    /**
     * 첫 정차지는 이전 정차지가 없어 거리가 null이다.
     * 여기서 0분이나 "도보 0분"을 만들면 가장 눈에 띄는 카드가 가장 이상해진다.
     */
    @Test
    void 첫_정차지는_거리가_없어_동선_근거가_없다() {
        assertThat(CourseReasons.build(0, 0, List.of(), null)).isEmpty();
    }

    /** 아주 가까워도 "0분"은 말이 안 된다 — 최소 1분. */
    @Test
    void 아주_가까운_거리도_최소_1분으로_표시한다() {
        assertThat(CourseReasons.build(0, 0, List.of(), 20).get(0).walkMinutes()).isEqualTo(1);
    }

    /** 도보로 갈 만한 거리가 아니면 "도보 N분"이 오히려 거짓말이 된다. */
    @Test
    void 도보권을_벗어나면_동선_근거를_만들지_않는다() {
        assertThat(CourseReasons.build(0, 0, List.of(), 3000)).isEmpty();
    }

    // ── 조합 ────────────────────────────────────────────────────────

    /** 규칙1. 서로 다른 가족을 섞어 최대 2개 — 같은 가족 두 개는 근거를 두껍게 만들지 않는다. */
    @Test
    void 사회적_근거_하나와_동선_근거_하나까지만_보여준다() {
        List<CourseReasons.Reason> reasons = CourseReasons.build(2, 0, List.of(
                insight("photo_spot", "한국관광공사", 0.9),
                insight("best_time", "서울관광재단", 0.8)), 400);

        assertThat(reasons).extracting(CourseReasons.Reason::kind)
                .containsExactly("guide_course", "walk");
    }

    @Test
    void 근거가_하나도_없으면_빈_목록이다() {
        assertThat(CourseReasons.build(0, 0, List.of(), null)).isEmpty();
    }

    // ── 🧳 여행자들 ──────────────────────────────────────────────────
    //
    // 서열은 🎫 > 🧳 > 🏛 다. 전문가의 판단(자격 게이팅을 통과한 사람) > 또래의 실제 행동 >
    // 기관 자료 순으로 강하다. 어느 하나만 보여주므로 이 서열이 곧 무엇이 보일지를 정한다.

    @Test
    void 여행자가_담았으면_공공기관_자료보다_먼저_보여준다() {
        List<CourseReasons.Reason> reasons = CourseReasons.build(0, 12, List.of(
                insight("photo_spot", "한국관광공사", 0.9)), null);

        assertThat(reasons).extracting(CourseReasons.Reason::kind).containsExactly("traveler_saved");
        assertThat(reasons.get(0).count()).isEqualTo(12);
    }

    /** 인증 가이드가 담은 곳이라면 그게 더 강하다 — 자격을 통과한 사람의 판단이기 때문이다. */
    @Test
    void 인증_가이드_근거가_여행자_근거보다_강하다() {
        List<CourseReasons.Reason> reasons = CourseReasons.build(2, 30, List.of(), null);

        assertThat(reasons).extracting(CourseReasons.Reason::kind).containsExactly("guide_course");
    }

    /**
     * 한 명은 근거로 삼지 않는다. "여행자 1명이 담음"은 설득력이 없을 뿐 아니라,
     * 데이터가 적은 지금은 특정 개인의 행동을 그대로 드러내는 것에 가깝다.
     */
    @Test
    void 담은_여행자가_한_명이면_근거로_쓰지_않는다() {
        assertThat(CourseReasons.build(0, 1, List.of(), null)).isEmpty();
    }

    @Test
    void 담은_여행자가_두_명이면_근거가_된다() {
        assertThat(CourseReasons.build(0, 2, List.of(), null))
                .extracting(CourseReasons.Reason::kind).containsExactly("traveler_saved");
    }

    /** 한 명일 때는 그다음으로 강한 근거로 자연히 내려간다 — 근거가 사라지는 게 아니다. */
    @Test
    void 여행자가_한_명이면_공공기관_자료로_내려간다() {
        List<CourseReasons.Reason> reasons = CourseReasons.build(0, 1, List.of(
                insight("photo_spot", "한국관광공사", 0.9)), null);

        assertThat(reasons).extracting(CourseReasons.Reason::kind).containsExactly("official");
    }
}
