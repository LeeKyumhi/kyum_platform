package com.guidematch.knowledge;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 표준 장소 노드 — 모든 지식이 여기에 붙는다.
 *
 * <p>Kakao·TourAPI·블로그가 각자 다른 식별자(혹은 이름뿐)로 같은 장소를 가리키므로,
 * 이 엔티티가 그것들을 하나로 모으는 유일한 지점이다. 여기서 어긋나면 인사이트가
 * 중복 노드로 흩어지고 <b>그 실패는 조용하다</b>. 병합 판정은 {@link PlaceResolver}만 한다.
 */
@Entity
@Table(
    name = "places",
    indexes = {
        @Index(name = "idx_places_name_norm", columnList = "name_normalized"),
        @Index(name = "idx_places_city_district", columnList = "city,district")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_places_kakao", columnNames = "kakao_place_id"),
        @UniqueConstraint(name = "uk_places_tour_api", columnNames = "tour_api_content_id")
    }
)
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_ko", nullable = false, columnDefinition = "TEXT")
    private String nameKo;

    /** {@link PlaceNames#normalize} 결과. 외부 입력이 아니라 항상 서버가 계산한다. */
    @Column(name = "name_normalized", nullable = false, length = 200)
    private String nameNormalized;

    @Column(name = "city", length = 60)
    private String city;

    @Column(name = "district", length = 60)
    private String district;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    /** Postgres는 unique 컬럼에 NULL 다중 허용 → 외부 ID 없는 장소도 공존 가능. */
    @Column(name = "kakao_place_id", length = 40)
    private String kakaoPlaceId;

    @Column(name = "tour_api_content_id", length = 40)
    private String tourApiContentId;

    @Column(name = "category", columnDefinition = "TEXT")
    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Place() {}

    public Place(String nameKo, String city, String district,
                 Double lat, Double lng,
                 String kakaoPlaceId, String tourApiContentId, String category) {
        this.nameKo = nameKo;
        this.nameNormalized = PlaceNames.normalize(nameKo);
        this.city = city;
        this.district = district;
        this.lat = lat;
        this.lng = lng;
        this.kakaoPlaceId = kakaoPlaceId;
        this.tourApiContentId = tourApiContentId;
        this.category = category;
    }

    /**
     * 이미 아는 장소에 새 단서가 들어왔을 때 <b>빈 칸만</b> 채운다.
     * 기존 값을 덮어쓰지 않는 이유: 먼저 들어온 값이 대체로 더 권위 있는 소스(Kakao)이고,
     * 나중에 들어오는 블로그 추출값이 좌표를 흔들면 해결 사다리 자체가 불안정해진다.
     */
    public void enrichMissing(Double lat, Double lng, String kakaoPlaceId,
                              String tourApiContentId, String category,
                              String city, String district) {
        boolean changed = false;
        if (this.lat == null && lat != null)                     { this.lat = lat; changed = true; }
        if (this.lng == null && lng != null)                     { this.lng = lng; changed = true; }
        if (isBlank(this.kakaoPlaceId) && notBlank(kakaoPlaceId)) { this.kakaoPlaceId = kakaoPlaceId; changed = true; }
        if (isBlank(this.tourApiContentId) && notBlank(tourApiContentId)) { this.tourApiContentId = tourApiContentId; changed = true; }
        if (isBlank(this.category) && notBlank(category))        { this.category = category; changed = true; }
        if (isBlank(this.city) && notBlank(city))                { this.city = city; changed = true; }
        if (isBlank(this.district) && notBlank(district))        { this.district = district; changed = true; }
        if (changed) this.updatedAt = Instant.now();
    }

    private static boolean isBlank(String s)  { return s == null || s.isBlank(); }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    public Long getId()                 { return id; }
    public String getNameKo()           { return nameKo; }
    public String getNameNormalized()   { return nameNormalized; }
    public String getCity()             { return city; }
    public String getDistrict()         { return district; }
    public Double getLat()              { return lat; }
    public Double getLng()              { return lng; }
    public String getKakaoPlaceId()     { return kakaoPlaceId; }
    public String getTourApiContentId() { return tourApiContentId; }
    public String getCategory()         { return category; }
}
