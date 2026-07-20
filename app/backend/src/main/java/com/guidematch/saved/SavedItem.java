package com.guidematch.saved;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 위시리스트 항목 (다형성).
 * - GUIDE/COURSE: refId = guide_profile_id / tour_course_id. 목록 조회 시 원본을 배치 재조회.
 * - PLACE: placeRef = SPOTS slug 또는 "kakao:{placeId}". 원본 행이 없으므로 저장 시점 스냅샷을 함께 저장.
 * 중복 방지는 전적으로 서비스 레벨 존재검사(Follow의 idempotent 패턴)이며, 물리 유니크 제약은 모든 행이 NULL 포함 튜플이라(Postgres NULLS DISTINCT) 어떤 타입에도 실효가 없다(장식적).
 */
@Entity
@Table(name = "saved_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "item_type", "ref_id", "place_ref"}),
        // 공개 counts 배치(where item_type=? and ref_id in ...)용 — 복합 유니크는 user_id 선두라 이 패턴을 못 탄다
        indexes = @Index(name = "idx_saved_items_type_ref", columnList = "item_type, ref_id"))
public class SavedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private SavedItemType itemType;

    /** GUIDE=guide_profile_id, COURSE=tour_course_id. PLACE는 null. */
    @Column(name = "ref_id")
    private Long refId;

    /** PLACE 전용 참조 키 — SPOTS slug 또는 "kakao:{placeId}". */
    @Column(name = "place_ref")
    private String placeRef;

    // ── 장소 스냅샷 (PLACE일 때만 채움) ──
    @Column(name = "place_name")
    private String placeName;

    @Column(name = "place_category")
    private String placeCategory;

    @Column(name = "place_address")
    private String placeAddress;

    @Column(name = "place_lat")
    private Double placeLat;

    @Column(name = "place_lng")
    private Double placeLng;

    @Column(name = "place_image")
    private String placeImage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    protected SavedItem() {}

    /** GUIDE/COURSE 저장용. */
    public SavedItem(Long userId, SavedItemType itemType, Long refId) {
        this.userId = userId;
        this.itemType = itemType;
        this.refId = refId;
    }

    /** PLACE 저장용 (스냅샷 포함). */
    public SavedItem(Long userId, String placeRef, String placeName, String placeCategory,
                     String placeAddress, Double placeLat, Double placeLng, String placeImage) {
        this.userId = userId;
        this.itemType = SavedItemType.PLACE;
        this.placeRef = placeRef;
        this.placeName = placeName;
        this.placeCategory = placeCategory;
        this.placeAddress = placeAddress;
        this.placeLat = placeLat;
        this.placeLng = placeLng;
        this.placeImage = placeImage;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public SavedItemType getItemType() { return itemType; }
    public Long getRefId() { return refId; }
    public String getPlaceRef() { return placeRef; }
    public String getPlaceName() { return placeName; }
    public String getPlaceCategory() { return placeCategory; }
    public String getPlaceAddress() { return placeAddress; }
    public Double getPlaceLat() { return placeLat; }
    public Double getPlaceLng() { return placeLng; }
    public String getPlaceImage() { return placeImage; }
    public Instant getCreatedAt() { return createdAt; }
}
