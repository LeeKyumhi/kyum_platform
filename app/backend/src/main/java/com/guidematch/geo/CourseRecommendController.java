package com.guidematch.geo;

import com.guidematch.knowledge.PlaceInsightLookup;
import com.guidematch.knowledge.SignalRecorder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** 테마 → 정차 슬롯 순서 (facet 키). 각 슬롯마다 이전 정차지에서 가까운 후보를 뽑는다. */
    private static final Map<String, List<String>> THEME_SLOTS = Map.of(
            "attraction", List.of("attraction", "attraction", "food", "attraction", "cafe"),
            "food",       List.of("attraction", "food", "market", "food", "cafe"),
            "cafe",       List.of("attraction", "cafe", "food", "cafe", "cafe"),
            "culture",    List.of("culture", "attraction", "food", "culture", "cafe"),
            "market",     List.of("market", "food", "attraction", "market", "cafe"),
            "mixed",      List.of("attraction", "food", "culture", "cafe", "market")
    );

    private static final int MINUTES_PER_STOP = 40;
    private static final double WALK_KMH = 4.0;

    private final CoursePlanner coursePlanner;
    private final TranslationService translationService;
    private final PlaceInsightLookup insightLookup;
    private final SignalRecorder signalRecorder;

    public CourseRecommendController(CoursePlanner coursePlanner,
                                     TranslationService translationService,
                                     PlaceInsightLookup insightLookup,
                                     SignalRecorder signalRecorder) {
        this.coursePlanner = coursePlanner;
        this.translationService = translationService;
        this.insightLookup = insightLookup;
        this.signalRecorder = signalRecorder;
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
                    coursePlanner.isKakaoEnabled(), List.of(), 0, 0);
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

        // 무엇이 노출됐는지 지금 남겨두지 않으면 영원히 못 되찾는다.
        // "추천에는 나왔는데 인사이트가 없는 장소"가 곧 다음 수집 우선순위이기도 하다.
        signalRecorder.recordShown(
                picked.stream()
                        .map(p -> new SignalRecorder.StopRef(p.placeId(), p.kakaoPlaceId()))
                        .toList(),
                target.key() + "/" + (plan.resolvedDistrict() == null ? "" : plan.resolvedDistrict())
                        + "/" + theme,
                userId);

        // kakaoEnabled는 실제 값을 싣는다 — 프론트가 지도·장소 링크 노출을 이 값으로 판단하므로
        // true로 못 박으면 키가 없을 때 깨진 지도를 띄우게 된다.
        return new RecommendResponse(target.key(), plan.resolvedDistrict(), theme,
                coursePlanner.isKakaoEnabled(), stops, totalMeters, suggestedHours);
    }

    /**
     * 카테고리 전체 경로("음식점 &gt; 카페 &gt; …")는 마지막 segment만 취하고, lang != ko면 번역.
     * 여기서 축적된 인사이트도 함께 붙인다 — <b>정차지 수와 무관하게 최대 쿼리 3회</b>
     * (레지스트리 1회 + Kakao 폴백 2회, {@link PlaceInsightLookup}).
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

        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            CoursePlanner.PlannedStop p = picked.get(i);
            List<PlaceInsightLookup.InsightView> insights = p.placeId() != null
                    ? byPlace.getOrDefault(p.placeId(), List.of())
                    : byKakao.getOrDefault(p.kakaoPlaceId(), List.of());
            stops.add(new Stop(
                    i + 1, tNames.get(i),
                    tCats.get(i).isBlank() ? shortCats.get(i) : tCats.get(i),
                    p.address(), // 주소는 한국어 유지 (택시/지도 편의)
                    p.lat(), p.lng(), p.placeUrl(),
                    legMeters.get(i),
                    p.source(),
                    insights));
        }
        return stops;
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
            /** 아직 수집 안 된 장소는 빈 배열 — 프론트는 있으면 보여주고 없으면 무시하면 된다. */
            List<PlaceInsightLookup.InsightView> insights
    ) {}

    public record RecommendResponse(
            String city, String district, String theme, boolean kakaoEnabled,
            List<Stop> stops, int totalDistanceMeters, int suggestedDurationHours
    ) {}
}
