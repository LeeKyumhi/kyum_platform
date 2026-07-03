package com.guidematch.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Kakao Local REST API 클라이언트.
 *  - coord2regioncode: 좌표(lng,lat) → 행정구역 이름 (역지오코딩)
 * REST 키는 .env의 KAKAO_REST_API_KEY 에서 읽는다. 키가 없으면 기능이 비활성화되고
 * null을 반환해 앱은 정상 부팅/동작한다(수동 도시 선택은 그대로 가능).
 */
@Component
public class KakaoLocalClient {

    private final RestClient restClient = RestClient.create();
    private final String restApiKey;

    public KakaoLocalClient(@Value("${kakao.rest-api-key:}") String restApiKey) {
        this.restApiKey = restApiKey;
    }

    public boolean isEnabled() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    /**
     * 카테고리 그룹 코드로 주변 장소 검색 (category.json).
     * 코드 예: AT4 관광명소, FD6 음식점, CE7 카페, CT1 문화시설.
     * 키가 없거나 실패하면 빈 목록.
     */
    public List<Place> searchByCategory(String categoryCode, double lat, double lng, int radius) {
        if (!isEnabled()) return List.of();
        try {
            PlaceSearchResponse res = restClient.get()
                    .uri("https://dapi.kakao.com/v2/local/search/category.json"
                            + "?category_group_code={c}&x={x}&y={y}&radius={r}&size=15&sort=distance",
                            categoryCode, lng, lat, radius)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(PlaceSearchResponse.class);
            return toPlaces(res);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 키워드로 주변 장소 검색 (keyword.json).
     * 카테고리 코드가 없는 개념(예: 전통시장/상권)에 사용.
     * 키가 없거나 실패하면 빈 목록.
     */
    public List<Place> searchByKeyword(String query, double lat, double lng, int radius) {
        if (!isEnabled()) return List.of();
        try {
            PlaceSearchResponse res = restClient.get()
                    .uri("https://dapi.kakao.com/v2/local/search/keyword.json"
                            + "?query={q}&x={x}&y={y}&radius={r}&size=15&sort=distance",
                            query, lng, lat, radius)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(PlaceSearchResponse.class);
            return toPlaces(res);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 지역명(예: "서울 강남구")을 대표 좌표로 지오코딩. 실패하면 null.
     * 1) 주소검색(address.json) → 2) 키워드검색(keyword.json) 폴백.
     * @return [lat, lng] 또는 null
     */
    public double[] geocodeRegion(String query) {
        if (!isEnabled() || query == null || query.isBlank()) return null;
        double[] c = tryGeocode("https://dapi.kakao.com/v2/local/search/address.json?query={q}&size=1", query);
        if (c != null) return c;
        return tryGeocode("https://dapi.kakao.com/v2/local/search/keyword.json?query={q}&size=1", query);
    }

    private double[] tryGeocode(String uri, String query) {
        try {
            GeoSearchResponse res = restClient.get()
                    .uri(uri, query)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(GeoSearchResponse.class);
            if (res != null && res.documents() != null && !res.documents().isEmpty()) {
                GeoDoc d = res.documents().get(0);
                Double x = parseDouble(d.x()), y = parseDouble(d.y());
                if (x != null && y != null) return new double[]{y, x}; // [lat, lng]
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private record GeoSearchResponse(List<GeoDoc> documents) {}

    private record GeoDoc(@JsonProperty("x") String x, @JsonProperty("y") String y) {}

    private static List<Place> toPlaces(PlaceSearchResponse res) {
        if (res == null || res.documents() == null) return List.of();
        return res.documents().stream().map(d -> new Place(
                d.id(),
                d.placeName(),
                d.categoryName(),
                d.categoryGroupCode(),
                d.phone(),
                d.roadAddressName() != null && !d.roadAddressName().isBlank()
                        ? d.roadAddressName() : d.addressName(),
                parseDouble(d.y()),
                parseDouble(d.x()),
                d.placeUrl(),
                parseInt(d.distance())
        )).toList();
    }

    private static Double parseDouble(String s) {
        try { return s == null ? null : Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    private static Integer parseInt(String s) {
        try { return (s == null || s.isBlank()) ? null : Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    /** 프론트에 돌려줄 장소 정보 (Kakao place 문서 정규화). */
    public record Place(
            String id,
            String name,
            String category,
            String categoryGroupCode,
            String phone,
            String address,
            Double latitude,
            Double longitude,
            String placeUrl,
            Integer distanceMeters
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PlaceSearchResponse(List<PlaceDoc> documents) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PlaceDoc(
            @JsonProperty("id") String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("category_group_code") String categoryGroupCode,
            @JsonProperty("phone") String phone,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            @JsonProperty("x") String x,
            @JsonProperty("y") String y,
            @JsonProperty("place_url") String placeUrl,
            @JsonProperty("distance") String distance
    ) {}

    /** 좌표를 행정구역명으로 변환. 키가 없거나 실패하면 null. */
    public KakaoRegion reverseGeocode(double lat, double lng) {
        if (!isEnabled()) return null;
        try {
            RegionResponse res = restClient.get()
                    .uri("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x={x}&y={y}", lng, lat)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(RegionResponse.class);
            if (res == null || res.documents() == null || res.documents().isEmpty()) return null;
            // region_type "B"(법정동) 우선, 없으면 첫 문서
            Document doc = res.documents().stream()
                    .filter(d -> "B".equals(d.regionType()))
                    .findFirst()
                    .orElse(res.documents().get(0));
            return new KakaoRegion(doc.region1depthName(), doc.region2depthName(), doc.region3depthName());
        } catch (Exception e) {
            return null; // 네트워크/키 오류 시 조용히 비활성 처리
        }
    }

    /** 프론트에 돌려줄 간단한 행정구역 정보. */
    public record KakaoRegion(String region1depth, String region2depth, String region3depth) {
        public String label() {
            StringBuilder sb = new StringBuilder();
            if (region1depth != null) sb.append(region1depth);
            if (region2depth != null && !region2depth.isBlank()) sb.append(" ").append(region2depth);
            return sb.toString().trim();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RegionResponse(List<Document> documents) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Document(
            @JsonProperty("region_type") String regionType,
            @JsonProperty("region_1depth_name") String region1depthName,
            @JsonProperty("region_2depth_name") String region2depthName,
            @JsonProperty("region_3depth_name") String region3depthName
    ) {}
}
