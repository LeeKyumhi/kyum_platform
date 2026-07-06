package com.guidematch.geo;

import com.guidematch.geo.KakaoLocalClient.Place;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 가이드용 투어 코스 추천 (인증 필요 — SecurityConfig public 목록에 없음).
 * 도시(+구) 중심으로 테마별 카테고리 장소를 Kakao에서 모아
 * 최근접 이웃(greedy nearest-neighbor)으로 걷기 좋은 동선 4~5곳을 구성한다.
 * 후보 중 무작위 선택이 섞여 있어 재호출 시마다 조금씩 다른 코스가 나온다("다시 추천").
 * Kakao 키가 없으면 kakaoEnabled=false + 빈 목록 (앱은 정상 동작).
 */
@RestController
public class CourseRecommendController {

    private static final int CITY_RADIUS_METERS = 10000;    // 도시 중심 — 코스용이라 20km보다 좁게
    private static final int DISTRICT_RADIUS_METERS = 4000; // 구 단위 — 도보 동선 위주

    /** facet 키 → Kakao 카테고리 그룹 코드 (PlaceController와 동일 규약) */
    private static final Map<String, String> CATEGORY_CODES = Map.of(
            "attraction", "AT4",
            "food",       "FD6",
            "cafe",       "CE7",
            "culture",    "CT1"
    );

    /** facet 키 → keyword.json 검색어 (카테고리 코드가 없는 경우) */
    private static final Map<String, String> KEYWORDS = Map.of(
            "market", "전통시장"
    );

    /** 테마 → 정차 슬롯 순서 (facet 키). 각 슬롯마다 이전 정차지에서 가까운 후보를 뽑는다. */
    private static final Map<String, List<String>> THEME_SLOTS = Map.of(
            "attraction", List.of("attraction", "attraction", "food", "attraction", "cafe"),
            "food",       List.of("attraction", "food", "market", "food", "cafe"),
            "cafe",       List.of("attraction", "cafe", "food", "cafe", "cafe"),
            "culture",    List.of("culture", "attraction", "food", "culture", "cafe"),
            "market",     List.of("market", "food", "attraction", "market", "cafe"),
            "mixed",      List.of("attraction", "food", "culture", "cafe", "market")
    );

    /** 슬롯당 무작위 선택 폭 — 가까운 후보 상위 N 중 랜덤 (동선 유지 + 재추천 다양성) */
    private static final int PICK_POOL = 3;
    private static final int MINUTES_PER_STOP = 40;
    private static final double WALK_KMH = 4.0;

    private final KakaoLocalClient kakaoClient;
    private final TranslationService translationService;

    public CourseRecommendController(KakaoLocalClient kakaoClient, TranslationService translationService) {
        this.kakaoClient = kakaoClient;
        this.translationService = translationService;
    }

    @GetMapping("/api/courses/recommend")
    public RecommendResponse recommend(
            @RequestParam String city,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "mixed") String theme,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        KoreanCity target = KoreanCity.LIST.stream()
                .filter(c -> c.key().equalsIgnoreCase(city))
                .findFirst()
                .orElse(null);
        List<String> slots = THEME_SLOTS.getOrDefault(theme, THEME_SLOTS.get("mixed"));

        if (target == null || !kakaoClient.isEnabled()) {
            return new RecommendResponse(city, null, theme, kakaoClient.isEnabled(), List.of(), 0, 0);
        }

        // 중심 좌표: 도시 기본, 유효한 구가 지정되면 구 중심으로 좁힘 (PlaceController와 동일 패턴)
        double lat = target.lat(), lng = target.lng();
        int radius = CITY_RADIUS_METERS;
        String resolvedDistrict = null;
        boolean validDistrict = district != null && !district.isBlank()
                && KoreanCity.districtsOf(target.key()).stream().anyMatch(x -> x.ko().equals(district));
        if (validDistrict) {
            double[] coord = kakaoClient.geocodeRegion(target.nameKo() + " " + district);
            if (coord != null) {
                lat = coord[0];
                lng = coord[1];
                radius = DISTRICT_RADIUS_METERS;
                resolvedDistrict = district;
            }
        }

        // 테마에 등장하는 카테고리별로 1회씩만 Kakao 검색 (슬롯 수만큼 호출하지 않음)
        Map<String, List<Place>> pool = new HashMap<>();
        for (String facet : new HashSet<>(slots)) {
            String code = CATEGORY_CODES.get(facet);
            List<Place> places = code != null
                    ? kakaoClient.searchByCategory(code, lat, lng, radius)
                    : kakaoClient.searchByKeyword(KEYWORDS.getOrDefault(facet, facet), lat, lng, radius);
            pool.put(facet, places.stream()
                    .filter(p -> p.latitude() != null && p.longitude() != null)
                    .toList());
        }

        // greedy nearest-neighbor: 이전 정차지에서 가까운 상위 PICK_POOL 중 랜덤 선택
        List<Place> picked = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        double prevLat = lat, prevLng = lng;
        for (String facet : slots) {
            double fLat = prevLat, fLng = prevLng;
            List<Place> candidates = pool.getOrDefault(facet, List.of()).stream()
                    .filter(p -> !usedIds.contains(p.id()) && !usedNames.contains(p.name()))
                    .sorted((a, b) -> Double.compare(
                            GeoUtils.distanceKm(fLat, fLng, a.latitude(), a.longitude()),
                            GeoUtils.distanceKm(fLat, fLng, b.latitude(), b.longitude())))
                    .limit(PICK_POOL)
                    .toList();
            if (candidates.isEmpty()) continue; // 이 슬롯은 건너뜀 (예: 구 안에 전통시장 없음)
            Place chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            picked.add(chosen);
            usedIds.add(chosen.id());
            usedNames.add(chosen.name());
            prevLat = chosen.latitude();
            prevLng = chosen.longitude();
        }

        // 구간 거리 + 총 이동 거리
        List<Integer> legMeters = new ArrayList<>();
        int totalMeters = 0;
        for (int i = 0; i < picked.size(); i++) {
            if (i == 0) { legMeters.add(null); continue; }
            Place prev = picked.get(i - 1), cur = picked.get(i);
            int m = (int) Math.round(GeoUtils.distanceKm(
                    prev.latitude(), prev.longitude(), cur.latitude(), cur.longitude()) * 1000);
            legMeters.add(m);
            totalMeters += m;
        }

        int suggestedHours = picked.isEmpty() ? 0 : (int) Math.min(8, Math.max(2, Math.round(
                (picked.size() * MINUTES_PER_STOP + (totalMeters / 1000.0) / WALK_KMH * 60) / 60.0)));

        List<Stop> stops = toStops(picked, legMeters, lang);
        return new RecommendResponse(target.key(), resolvedDistrict, theme,
                true, stops, totalMeters, suggestedHours);
    }

    /** Kakao 카테고리 전체 경로("음식점 > 카페 > …")는 마지막 segment만 취하고, lang != ko면 번역. */
    private List<Stop> toStops(List<Place> picked, List<Integer> legMeters, String lang) {
        List<String> names = picked.stream().map(Place::name).toList();
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

        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            Place p = picked.get(i);
            stops.add(new Stop(
                    i + 1, tNames.get(i),
                    tCats.get(i).isBlank() ? shortCats.get(i) : tCats.get(i),
                    p.address(), // 주소는 한국어 유지 (택시/지도 편의)
                    p.latitude(), p.longitude(), p.placeUrl(),
                    legMeters.get(i)
            ));
        }
        return stops;
    }

    public record Stop(
            int order, String name, String category, String address,
            Double latitude, Double longitude, String placeUrl,
            Integer distanceFromPrevMeters
    ) {}

    public record RecommendResponse(
            String city, String district, String theme, boolean kakaoEnabled,
            List<Stop> stops, int totalDistanceMeters, int suggestedDurationHours
    ) {}
}
