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
     * 키워드로 전국 장소 검색 (keyword.json, 위치 바이어스 없음).
     * 만남 장소 지정(T1)처럼 임의의 장소를 자유 검색할 때 사용 — 정확도순 상위 결과.
     * 키가 없거나 실패하면 빈 목록.
     */
    public List<Place> searchByKeyword(String query) {
        if (!isEnabled() || query == null || query.isBlank()) return List.of();
        try {
            PlaceSearchResponse res = restClient.get()
                    .uri("https://dapi.kakao.com/v2/local/search/keyword.json?query={q}&size=15",
                            query)
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
                parseInt(d.distance()),
                java.util.List.of(),  // 근거는 PlaceController가 채운다
                null, null,           // 여행자 사진도 PlaceController가 채운다
                null, null            // 공식 사진(레지스트리 시드)도 마찬가지
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
            Integer distanceMeters,
            /**
             * 추천 근거 (없으면 빈 목록). Kakao는 이 값을 주지 않는다 —
             * {@link PlaceController}가 우리 레지스트리를 조인해 채운다.
             */
            java.util.List<CourseReasons.Reason> reasons,
            /**
             * 사용자가 올린 대표 사진(400px 썸네일)과 장수. Kakao는 사진을 주지 않는다 —
             * {@link PlaceController}가 우리 노트를 조인해 채운다.
             * <b>사진이 없으면 둘 다 null이다</b>(0이 아니다) — "사진 0장"을 렌더할 여지를 없앤다.
             */
            String coverPhotoUrl,
            Integer photoCount,
            /**
             * 공식 사진(TourAPI)과 그 발행처. <b>쌍으로만</b> 실린다 —
             * 출처를 못 밝히는 사진은 띄우지 않는다(계약 §16).
             */
            String officialPhotoUrl,
            String officialPhotoPublisher
    ) {
        /** 근거만 갈아끼운 사본. record라 값을 고치는 대신 새로 만든다. */
        public Place withReasons(java.util.List<CourseReasons.Reason> newReasons) {
            return new Place(id, name, category, categoryGroupCode, phone, address,
                    latitude, longitude, placeUrl, distanceMeters, newReasons,
                    coverPhotoUrl, photoCount, officialPhotoUrl, officialPhotoPublisher);
        }

        /** 사진(여행자 대표 + 공식)만 갈아끼운 사본. */
        public Place withMedia(String newCoverPhotoUrl, Integer newPhotoCount,
                               String newOfficialUrl, String newOfficialPublisher) {
            return new Place(id, name, category, categoryGroupCode, phone, address,
                    latitude, longitude, placeUrl, distanceMeters, reasons,
                    newCoverPhotoUrl, newPhotoCount, newOfficialUrl, newOfficialPublisher);
        }
    }

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

}
