package com.guidematch.guide;

/**
 * 관광통역안내사 자격 인증 상태.
 * NONE(미신청) → PENDING(심사중) → VERIFIED(인증됨) / REJECTED(반려).
 * 반려된 가이드는 다시 신청할 수 있다(PENDING으로 되돌아감).
 */
public enum VerificationStatus {
    NONE,
    PENDING,
    VERIFIED,
    REJECTED
}
