package com.guidematch.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 한 사용자가 다른 사용자를 차단한 사실 한 건.
 * (blocker → blocked) 방향으로 저장하지만, 숨김/차단 효과는 애플리케이션에서 양방향으로 적용한다.
 * 같은 방향 중복 차단은 unique 제약으로 막고, 서비스에서 멱등 처리한다(중복 요청은 no-op).
 */
@Entity
@Table(name = "blocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_user_id", "blocked_user_id"}))
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 차단을 건 사용자 */
    @Column(name = "blocker_user_id", nullable = false)
    private Long blockerUserId;

    /** 차단당한 사용자 */
    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Block() {
    }

    public Block(Long blockerUserId, Long blockedUserId) {
        this.blockerUserId = blockerUserId;
        this.blockedUserId = blockedUserId;
    }

    public Long getId() { return id; }
    public Long getBlockerUserId() { return blockerUserId; }
    public Long getBlockedUserId() { return blockedUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
