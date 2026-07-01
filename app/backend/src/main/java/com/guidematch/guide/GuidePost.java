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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected GuidePost() {}

    public GuidePost(Long guideProfileId, String content, String imageUrl) {
        this.guideProfileId = guideProfileId;
        this.content = content;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public Long getGuideProfileId() { return guideProfileId; }
    public String getContent() { return content; }
    public String getImageUrl() { return imageUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
