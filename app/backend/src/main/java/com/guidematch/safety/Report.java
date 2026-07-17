package com.guidematch.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 신고 한 건. 관리자 검토 대기열(현재는 저장만; 운영 도구는 추후 과제).
 * targetType/targetId로 신고 대상을 가리킨다 — 사용자/대화/게시글/리뷰 등 여러 종류를 한 테이블로 받는다.
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 신고한 사용자 */
    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    /** 신고 대상 종류: USER / CONVERSATION / POST / REVIEW (문자열로 저장, 검증은 서비스에서) */
    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    /** 신고 대상 id (targetType에 해당하는 엔티티의 id) */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 신고 사유 코드: SPAM / HARASSMENT / SCAM / INAPPROPRIATE / OTHER */
    @Column(name = "reason", nullable = false, length = 32)
    private String reason;

    /** 자유 서술 (선택) */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    /** 처리 상태: OPEN(접수) / REVIEWED / DISMISSED — 지금은 OPEN 고정 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = "OPEN";
    }

    protected Report() {
    }

    public Report(Long reporterUserId, String targetType, Long targetId, String reason, String detail) {
        this.reporterUserId = reporterUserId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.status = "OPEN";
    }

    /** 관리자 검토 완료 처리 (조치함). */
    public void markReviewed() { this.status = "REVIEWED"; }

    /** 관리자 기각 (문제 없음). */
    public void dismiss() { this.status = "DISMISSED"; }

    public Long getId() { return id; }
    public Long getReporterUserId() { return reporterUserId; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
