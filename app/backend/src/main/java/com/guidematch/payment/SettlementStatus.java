package com.guidematch.payment;

/** 정산 상태. PENDING(지급 대기) → PAID_OUT(관리자 이체 완료). */
public enum SettlementStatus {
    PENDING, PAID_OUT
}
