package com.guidematch.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Set;

/**
 * 가이드에 대한 리뷰. 완료된 예약 하나당 하나만 작성할 수 있다.
 */
@Entity
@Table(name = "reviews")
public class Review {

    /**
     * 리뷰 작성 시 고를 수 있는 키워드 태그의 canonical key 집합.
     * 프론트는 key → 현지화 라벨(i18n)로 매핑해 렌더링한다. 백엔드는 key만 저장/검증한다.
     * kind=친절해요, punctual=시간약속 잘 지켜요, knowledgeable=아는게 많아요, flexible=유연해요,
     * goodPhotos=사진 잘 찍어줘요, goodFood=맛집 잘 알아요, languageGood=외국어 능통, funny=유쾌해요
     */
    public static final Set<String> CANONICAL_TAG_KEYS = Set.of(
            "kind", "punctual", "knowledgeable", "flexible",
            "goodPhotos", "goodFood", "languageGood", "funny"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어떤 예약에 대한 리뷰인지 (예약당 1개) */
    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    /** 대상 가이드 프로필 */
    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    /** 작성자(여행자 User) id */
    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    /** 별점 1~5 */
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "text")
    private String comment;

    /** 콤마로 구분된 canonical 태그 key 목록 (예: "kind,punctual"). 기존 리뷰는 null. */
    @Column(name = "tags", columnDefinition = "text")
    private String tags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Review() {
    }

    public Review(Long bookingId, Long guideProfileId, Long reviewerId, Integer rating, String comment, String tags) {
        this.bookingId = bookingId;
        this.guideProfileId = guideProfileId;
        this.reviewerId = reviewerId;
        this.rating = rating;
        this.comment = comment;
        this.tags = tags;
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getGuideProfileId() {
        return guideProfileId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getTags() {
        return tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
