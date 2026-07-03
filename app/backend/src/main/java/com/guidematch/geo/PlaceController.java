package com.guidematch.geo;

import com.guidematch.geo.KakaoLocalClient.Place;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public PlaceController(KakaoLocalClient kakaoClient, TranslationService translationService) {
        this.kakaoClient = kakaoClient;
        this.translationService = translationService;
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

        // lang != "ko" 면 장소명/카테고리 번역 (캐시 우선, 원문 폴백)
        String googleLang = GoogleTranslateClient.toGoogleLang(lang);
        List<Place> result = places;
        if (googleLang != null && !places.isEmpty()) {
            List<String> names      = places.stream().map(Place::name).toList();
            List<String> categories = places.stream().map(p -> p.category() != null ? p.category() : "").toList();
            List<String> tNames = translationService.translate(names, googleLang);
            List<String> tCats  = translationService.translate(categories, googleLang);
            result = new ArrayList<>();
            for (int i = 0; i < places.size(); i++) {
                Place p = places.get(i);
                result.add(new Place(
                        p.id(), tNames.get(i),
                        tCats.get(i).isBlank() ? p.category() : tCats.get(i),
                        p.categoryGroupCode(), p.phone(),
                        p.address(),   // 주소는 한국어 유지 (택시/지도 사용 편의)
                        p.latitude(), p.longitude(), p.placeUrl(), p.distanceMeters()
                ));
            }
        }

        return new PlacesResponse(target.key(), resolvedDistrict, category, kakaoClient.isEnabled(), result);
    }

    public record PlacesResponse(String city, String district, String category, boolean kakaoEnabled, List<Place> places) {}
}
