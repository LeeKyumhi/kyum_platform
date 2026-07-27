package com.guidematch.payment;

/** 결제 상태. PENDING(결제창 준비) → PAID(검증완료) / FAILED / REFUNDED(전액취소). */
public enum PaymentStatus {
    PENDING, PAID, FAILED, REFUNDED
}
