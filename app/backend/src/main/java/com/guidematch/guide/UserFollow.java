package com.guidematch.guide;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_follows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_user_id", "followed_user_id"}))
public class UserFollow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_user_id", nullable = false)
    private Long followerUserId;

    @Column(name = "followed_user_id", nullable = false)
    private Long followedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void onCreate() { this.createdAt = Instant.now(); }

    protected UserFollow() {}
    public UserFollow(Long followerUserId, Long followedUserId) {
        this.followerUserId = followerUserId;
        this.followedUserId = followedUserId;
    }
    public Long getId() { return id; }
    public Long getFollowerUserId() { return followerUserId; }
    public Long getFollowedUserId() { return followedUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
