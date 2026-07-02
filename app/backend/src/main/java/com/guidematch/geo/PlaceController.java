package com.guidematch.geo;

import com.guidematch.geo.KakaoLocalClient.Place;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private static final int RADIUS_METERS = 20000; // Kakao 최대 반경(20km)

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

    public PlaceController(KakaoLocalClient kakaoClient) {
        this.kakaoClient = kakaoClient;
    }

    @GetMapping("/api/places")
    public PlacesResponse places(
            @RequestParam String city,
            @RequestParam(defaultValue = "attraction") String category
    ) {
        KoreanCity target = KoreanCity.LIST.stream()
                .filter(c -> c.key().equalsIgnoreCase(city))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return new PlacesResponse(city, category, kakaoClient.isEnabled(), List.of());
        }

        List<Place> places;
        String code = CATEGORY_CODES.get(category);
        if (code != null) {
            places = kakaoClient.searchByCategory(code, target.lat(), target.lng(), RADIUS_METERS);
        } else {
            String keyword = KEYWORDS.getOrDefault(category, category);
            places = kakaoClient.searchByKeyword(keyword, target.lat(), target.lng(), RADIUS_METERS);
        }

        return new PlacesResponse(target.key(), category, kakaoClient.isEnabled(), places);
    }

    public record PlacesResponse(String city, String category, boolean kakaoEnabled, List<Place> places) {}
}
