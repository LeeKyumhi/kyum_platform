package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuideVerificationRepository extends JpaRepository<GuideVerification, Long> {

    /** 가이드 프로필별 인증 신청 (프로필당 1행). */
    Optional<GuideVerification> findByGuideProfileId(Long guideProfileId);

    /** 상태별 목록 (어드민 대기열: PENDING). 오래된 신청부터. */
    List<GuideVerification> findByStatusOrderByCreatedAtAsc(VerificationStatus status);

    /** 어드민 대시보드: 상태별 신청 수 (count 쿼리, N+1 없음) */
    long countByStatus(VerificationStatus status);
}
