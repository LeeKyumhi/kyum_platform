package com.guidematch.geo;

import com.guidematch.knowledge.GuideCourseSignalLookup;
import com.guidematch.knowledge.PlaceInsightLookup;
import com.guidematch.knowledge.PlaceMediaLookup;
import com.guidematch.knowledge.SignalRecorder;
import com.guidematch.knowledge.TravelerSignalLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 가이드용 투어 코스 추천 (인증 필요 — SecurityConfig public 목록에 없음).
 *
 * <p>정차지 선정은 {@link CoursePlanner}가 한다 — <b>우리 레지스트리가 먼저, Kakao는 폴백.</b>
 * 여기서는 테마→슬롯 정의, 거리·소요시간 산식, 번역, 인사이트 부착, 신호 기록만 담당한다.
 * 후보 중 무작위 선택이 섞여 있어 재호출 시마다 조금씩 다른 코스가 나온다("다시 추천").
 *
 * <p>Kakao 키가 없어도 <b>더 이상 빈 목록이 아니다</b> — 레지스트리에 수집된 범위는 그대로 동작한다.
 */
@RestController
public class CourseRecommendController {

    private static final Logger log = LoggerFactory.getLogger(CourseRecommendController.class);

    /**
     * 테마 → 정차 슬롯 순서 (facet 키). 각 슬롯마다 이전 정차지에서 가까운 후보를 뽑는다.
     *
     * <p><b>규칙: 고른 테마가 과반(5칸 중 3칸)</b>. 예전에는 맛집을 골라도 맛집이 2칸뿐이라
     * 사용자에게는 선택이 무시된 것으로 읽혔다. 그렇다고 5칸을 전부 같은 종류로 채우면
     * "밥 5끼 코스"가 되어 동선이 성립하지 않으므로, 쉬어가는 자리를 2칸 남긴다.
     * 순서는 방문 순서이기도 해서 같은 종류가 연달아 붙지 않게 사이에 끼워 넣는다.
     *
     * <p>믹스만 예외 — 골고루가 그 테마의 정체성이라 5칸을 모두 다른 종류로 둔다.
     */
    private static final Map<String, List<String>> THEME_SLOTS = Map.of(
            "attraction", List.of("attraction", "attraction", "food", "attraction", "cafe"),
            "food",       List.of("food", "cafe", "food", "market", "food"),
            "cafe",       List.of("cafe", "attraction", "cafe", "food", "cafe"),
            "culture",    List.of("culture", "culture", "food", "culture", "cafe"),
            "market",     List.of("market", "food", "market", "cafe", "market"),
            "mixed",      List.of("attraction", "food", "culture", "cafe", "market")
    );

    private static final int MINUTES_PER_STOP = 40;
    private static final double WALK_KMH = 4.0;

    private final CoursePlanner coursePlanner;
    private final TranslationService translationService;
    private final PlaceInsightLookup insightLookup;
    private final GuideCourseSignalLookup guideCourseLookup;
    private final TravelerSignalLookup travelerLookup;
    private final SignalRecorder signalRecorder;
    private final PlaceMediaLookup mediaLookup;

    public CourseRecommendController(CoursePlanner coursePlanner,
                                     TranslationService translationService,
                                     PlaceInsightLookup insightLookup,
                                     GuideCourseSignalLookup guideCourseLookup,
                                     TravelerSignalLookup travelerLookup,
                                     SignalRecorder signalRecorder,
                                     PlaceMediaLookup mediaLookup) {
        this.coursePlanner = coursePlanner;
        this.translationService = translationService;
        this.insightLookup = insightLookup;
        this.guideCourseLookup = guideCourseLookup;
        this.travelerLookup = travelerLookup;
        this.signalRecorder = signalRecorder;
        this.mediaLookup = mediaLookup;
    }

    @GetMapping("/api/courses/recommend")
    public RecommendResponse recommend(
            @RequestParam String city,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "mixed") String theme,
            @RequestParam(defaultValue = "ko") String lang,
            // 신호 기록용. public 엔드포인트가 되더라도 null로 들어올 뿐 401을 던지지 않는다.
            @AuthenticationPrincipal Long userId
    ) {
        KoreanCity target = KoreanCity.LIST.stream()
                .filter(c -> c.key().equalsIgnoreCase(city))
                .findFirst()
                .orElse(null);
        List<String> slots = THEME_SLOTS.getOrDefault(theme, THEME_SLOTS.get("mixed"));

        if (target == null) {
            return new RecommendResponse(city, null, theme,
                    coursePlanner.isKakaoEnabled(), List.of(), 0, 0, null);
        }

        CoursePlanner.Plan plan = coursePlanner.plan(target, district, slots);
        List<CoursePlanner.PlannedStop> picked = plan.stops();

        // 구간 거리 + 총 이동 거리
        List<Integer> legMeters = new ArrayList<>();
        int totalMeters = 0;
        for (int i = 0; i < picked.size(); i++) {
            if (i == 0) { legMeters.add(null); continue; }
            CoursePlanner.PlannedStop prev = picked.get(i - 1), cur = picked.get(i);
            int m = (int) Math.round(GeoUtils.distanceKm(
                    prev.lat(), prev.lng(), cur.lat(), cur.lng()) * 1000);
            legMeters.add(m);
            totalMeters += m;
        }

        int suggestedHours = picked.isEmpty() ? 0 : (int) Math.min(8, Math.max(2, Math.round(
                (picked.size() * MINUTES_PER_STOP + (totalMeters / 1000.0) / WALK_KMH * 60) / 60.0)));

        List<Stop> stops = toStops(picked, legMeters, lang);

        // SHOWN과 ADDED를 같은 키로 짝지으려면 프론트도 이 값을 알아야 한다 —
        // 그래서 만들어 쓰고 버리지 않고 응답에도 싣는다.
        String courseRef = target.key() + "/"
                + (plan.resolvedDistrict() == null ? "" : plan.resolvedDistrict()) + "/" + theme;

        // 무엇이 노출됐는지 지금 남겨두지 않으면 영원히 못 되찾는다.
        // "추천에는 나왔는데 인사이트가 없는 장소"가 곧 다음 수집 우선순위이기도 하다.
        signalRecorder.recordShown(
                picked.stream()
                        .map(p -> new SignalRecorder.StopRef(p.placeId(), p.kakaoPlaceId()))
                        .toList(),
                courseRef, userId);

        // kakaoEnabled는 실제 값을 싣는다 — 프론트가 지도·장소 링크 노출을 이 값으로 판단하므로
        // true로 못 박으면 키가 없을 때 깨진 지도를 띄우게 된다.
        return new RecommendResponse(target.key(), plan.resolvedDistrict(), theme,
                coursePlanner.isKakaoEnabled(), stops, totalMeters, suggestedHours, courseRef);
    }

    /**
     * 추천 정차지를 일정·코스에 담았다고 알린다 (fire-and-forget).
     *
     * <p><b>왜 별도 엔드포인트인가</b>: 드래그는 순수 프론트 동작이고, 서버가 처음 소식을 듣는 것은
     * 한참 뒤의 일정 전체 PUT이다. 그 payload에는 "이 항목이 추천에서 왔다"는 표식도,
     * {@code courseRef}도 없다. 일정·코스 저장 계약에 추천 출처를 끼워 넣는 대신
     * 담는 순간에만 쓰는 얇은 통로를 따로 둔다.
     *
     * <p>저장하지 않고 담기만 한 것도 기록된다 — "담았다"는 의도 신호로는 그게 맞다.
     */
    @PostMapping("/api/courses/recommend/signals")
    public void recordAdded(@RequestBody AddedSignalRequest req,
                            @AuthenticationPrincipal Long userId) {
        signalRecorder.recordAdded(
                new SignalRecorder.StopRef(req.placeId(), req.kakaoPlaceId()),
                req.courseRef(), userId);
    }

    public record AddedSignalRequest(Long placeId, String kakaoPlaceId, String courseRef) {}

    /**
     * 카테고리 전체 경로("음식점 &gt; 카페 &gt; …")는 마지막 segment만 취하고, lang != ko면 번역.
     * 여기서 축적된 인사이트와 추천 근거도 함께 붙인다 — <b>정차지 수와 무관하게 최대 쿼리 7회</b>
     * (인사이트: 레지스트리 1회 + Kakao 폴백 2회 {@link PlaceInsightLookup},
     * 🎫: 코스 1회 + 가이드 1회 {@link GuideCourseSignalLookup},
     * 🧳: 찜 1회 + 일정 1회 {@link TravelerSignalLookup}). 전부 배치라 정차지가 늘어도 늘지 않는다.
     */
    private List<Stop> toStops(List<CoursePlanner.PlannedStop> picked,
                               List<Integer> legMeters, String lang) {
        List<String> names = picked.stream().map(CoursePlanner.PlannedStop::name).toList();
        List<String> shortCats = picked.stream().map(p -> {
            String c = p.category() != null ? p.category() : "";
            int idx = c.lastIndexOf(" > ");
            return idx >= 0 ? c.substring(idx + 3) : c;
        }).toList();

        String googleLang = GoogleTranslateClient.toGoogleLang(lang);
        List<String> tNames = googleLang != null && !names.isEmpty()
                ? translationService.translate(names, googleLang) : names;
        List<String> tCats = googleLang != null && !shortCats.isEmpty()
                ? translationService.translate(shortCats, googleLang) : shortCats;

        // 레지스트리 정차지는 place_id를 이미 안다 — kakao id를 거칠 이유가 없다
        Map<Long, List<PlaceInsightLookup.InsightView>> byPlace = insightLookup.byPlaceIds(
                picked.stream().map(CoursePlanner.PlannedStop::placeId)
                        .filter(Objects::nonNull).toList(), lang);
        // 폴백 정차지는 우리 레지스트리에 있을 수도, 없을 수도 있다
        Map<String, List<PlaceInsightLookup.InsightView>> byKakao = insightLookup.byKakaoPlaceIds(
                picked.stream().filter(p -> p.placeId() == null)
                        .map(CoursePlanner.PlannedStop::kakaoPlaceId)
                        .filter(Objects::nonNull).toList(), lang);

        // 사람 근거 — 🎫는 waypoint, 🧳는 찜·일정. 둘 다 Kakao place id가 조인 키다.
        List<String> kakaoIds = picked.stream()
                .map(CoursePlanner.PlannedStop::kakaoPlaceId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, Integer> guideCounts   = counts("가이드 코스", kakaoIds, guideCourseLookup::verifiedGuideCounts);
        Map<String, Integer> travelerCounts = counts("여행자 담기", kakaoIds, travelerLookup::travelerCounts);

        // 사진 — 정차지는 두 종류가 섞여 있다. kakao id가 있으면 그 키로(두 출신 노트를 합쳐서),
        // 레지스트리 전용(kakao id 없음, 실측 19곳 중 11곳)은 place id로 조회한다.
        // 후자를 빼면 그 장소들의 사진은 구조적으로 도달하지 못한다.
        Map<String, PlaceMediaLookup.Cover> coversByKakao = media("kakao", () ->
                kakaoIds.isEmpty() ? Map.<String, PlaceMediaLookup.Cover>of()
                                   : mediaLookup.coversByKakaoIds(kakaoIds));
        List<Long> registryOnlyIds = picked.stream()
                .filter(p -> p.kakaoPlaceId() == null && p.placeId() != null)
                .map(CoursePlanner.PlannedStop::placeId).distinct().toList();
        Map<Long, PlaceMediaLookup.Cover> coversByPlace = media("registry", () ->
                registryOnlyIds.isEmpty() ? Map.<Long, PlaceMediaLookup.Cover>of()
                                          : mediaLookup.coversByPlaceIds(registryOnlyIds));

        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            CoursePlanner.PlannedStop p = picked.get(i);
            PlaceMediaLookup.Cover cover = p.kakaoPlaceId() != null
                    ? coversByKakao.get(p.kakaoPlaceId())
                    : (p.placeId() == null ? null : coversByPlace.get(p.placeId()));
            List<PlaceInsightLookup.InsightView> insights = p.placeId() != null
                    ? byPlace.getOrDefault(p.placeId(), List.of())
                    : byKakao.getOrDefault(p.kakaoPlaceId(), List.of());
            int guideCount    = p.kakaoPlaceId() == null ? 0 : guideCounts.getOrDefault(p.kakaoPlaceId(), 0);
            int travelerCount = p.kakaoPlaceId() == null ? 0 : travelerCounts.getOrDefault(p.kakaoPlaceId(), 0);
            stops.add(new Stop(
                    i + 1, tNames.get(i),
                    tCats.get(i).isBlank() ? shortCats.get(i) : tCats.get(i),
                    p.address(), // 주소는 한국어 유지 (택시/지도 편의)
                    p.lat(), p.lng(), p.placeUrl(),
                    legMeters.get(i),
                    p.source(),
                    p.placeId(), p.kakaoPlaceId(),
                    insights,
                    // insights는 상세용 원본 그대로 남긴다 — reasons는 앞면 요약이지 대체가 아니다
                    CourseReasons.build(guideCount, travelerCount, insights, legMeters.get(i)),
                    cover == null ? null : cover.thumbUrl(),
                    cover == null ? null : cover.photoCount(),
                    cover == null ? null : cover.officialUrl(),
                    cover == null ? null : cover.officialPublisher()));
        }
        return stops;
    }

    /**
     * 사진도 근거와 같다 — 없어도 추천은 나가야 한다. 사진 조회 실패로 코스가 통째로
     * 비면 본말전도다({@code counts()}와 같은 격리 원칙).
     */
    private <K> Map<K, PlaceMediaLookup.Cover> media(
            String what, java.util.function.Supplier<Map<K, PlaceMediaLookup.Cover>> lookup) {
        try {
            return lookup.get();
        } catch (Exception e) {
            log.warn("{} 정차지 사진 조회 실패 — 사진 없이 진행: {}", what, e.toString());
            return Map.of();
        }
    }

    /**
     * 근거는 부가 정보지 본체가 아니다 — 집계가 죽어도 추천은 나가야 한다.
     * 여기서 예외가 새면 근거 하나 때문에 화면 전체가 빈다.
     */
    private Map<String, Integer> counts(String what, List<String> kakaoIds,
                                        java.util.function.Function<List<String>, Map<String, Integer>> lookup) {
        if (kakaoIds.isEmpty()) return Map.of();
        try {
            return lookup.apply(kakaoIds);
        } catch (Exception e) {
            log.warn("{} 근거 집계 실패 — 근거 없이 진행: {}", what, e.toString());
            return Map.of();
        }
    }

    public record Stop(
            int order, String name, String category, String address,
            Double latitude, Double longitude, String placeUrl,
            Integer distanceFromPrevMeters,
            /**
             * "registry" | "kakao". 프론트는 무시해도 되지만 응답에는 반드시 실린다 —
             * 백필 누락 같은 조용한 실패를 밖에서 잡아내는 유일한 관측 지점이다.
             * 전부 "kakao"면 레지스트리가 아무 일도 안 하고 있다는 뜻이고, 그 상태에서도
             * 응답은 완벽히 정상으로 보인다.
             */
            String source,
            /**
             * 레지스트리 장소 id. Kakao 폴백 정차지는 null.
             */
            Long placeId,
            /**
             * ★ Kakao 장소 id — <b>일정·코스에 담을 때 저장되는 값이다.</b>
             * 이걸 안 실어 보내면 프론트가 식별자를 합성해 쓰고, 그 합성값이
             * {@code tour_course_waypoints.place_id}에 그대로 저장돼 카카오맵 링크가 깨지고
             * 🎫 근거 집계의 조인 키가 오염된다. 폴백 장소도 Kakao id는 있으므로 대개 non-null.
             */
            String kakaoPlaceId,
            /** 아직 수집 안 된 장소는 빈 배열 — 프론트는 있으면 보여주고 없으면 무시하면 된다. */
            List<PlaceInsightLookup.InsightView> insights,
            /**
             * 앞면에 보여줄 추천 근거 (최대 2개, 없으면 빈 배열).
             * {@code insights}를 대체하지 않는다 — 상세 모달은 계속 원본을 쓴다.
             */
            List<CourseReasons.Reason> reasons,
            /**
             * 대표 사진 — 여행자 사진이 있으면 그 최신, 없으면 공식 사진. 없으면 null.
             * (규칙은 {@link PlaceMediaLookup} 한 곳에서만 정한다)
             */
            String coverPhotoUrl,
            /** 여행자 사진 수. 0이면 null — "0장"을 담아 보내지 않는다. */
            Integer photoCount,
            /** 공식 사진과 발행처. <b>쌍으로만</b> 실린다(계약 §16). */
            String officialPhotoUrl,
            String officialPhotoPublisher
    ) {}

    public record RecommendResponse(
            String city, String district, String theme, boolean kakaoEnabled,
            List<Stop> stops, int totalDistanceMeters, int suggestedDurationHours,
            /** SHOWN↔ADDED 신호를 짝짓는 키 ("Seoul/종로구/mixed"). 도시를 못 찾으면 null. */
            String courseRef
    ) {}
}
