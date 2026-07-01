package com.guidematch.guide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "follows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_user_id", "guide_profile_id"}))
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_user_id", nullable = false)
    private Long followerUserId;

    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    protected Follow() {}

    public Follow(Long followerUserId, Long guideProfileId) {
        this.followerUserId = followerUserId;
        this.guideProfileId = guideProfileId;
    }

    public Long getId() { return id; }
    public Long getFollowerUserId() { return followerUserId; }
    public Long getGuideProfileId() { return guideProfileId; }
    public Instant getCreatedAt() { return createdAt; }
}
