package com.guidematch.payment;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 결제 한 건 (에스크로: 여행자→플랫폼). 예약당 1건.
 * BookingStatus와 직교 — "결제됨"은 이 행이 PAID인 것으로 판단한다.
 */
@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_booking", columnNames = "booking_id"),
        @UniqueConstraint(name = "uk_payments_portone_uid", columnNames = "portone_uid"),
        @UniqueConstraint(name = "uk_payments_merchant_uid", columnNames = "merchant_uid")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** 우리가 만든 주문번호. 결제창에 넘기고, 콜백/웹훅에서 매칭 키로 쓴다. */
    @Column(name = "merchant_uid", nullable = false)
    private String merchantUid;

    /** PortOne 결제 id. 결제 확정 시 채워진다. 멱등 키(unique). */
    @Column(name = "portone_uid")
    private String portoneUid;

    /** 결제 금액(KRW). booking.totalPrice 스냅샷. */
    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = PaymentStatus.PENDING;
    }

    protected Payment() {}

    public Payment(Long bookingId, String merchantUid, Integer amount, String currency) {
        this.bookingId = bookingId;
        this.merchantUid = merchantUid;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
    }

    /** 서버 검증 통과 후 PAID로 확정. 멱등: 이미 PAID면 아무것도 하지 않는다. */
    public void markPaid(String portoneUid) {
        if (this.status == PaymentStatus.PAID) return;
        this.portoneUid = portoneUid;
        this.status = PaymentStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = Instant.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getMerchantUid() { return merchantUid; }
    public String getPortoneUid() { return portoneUid; }
    public Integer getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
