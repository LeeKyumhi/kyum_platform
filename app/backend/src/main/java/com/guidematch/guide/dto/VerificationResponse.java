package com.guidematch.guide.dto;

import com.guidematch.guide.GuideVerification;
import com.guidematch.guide.VerificationStatus;

/**
 * 가이드 본인이 보는 인증 신청 상태.
 * 신청 전이면 status=NONE에 나머지는 null로 채워 반환한다(프론트 분기 단순화).
 */
public record VerificationResponse(
        VerificationStatus status,
        String legalName,
        String licenseNumber,
        String licenseIssueDate,
        String licenseInnerNumber,
        String rejectReason
) {
    public static VerificationResponse none() {
        return new VerificationResponse(VerificationStatus.NONE, null, null, null, null, null);
    }

    public static VerificationResponse from(GuideVerification v) {
        return new VerificationResponse(
                v.getStatus(),
                v.getLegalName(),
                v.getLicenseNumber(),
                v.getLicenseIssueDate() != null ? v.getLicenseIssueDate().toString() : null,
                v.getLicenseInnerNumber(),
                v.getRejectReason()
        );
    }
}
