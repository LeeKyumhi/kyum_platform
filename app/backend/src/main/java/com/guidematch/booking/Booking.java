package com.guidematch.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 예약(매칭) 한 건.
 * 여행자가 특정 가이드에게 요청하면 생성되고, 가이드의 수락으로 계약이 성립한다.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 요청한 여행자(User)의 id */
    @Column(name = "traveler_id", nullable = false)
    private Long travelerId;

    /** 대상 가이드 프로필의 id */
    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    /** 희망 시작 시각 */
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    /** 이용 시간(시간 단위) */
    @Column(nullable = false)
    private Integer hours;

    /**
     * 계약 시점의 시간당 요금 스냅샷.
     * 가이드가 나중에 요금을 바꿔도 이 예약의 금액은 변하지 않도록 복사해 둔다.
     */
    @Column(name = "hourly_rate_snapshot", nullable = false)
    private Integer hourlyRateSnapshot;

    @Column(nullable = false)
    private String currency;

    /** 총 금액 = 시급 스냅샷 × 시간 */
    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    /** 여행자가 남기는 요청 메시지 (선택) */
    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = BookingStatus.REQUESTED;
    }

    protected Booking() {
    }

    public Booking(Long travelerId, Long guideProfileId, Instant startAt, Integer hours,
                   Integer hourlyRateSnapshot, String currency, Integer totalPrice, String message) {
        this.travelerId = travelerId;
        this.guideProfileId = guideProfileId;
        this.startAt = startAt;
        this.hours = hours;
        this.hourlyRateSnapshot = hourlyRateSnapshot;
        this.currency = currency;
        this.totalPrice = totalPrice;
        this.message = message;
        this.status = BookingStatus.REQUESTED;
    }

    // --- 상태 변경 (규칙 포함) ---

    /** 가이드 수락: 요청 상태일 때만 가능 */
    public void accept() {
        requireStatus(BookingStatus.REQUESTED);
        this.status = BookingStatus.ACCEPTED;
    }

    /** 가이드 거절: 요청 상태일 때만 가능 */
    public void reject() {
        requireStatus(BookingStatus.REQUESTED);
        this.status = BookingStatus.REJECTED;
    }

    /** 여행자 취소: 요청 또는 수락 상태일 때 가능 */
    public void cancel() {
        if (status != BookingStatus.REQUESTED && status != BookingStatus.ACCEPTED) {
            throw new IllegalArgumentException("취소할 수 없는 상태입니다.");
        }
        this.status = BookingStatus.CANCELLED;
    }

    /** 일정 종료 처리: 수락된 예약만 완료로 바꿀 수 있다 (이후 리뷰 가능) */
    public void complete() {
        requireStatus(BookingStatus.ACCEPTED);
        this.status = BookingStatus.COMPLETED;
    }

    private void requireStatus(BookingStatus expected) {
        if (this.status != expected) {
            throw new IllegalArgumentException("처리할 수 없는 예약 상태입니다.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTravelerId() {
        return travelerId;
    }

    public Long getGuideProfileId() {
        return guideProfileId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Integer getHours() {
        return hours;
    }

    public Integer getHourlyRateSnapshot() {
        return hourlyRateSnapshot;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getTotalPrice() {
        return totalPrice;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
