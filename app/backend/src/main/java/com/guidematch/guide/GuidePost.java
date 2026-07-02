package com.guidematch.guide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "guide_posts")
public class GuidePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "image_url")
    private String imageUrl;

    // 게시글 카테고리 (여행 테마 키). 기존 게시글은 null 가능. (현재 UI 미사용, 컬럼 유지)
    @Column(name = "category")
    private String category;

    // 조회수 (피드 노출 임프레션 기준). 기존 행에도 안전하게 추가되도록 DB default 지정.
    @Column(name = "view_count", nullable = false, columnDefinition = "bigint default 0")
    private long viewCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected GuidePost() {}

    public GuidePost(Long guideProfileId, String content, String imageUrl, String category) {
        this.guideProfileId = guideProfileId;
        this.content = content;
        this.imageUrl = imageUrl;
        this.category = category;
    }

    public Long getId() { return id; }
    public Long getGuideProfileId() { return guideProfileId; }
    public String getContent() { return content; }
    public String getImageUrl() { return imageUrl; }
    public String getCategory() { return category; }
    public long getViewCount() { return viewCount; }
    public Instant getCreatedAt() { return createdAt; }
}
