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

/**
 * 가이드의 증빙 자료 한 건 (학력/자격증/면허 + 업로드한 파일).
 * 실제 파일은 Supabase Storage에 저장하고, 여기엔 그 파일의 URL만 보관한다.
 */
@Entity
@Table(name = "guide_credentials")
public class GuideCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어느 가이드 프로필의 자료인지 */
    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialType type;

    /** 자료 이름 (예: "서울대 졸업증명서") */
    @Column(nullable = false)
    private String title;

    /** Supabase Storage에 업로드된 파일의 URL */
    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected GuideCredential() {
    }

    public GuideCredential(Long guideProfileId, CredentialType type, String title, String fileUrl) {
        this.guideProfileId = guideProfileId;
        this.type = type;
        this.title = title;
        this.fileUrl = fileUrl;
    }

    public Long getId() {
        return id;
    }

    public Long getGuideProfileId() {
        return guideProfileId;
    }

    public CredentialType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
