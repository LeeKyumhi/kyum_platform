package com.guidematch.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 가이드에 대한 리뷰. 완료된 예약 하나당 하나만 작성할 수 있다.
 */
@Entity
@Table(name = "reviews")
public class Review {

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Review() {
    }

    public Review(Long bookingId, Long guideProfileId, Long reviewerId, Integer rating, String comment) {
        this.bookingId = bookingId;
        this.guideProfileId = guideProfileId;
        this.reviewerId = reviewerId;
        this.rating = rating;
        this.comment = comment;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
