package com.guidematch.payment;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 정산 한 건 (플랫폼→가이드). 예약당 1건, COMPLETED 시 생성.
 * 수수료율은 생성 시점 스냅샷(hourlyRateSnapshot 철학) — 이후 요율 변경에 영향받지 않는다.
 */
@Entity
@Table(name = "settlements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlements_booking", columnNames = "booking_id")
})
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    /** 총액 = booking.totalPrice */
    @Column(name = "gross_amount", nullable = false)
    private Integer grossAmount;

    /** 정산 시점 수수료율 스냅샷 (예: 0.15) */
    @Column(name = "commission_rate", nullable = false)
    private double commissionRate;

    /** 수수료 = round(gross × rate) */
    @Column(name = "commission_amount", nullable = false)
    private Integer commissionAmount;

    /** 가이드 수령액 = gross − commission */
    @Column(name = "net_amount", nullable = false)
    private Integer netAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    @Column(name = "admin_memo")
    private String adminMemo;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = SettlementStatus.PENDING;
    }

    protected Settlement() {}

    public Settlement(Long bookingId, Long guideProfileId, Integer grossAmount, double commissionRate) {
        this.bookingId = bookingId;
        this.guideProfileId = guideProfileId;
        this.grossAmount = grossAmount;
        this.commissionRate = commissionRate;
        this.commissionAmount = (int) Math.round(grossAmount * commissionRate);
        this.netAmount = grossAmount - this.commissionAmount;
        this.status = SettlementStatus.PENDING;
    }

    public void markPaidOut(String adminMemo) {
        this.status = SettlementStatus.PAID_OUT;
        this.paidOutAt = Instant.now();
        this.adminMemo = adminMemo;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Long getGuideProfileId() { return guideProfileId; }
    public Integer getGrossAmount() { return grossAmount; }
    public double getCommissionRate() { return commissionRate; }
    public Integer getCommissionAmount() { return commissionAmount; }
    public Integer getNetAmount() { return netAmount; }
    public SettlementStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidOutAt() { return paidOutAt; }
    public String getAdminMemo() { return adminMemo; }
}
