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

    /**
     * 정차지 슬롯 매칭 + 여행자 무관 장소 배제. 적재·백필 모두 {@link PlaceKinds}가 정한다.
     *
     * <p><b>⚠ nullable이어야 한다.</b> {@code ddl-auto: update}로 NOT NULL 컬럼을 추가하면
     * Hibernate가 DEFAULT 없이 {@code add column ... not null}을 내고 Postgres가 거부하는데,
     * <b>컬럼이 안 생긴 채 앱은 정상 기동한다.</b> 기존 행은 {@link PlaceKindBackfill}이 채운다.
     *
     * <p><b>⚠ Hibernate가 CHECK 제약을 자동 생성한다 — 실측 확인.</b> {@code columnDefinition}을
     * 줘도 막지 못한다. 실제로 생성된 제약:
     * {@code places_place_kind_check CHECK (place_kind = ANY (ARRAY['ATTRACTION',…,'OTHER']))}.
     * {@code ddl-auto: update}는 이 제약을 <b>나중에 고쳐주지 않으므로</b>, {@link PlaceKind}에
     * 값을 추가하면 적재가 제약 위반으로 죽는다(권한 오류처럼 보인다). 값을 늘릴 때는
     * Postgres에서 제약을 직접 {@code drop} 후 재생성해야 한다. 그래서 지금 안 쓰는
     * {@code NATURE}까지 처음부터 열거해 두었다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "place_kind", length = 20, columnDefinition = "varchar(20)")
    private PlaceKind placeKind;

    /** 한국어 주소. 번역하지 않는다 (CLAUDE.md 규약 — 택시·지도 앱 편의). */
    @Column(name = "address_ko", columnDefinition = "TEXT")
    private String addressKo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Place() {}

    public Place(String nameKo, String city, String district,
                 Double lat, Double lng,
                 String kakaoPlaceId, String tourApiContentId, String category,
                 String addressKo) {
        this.nameKo = nameKo;
        this.nameNormalized = PlaceNames.normalize(nameKo);
        this.city = city;
        this.district = district;
        this.lat = lat;
        this.lng = lng;
        this.kakaoPlaceId = kakaoPlaceId;
        this.tourApiContentId = tourApiContentId;
        this.category = category;
        this.addressKo = addressKo;
        // 장소를 만드는 순간 종류가 정해진다 — 나중에 채우면 그 사이의 후보 조회가 전부 놓친다
        this.placeKind = PlaceKinds.classify(category, nameKo);
    }

    /**
     * 이미 아는 장소에 새 단서가 들어왔을 때 <b>빈 칸만</b> 채운다.
     * 기존 값을 덮어쓰지 않는 이유: 먼저 들어온 값이 대체로 더 권위 있는 소스(Kakao)이고,
     * 나중에 들어오는 블로그 추출값이 좌표를 흔들면 해결 사다리 자체가 불안정해진다.
     */
    /**
     * @return 실제로 바뀐 값이 있으면 true.
     *         <b>호출부는 이 값이 false면 저장을 건너뛴다</b> — detached 엔티티에 {@code save()}를
     *         부르면 {@code merge}가 행마다 SELECT를 한 번씩 내고, Sydney 왕복 250ms에서는
     *         그것만으로 40건에 10초다. 재적재(멱등 확인)는 대부분 아무것도 안 바뀐다.
     */
    public boolean enrichMissing(Double lat, Double lng, String kakaoPlaceId,
                                 String tourApiContentId, String category,
                                 String city, String district, String addressKo) {
        boolean changed = false;
        if (this.lat == null && lat != null)                     { this.lat = lat; changed = true; }
        if (this.lng == null && lng != null)                     { this.lng = lng; changed = true; }
        if (isBlank(this.kakaoPlaceId) && notBlank(kakaoPlaceId)) { this.kakaoPlaceId = kakaoPlaceId; changed = true; }
        if (isBlank(this.tourApiContentId) && notBlank(tourApiContentId)) { this.tourApiContentId = tourApiContentId; changed = true; }
        if (isBlank(this.category) && notBlank(category))        { this.category = category; changed = true; }
        if (isBlank(this.city) && notBlank(city))                { this.city = city; changed = true; }
        if (isBlank(this.district) && notBlank(district))        { this.district = district; changed = true; }
        if (isBlank(this.addressKo) && notBlank(addressKo))      { this.addressKo = addressKo; changed = true; }
        // 종류가 비어 있고 이제 카테고리를 알게 됐다면 그때 정한다 (백필과 같은 규칙)
        if (this.placeKind == null && notBlank(this.category)) {
            this.placeKind = PlaceKinds.classify(this.category, this.nameKo);
            changed = true;
        }
        if (changed) this.updatedAt = Instant.now();
        return changed;
    }

    /**
     * 종류가 비어 있을 때만 채운다. 사람이 손으로 고친 값을 백필이 되돌리면 안 된다.
     *
     * @return 실제로 값이 바뀌었으면 true
     */
    public boolean assignKindIfMissing() {
        if (this.placeKind != null) return false;
        this.placeKind = PlaceKinds.classify(this.category, this.nameKo);
        this.updatedAt = Instant.now();
        return true;
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
    public PlaceKind getPlaceKind()     { return placeKind; }
    public String getAddressKo()        { return addressKo; }
}
