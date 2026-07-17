package com.guidematch.guide;

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
import java.time.LocalDate;

/**
 * 관광통역안내사 자격 인증 신청 1건 (가이드 프로필당 최대 1행).
 *
 * 공식 제3자 검증 API가 없으므로(Q-net·관광인 포털은 본인 로그인/수동 조회뿐),
 * 관리자가 Q-net 진위확인으로 대조해 승인/반려하는 사람 개입(admin approval) 방식이다.
 *
 * 신원 결박(계정·자격 대여 방지): 자격증 이름(legalName)과 신분증(idDocFileUrl)을
 * 함께 받아 관리자가 "자격증 명의 == 실제 본인"을 대조한다.
 */
@Entity
@Table(name = "guide_verifications")
public class GuideVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어느 가이드 프로필의 인증인지 (가이드당 1행). */
    @Column(name = "guide_profile_id", nullable = false, unique = true)
    private Long guideProfileId;

    /** 자격증·신분증에 적힌 실명. 관리자가 명의 일치를 확인하는 기준. */
    @Column(name = "legal_name", nullable = false)
    private String legalName;

    /** 관광통역안내사 자격증 번호. */
    @Column(name = "license_number", nullable = false)
    private String licenseNumber;

    /** 자격증 발급일자 (Q-net 진위확인에 필요). */
    @Column(name = "license_issue_date")
    private LocalDate licenseIssueDate;

    /** 자격증 내지번호 (Q-net 진위확인에 필요). */
    @Column(name = "license_inner_number")
    private String licenseInnerNumber;

    /** 자격증 사본 — 비공개 버킷 내 경로. 열람은 어드민이 서명 URL로만(공개 URL 아님). */
    @Column(name = "license_file_url", nullable = false)
    private String licenseFileUrl;

    /** 신분증/여권 사본 — 비공개 버킷 내 경로. 명의 대조용(신원 결박), 어드민 서명 URL로만 열람. */
    @Column(name = "id_doc_file_url", nullable = false)
    private String idDocFileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    /** 반려 사유 (status == REJECTED일 때). */
    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;

    /** 심사한 관리자 user id. */
    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    /** 심사(승인/반려) 시각. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = VerificationStatus.PENDING;
    }

    protected GuideVerification() {
    }

    public GuideVerification(Long guideProfileId, String legalName, String licenseNumber,
                             LocalDate licenseIssueDate, String licenseInnerNumber,
                             String licenseFileUrl, String idDocFileUrl) {
        this.guideProfileId = guideProfileId;
        this.legalName = legalName;
        this.licenseNumber = licenseNumber;
        this.licenseIssueDate = licenseIssueDate;
        this.licenseInnerNumber = licenseInnerNumber;
        this.licenseFileUrl = licenseFileUrl;
        this.idDocFileUrl = idDocFileUrl;
        this.status = VerificationStatus.PENDING;
    }

    /**
     * 재신청 — 반려된(또는 기존) 신청을 새 서류로 갱신하고 다시 심사 대기(PENDING)로 되돌린다.
     * 이전 심사 결과(반려 사유·심사자)는 초기화한다.
     */
    public void resubmit(String legalName, String licenseNumber, LocalDate licenseIssueDate,
                         String licenseInnerNumber, String licenseFileUrl, String idDocFileUrl) {
        this.legalName = legalName;
        this.licenseNumber = licenseNumber;
        this.licenseIssueDate = licenseIssueDate;
        this.licenseInnerNumber = licenseInnerNumber;
        this.licenseFileUrl = licenseFileUrl;
        this.idDocFileUrl = idDocFileUrl;
        this.status = VerificationStatus.PENDING;
        this.rejectReason = null;
        this.reviewedByUserId = null;
        this.reviewedAt = null;
        this.updatedAt = Instant.now();
    }

    /** 관리자 승인. */
    public void approve(Long adminUserId) {
        this.status = VerificationStatus.VERIFIED;
        this.rejectReason = null;
        this.reviewedByUserId = adminUserId;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    /** 관리자 반려 (사유 포함). */
    public void reject(Long adminUserId, String reason) {
        this.status = VerificationStatus.REJECTED;
        this.rejectReason = reason;
        this.reviewedByUserId = adminUserId;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public Long getId() { return id; }
    public Long getGuideProfileId() { return guideProfileId; }
    public String getLegalName() { return legalName; }
    public String getLicenseNumber() { return licenseNumber; }
    public LocalDate getLicenseIssueDate() { return licenseIssueDate; }
    public String getLicenseInnerNumber() { return licenseInnerNumber; }
    public String getLicenseFileUrl() { return licenseFileUrl; }
    public String getIdDocFileUrl() { return idDocFileUrl; }
    public VerificationStatus getStatus() { return status; }
    public String getRejectReason() { return rejectReason; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
