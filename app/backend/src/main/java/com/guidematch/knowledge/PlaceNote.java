package com.guidematch.knowledge;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 사용자가 장소에 남긴 사진 한 장 또는 한줄팁 — 축적되는 자산.
 *
 * <p><b>왜 식별자가 두 개인가.</b> 화면의 장소는 두 출신이 섞여 있고 각자 다른 신분증을 쥔다.
 * 레지스트리 유래는 {@code places.id}를, Kakao 실시간 검색 유래는 {@code kakao_place_id}만 안다.
 * 저장 시점에 한쪽으로 해소하면(= {@code SignalRecorder:67}) 레지스트리에 없는 장소에서
 * {@code place_id=null}이 되어 <b>신원을 영구히 잃는다.</b> 그래서 가진 것을 그대로 적고,
 * 합치는 일은 읽는 쪽({@link PlaceMediaLookup})이 한다.
 *
 * <p><b>왜 status가 String인가.</b> Java enum을 JPA 컬럼으로 쓰면 Hibernate가 CHECK 제약을
 * 테이블 생성 시점의 값으로 고정하고 {@code ddl-auto: update}가 나중에 고쳐주지 않는다
 * ({@code places_place_kind_check}로 실측). {@code Report.targetType}과 같이 String으로 둔다.
 */
@Entity
@Table(name = "place_notes")
public class PlaceNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 레지스트리 유래면 채워진다. Kakao 유래는 null이고, 나중에 백필이 채운다. */
    @Column(name = "place_id")
    private Long placeId;

    /** Kakao 유래면 채워진다. 절대 버리지 않는다 — 백필의 매칭 키다. */
    @Column(name = "kakao_place_id", length = 50)
    private String kakaoPlaceId;

    /** 두 키를 다 못 믿게 됐을 때의 최후 표시용. 번역하지 않는다. */
    @Column(name = "place_name_snapshot", nullable = false, length = 200)
    private String placeNameSnapshot;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "photo_url", columnDefinition = "text")
    private String photoUrl;

    /** 목록용 400px. photoUrl과 항상 함께 채워진다. */
    @Column(name = "photo_thumb_url", columnDefinition = "text")
    private String photoThumbUrl;

    @Column(name = "tip", length = 140)
    private String tip;

    /** VISIBLE / HIDDEN — 검증은 이 클래스와 서비스가 한다(DB CHECK에 의존하지 않는다). */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "VISIBLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PlaceNote() {}

    public PlaceNote(Long placeId, String kakaoPlaceId, String placeNameSnapshot, Long userId,
                     String photoUrl, String photoThumbUrl, String tip) {
        if (placeId == null && !notBlank(kakaoPlaceId)) {
            throw new IllegalArgumentException("장소 식별자가 최소 하나는 있어야 한다 — 없으면 되찾을 수 없다");
        }
        if (!notBlank(photoUrl) && !notBlank(tip)) {
            throw new IllegalArgumentException("사진 또는 팁 중 최소 하나는 있어야 한다");
        }
        this.placeId = placeId;
        this.kakaoPlaceId = notBlank(kakaoPlaceId) ? kakaoPlaceId.trim() : null;
        this.placeNameSnapshot = placeNameSnapshot;
        this.userId = userId;
        this.photoUrl = photoUrl;
        this.photoThumbUrl = photoThumbUrl;
        this.tip = notBlank(tip) ? tip.trim() : null;
    }

    public void hide() { this.status = "HIDDEN"; }

    /** 뒤늦게 레지스트리에 수집된 장소를 연결한다. kakao id는 지우지 않는다. */
    public void linkPlaceId(Long resolved) { this.placeId = resolved; }

    public Long getId()                 { return id; }
    public Long getPlaceId()            { return placeId; }
    public String getKakaoPlaceId()     { return kakaoPlaceId; }
    public String getPlaceNameSnapshot(){ return placeNameSnapshot; }
    public Long getUserId()             { return userId; }
    public String getPhotoUrl()         { return photoUrl; }
    public String getPhotoThumbUrl()    { return photoThumbUrl; }
    public String getTip()              { return tip; }
    public String getStatus()           { return status; }
    public Instant getCreatedAt()       { return createdAt; }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
