package com.guidematch.geo;

import com.guidematch.geo.KakaoLocalClient.Place;
import com.guidematch.knowledge.GuideCourseSignalLookup;
import com.guidematch.knowledge.PlaceInsightLookup;
import com.guidematch.knowledge.PlaceMediaLookup;
import com.guidematch.knowledge.TravelerSignalLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 지역 장소 검색 (공개, Phase 2).
 * 표준 도시(city) + 카테고리 facet → Kakao Local API 프록시.
 * Kakao 키가 없으면 kakaoEnabled=false + 빈 목록을 반환하고 앱은 정상 동작한다.
 *
 * 프론트가 넘기는 category facet 키를 Kakao 검색 방식으로 매핑:
 *  - attraction/food/cafe/culture → category_group_code (AT4/FD6/CE7/CT1)
 *  - market(상권/전통시장)         → 카테고리 코드가 없어 keyword.json("전통시장")으로 검색
 */
@RestController
public class PlaceController {

    private static final Logger log = LoggerFactory.getLogger(PlaceController.class);

    private static final int RADIUS_METERS = 20000;         // Kakao 최대 반경(20km) — 도시 전체
    private static final int DISTRICT_RADIUS_METERS = 6000;  // 구 단위로 좁힐 때 반경

    /** facet 키 → Kakao 카테고리 그룹 코드 */
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

    private final KakaoLocalClient kakaoClient;
    private final TranslationService translationService;
    private final PlaceInsightLookup insightLookup;
    private final GuideCourseSignalLookup guideCourseLookup;
    private final TravelerSignalLookup travelerLookup;
    private final PlaceMediaLookup mediaLookup;

    public PlaceController(KakaoLocalClient kakaoClient,
                           TranslationService translationService,
                           PlaceInsightLookup insightLookup,
                           GuideCourseSignalLookup guideCourseLookup,
                           TravelerSignalLookup travelerLookup,
                           PlaceMediaLookup mediaLookup) {
        this.kakaoClient = kakaoClient;
        this.translationService = translationService;
        this.insightLookup = insightLookup;
        this.guideCourseLookup = guideCourseLookup;
        this.travelerLookup = travelerLookup;
        this.mediaLookup = mediaLookup;
    }

    @GetMapping("/api/places")
    public PlacesResponse places(
            @RequestParam String city,
            @RequestParam(defaultValue = "attraction") String category,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        KoreanCity target = KoreanCity.LIST.stream()
                .filter(c -> c.key().equalsIgnoreCase(city))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return new PlacesResponse(city, null, category, kakaoClient.isEnabled(), List.of());
        }

        // 기본은 도시 중심 + 20km. 유효한 구가 지정되면 그 구 좌표로 좁힌다(6km).
        double lat = target.lat(), lng = target.lng();
        int radius = RADIUS_METERS;
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

        List<Place> places;
        String code = CATEGORY_CODES.get(category);
        if (code != null) {
            places = kakaoClient.searchByCategory(code, lat, lng, radius);
        } else {
            String keyword = KEYWORDS.getOrDefault(category, category);
            places = kakaoClient.searchByKeyword(keyword, lat, lng, radius);
        }

        List<Place> result = recommendFirst(translateIfNeeded(places, lang), lang);
        return new PlacesResponse(target.key(), resolvedDistrict, category, kakaoClient.isEnabled(), result);
    }

    /**
     * 좌표 기준 주변 장소 검색 (공개). 명소 상세 페이지에서 "주변 식당/카페"용.
     * city/구 단위가 아니라 임의의 지점(lat,lng) 중심으로 좁게(기본 2km) 검색한다.
     */
    @GetMapping("/api/places/nearby")
    public PlacesResponse nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "food") String category,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        int radius = 2000;
        List<Place> places;
        String code = CATEGORY_CODES.get(category);
        if (code != null) {
            places = kakaoClient.searchByCategory(code, lat, lng, radius);
        } else {
            String keyword = KEYWORDS.getOrDefault(category, category);
            places = kakaoClient.searchByKeyword(keyword, lat, lng, radius);
        }

        List<Place> result = translateIfNeeded(places, lang);
        return new PlacesResponse(null, null, category, kakaoClient.isEnabled(), result);
    }

    /**
     * 자유 키워드 장소 검색 (만남 장소 지정용, T1). 위치 바이어스 없이 전국 정확도순.
     * SecurityConfig permitAll에 없어 인증 필요(채팅 사용자는 항상 로그인) — 프론트에서 auth 헤더 전달.
     */
    @GetMapping("/api/places/search")
    public PlacesResponse search(
            @RequestParam String query,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        if (query == null || query.isBlank()) {
            return new PlacesResponse(null, null, null, kakaoClient.isEnabled(), List.of());
        }
        List<Place> places = kakaoClient.searchByKeyword(query.trim());
        List<Place> result = translateIfNeeded(places, lang);
        return new PlacesResponse(null, null, null, kakaoClient.isEnabled(), result);
    }

    /**
     * 추천순으로 세우고 "왜 위에 있는지"를 각 장소에 붙인다.
     *
     * <p>Kakao 순서는 거리·정확도일 뿐이라 "왜 여기를 가야 하는가"에 답하지 못한다.
     * 우리가 가진 근거로 앞에 세우되, <b>세운 이유를 그대로 화면에 싣는다</b> — 순서와 설명이
     * 같은 규칙({@link PlaceRanking}·{@link CourseReasons})에서 나오므로 서로 어긋날 수 없다.
     *
     * <p>목록에는 이전 정차지가 없으므로 📍(도보) 근거는 만들지 않는다 — 기준 없는 숫자가 된다.
     *
     * <p><b>여기는 비로그인도 쓰는 공개 경로다.</b> 근거는 부가 정보라, 집계가 죽어도 목록은 나가야 한다.
     * 예외가 새면 로그인조차 안 한 사용자에게 탐색 화면이 통째로 깨진다.
     * 조회는 전부 배치 — 장소 수와 무관하게 최대 5회(인사이트 2 + 🎫 2 + 🧳 2 중 필요한 만큼)다.
     */
    private List<Place> recommendFirst(List<Place> places, String lang) {
        if (places.isEmpty()) return places;
        List<String> ids = places.stream().map(Place::id).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return places;

        Map<String, Integer> guideCounts    = counts("가이드 코스", () -> guideCourseLookup.verifiedGuideCounts(ids));
        Map<String, Integer> travelerCounts = counts("여행자 담기", () -> travelerLookup.travelerCounts(ids));
        Map<String, List<PlaceInsightLookup.InsightView>> insights = insights(ids, lang);

        List<Place> withReasons = places.stream().map(p -> p.withReasons(CourseReasons.build(
                guideCounts.getOrDefault(p.id(), 0),
                travelerCounts.getOrDefault(p.id(), 0),
                insights.getOrDefault(p.id(), List.of()),
                null   // 목록이라 이전 정차지가 없다
        ))).toList();

        List<Place> ranked = PlaceRanking.sort(withReasons, p -> new PlaceRanking.Signals(
                guideCounts.getOrDefault(p.id(), 0),
                travelerCounts.getOrDefault(p.id(), 0),
                p.reasons().stream().anyMatch(r -> "official".equals(r.kind()))));
        return attachMedia(ranked);
    }

    /**
     * 노트 사진을 붙인다. <b>실패해도 목록은 나간다</b> — 사진은 부가 정보이고
     * 이 경로는 비로그인도 쓴다. {@code counts()}와 같은 격리 원칙이다.
     */
    private List<Place> attachMedia(List<Place> places) {
        if (places.isEmpty()) return places;
        List<String> ids = places.stream().map(Place::id).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return places;

        Map<String, PlaceMediaLookup.Cover> covers;
        try {
            covers = mediaLookup.coversByKakaoIds(ids);
        } catch (Exception e) {
            log.warn("장소 노트 사진 조회 실패 — 사진 없이 진행: {}", e.toString());
            return places;
        }
        return places.stream().map(p -> {
            PlaceMediaLookup.Cover c = covers.get(p.id());
            // 없으면 null 유지 — 0을 담은 값을 만들지 않는다.
            return c == null ? p : p.withMedia(c.thumbUrl(), c.photoCount(),
                    c.officialUrl(), c.officialPublisher());
        }).toList();
    }

    private Map<String, Integer> counts(String what, java.util.function.Supplier<Map<String, Integer>> lookup) {
        try {
            return lookup.get();
        } catch (Exception e) {
            log.warn("{} 근거 집계 실패 — 근거 없이 진행: {}", what, e.toString());
            return Map.of();
        }
    }

    private Map<String, List<PlaceInsightLookup.InsightView>> insights(List<String> ids, String lang) {
        try {
            return insightLookup.byKakaoPlaceIds(ids, lang);
        } catch (Exception e) {
            log.warn("인사이트 조회 실패 — 근거 없이 진행: {}", e.toString());
            return Map.of();
        }
    }

    /** lang != "ko" 면 장소명/카테고리 번역 (캐시 우선, 원문 폴백). ko거나 결과가 없으면 그대로 반환. */
    private List<Place> translateIfNeeded(List<Place> places, String lang) {
        String googleLang = GoogleTranslateClient.toGoogleLang(lang);
        if (googleLang == null || places.isEmpty()) return places;

        List<String> names      = places.stream().map(Place::name).toList();
        List<String> categories = places.stream().map(p -> p.category() != null ? p.category() : "").toList();
        List<String> tNames = translationService.translate(names, googleLang);
        List<String> tCats  = translationService.translate(categories, googleLang);

        List<Place> result = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            result.add(new Place(
                    p.id(), tNames.get(i),
                    tCats.get(i).isBlank() ? p.category() : tCats.get(i),
                    p.categoryGroupCode(), p.phone(),
                    p.address(),   // 주소는 한국어 유지 (택시/지도 사용 편의)
                    p.latitude(), p.longitude(), p.placeUrl(), p.distanceMeters(),
                    p.reasons(),   // 번역이 근거를 떨어뜨리면 안 된다
                    p.coverPhotoUrl(), p.photoCount(),   // 번역이 사진을 떨어뜨리면 안 된다
                    p.officialPhotoUrl(), p.officialPhotoPublisher()
            ));
        }
        return result;
    }

    public record PlacesResponse(String city, String district, String category, boolean kakaoEnabled, List<Place> places) {}
}
