package com.guidematch.geo;

import com.guidematch.knowledge.PlaceInsightLookup.InsightView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 정차지마다 "왜 여기인가"를 고르는 규칙 — 증거 사다리.
 *
 * <p>지도 앱도 장소는 나열한다. 우리가 다른 지점은 <b>근거를 밝힌다</b>는 것뿐이고,
 * 근거는 밝히는 순간 검증 가능해진다. 그래서 규칙 셋을 지키지 못할 바에는 아무것도 안 보여준다:
 *
 * <ol>
 *   <li>있는 것 중 가장 강한 것만. 사회적 근거 1개(🎫 &gt; 🏛) + 개인 근거 1개(📍) = 최대 2개.
 *       같은 가족을 두 개 붙인다고 근거가 두꺼워지지 않는다.</li>
 *   <li><b>0은 절대 표시하지 않는다.</b> "0명이 담았어요"는 없느니만 못하다.
 *       수집이 안 된 지역은 🏛·📍가 버티고, 데이터가 쌓이면 🎫로 자연 승급한다.</li>
 *   <li>출처를 못 밝히는 자료는 근거로 쓰지 않는다 — TourAPI 계약의
 *       {@code attribution_required: true} 의무이기도 하다.</li>
 * </ol>
 *
 * <p><b>문구는 만들지 않는다.</b> 라벨을 여기서 한국어로 조립하면 en/zh 사용자에게 한국어가 나간다.
 * 구조(종류·수·출처)만 내려보내고 문장은 프론트 i18n이 만든다.
 */
public final class CourseReasons {

    private CourseReasons() {}

    /** 도보 속도 — 소요시간 산식({@code CourseRecommendController.WALK_KMH})과 같은 값을 쓴다. */
    private static final double WALK_KMH = 4.0;

    /**
     * 도보권 상한. 이 밖에서 "도보 N분"은 근거가 아니라 거짓말이다.
     * 4km/h로 약 15분 거리.
     */
    private static final int WALKABLE_MAX_METERS = 1000;

    /**
     * 🧳 최소 표시 인원. 한 명은 근거로 삼지 않는다 — 설득력이 없을 뿐 아니라,
     * 데이터가 적은 지금은 특정 개인의 행동을 그대로 드러내는 것에 가깝다.
     */
    static final int MIN_TRAVELERS = 2;

    /**
     * @param kind        {@code guide_course}(🎫) · {@code traveler_saved}(🧳) · {@code official}(🏛) · {@code walk}(📍)
     * @param count       🎫 인증 가이드 수 / 🧳 담은 여행자 수. 프론트는 🎫가 1이면 숫자를 숨긴다
     * @param source      🏛에서 발행처. 없으면 근거 자체를 만들지 않으므로 여기서는 항상 non-null
     * @param factKind    🏛에서 어떤 사실인지 (photo_spot 등, snake_case wire)
     * @param walkMinutes 📍에서 이전 정차지로부터의 도보 분
     */
    public record Reason(String kind, Integer count, String source, String factKind, Integer walkMinutes) {}

    /**
     * @param verifiedGuideCount        이 장소를 활성 코스에 담은 인증 가이드 수 (0이면 근거 없음)
     * @param travelerCount             이 장소를 찜·일정에 담은 여행자 수 (사람 단위 합집합)
     * @param insights                  이 장소의 인사이트 (신뢰도 내림차순일 필요는 없다 — 여기서 다시 정렬한다)
     * @param distanceFromPrevMeters    이전 정차지로부터의 거리. 첫 정차지·목록 화면은 null
     */
    public static List<Reason> build(int verifiedGuideCount, int travelerCount,
                                     List<InsightView> insights, Integer distanceFromPrevMeters) {
        List<Reason> reasons = new ArrayList<>(2);

        social(verifiedGuideCount, travelerCount, insights).ifPresent(reasons::add);
        walk(distanceFromPrevMeters).ifPresent(reasons::add);

        return List.copyOf(reasons);
    }

    /**
     * 사회적 근거 하나를 고른다 — <b>🎫 &gt; 🧳 &gt; 🏛</b>.
     *
     * <p>자격 게이팅을 통과한 전문가의 판단이 가장 강하고, 그다음이 또래의 실제 행동,
     * 그다음이 기관 자료다. 하나만 보여주므로 이 서열이 곧 "무엇이 보일지"를 정한다.
     */
    private static java.util.Optional<Reason> social(int verifiedGuideCount, int travelerCount,
                                                     List<InsightView> insights) {
        if (verifiedGuideCount > 0) {
            return java.util.Optional.of(new Reason("guide_course", verifiedGuideCount, null, null, null));
        }
        if (travelerCount >= MIN_TRAVELERS) {
            return java.util.Optional.of(new Reason("traveler_saved", travelerCount, null, null, null));
        }
        return insights.stream()
                // 출처 없는 사실은 신뢰도가 아무리 높아도 배지가 될 수 없다
                .filter(i -> i.publisher() != null && !i.publisher().isBlank())
                .max(Comparator.comparingDouble(i -> i.confidence() == null ? 0 : i.confidence()))
                .map(i -> new Reason("official", null, i.publisher(), i.kind(), null));
    }

    private static java.util.Optional<Reason> walk(Integer meters) {
        if (meters == null || meters > WALKABLE_MAX_METERS) return java.util.Optional.empty();
        // 아주 가까워도 "도보 0분"은 말이 안 된다
        int minutes = Math.max(1, (int) Math.round(meters / 1000.0 / WALK_KMH * 60));
        return java.util.Optional.of(new Reason("walk", null, null, null, minutes));
    }
}
