package com.guidematch.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 같은 대상을 이미 신고했는지 — 중복 신고 방지(멱등). */
    boolean existsByReporterUserIdAndTargetTypeAndTargetId(
            Long reporterUserId, String targetType, Long targetId);

    /** 상태별 목록 (어드민 검토 대기열: OPEN). 최신순. */
    List<Report> findByStatusOrderByCreatedAtDesc(String status);

    /** 어드민 대시보드: 상태별 신고 수 (count 쿼리, N+1 없음) */
    long countByStatus(String status);
}
