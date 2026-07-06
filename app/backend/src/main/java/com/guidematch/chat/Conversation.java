package com.guidematch.chat;

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
 * 예약과 무관한 1:1 대화방 (예약 전 문의용).
 * 여행자(어떤 사용자든) ↔ 가이드 프로필 쌍당 하나만 존재한다.
 *
 * 기존 messages 테이블은 booking_id NOT NULL이라 재사용하지 않고
 * 별도 테이블로 둔다 (ddl-auto는 제약 완화를 못 하므로 추가만 한다).
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"traveler_user_id", "guide_profile_id"}))
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대화를 시작한 쪽 (여행자 입장의 사용자) */
    @Column(name = "traveler_user_id", nullable = false)
    private Long travelerUserId;

    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    /** 가이드 쪽 사용자 id — 목록 조회 시 프로필 join 없이 바로 찾기 위한 비정규화 */
    @Column(name = "guide_user_id", nullable = false)
    private Long guideUserId;

    /** 마지막 메시지 시각/미리보기 — 인박스 목록을 쿼리 한 번으로 그리기 위한 비정규화 */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", columnDefinition = "text")
    private String lastMessagePreview;

    /** 참여자별 마지막 읽은 시각 — 안읽음 배지 계산용 (null이면 한 번도 안 읽음) */
    @Column(name = "traveler_last_read_at")
    private Instant travelerLastReadAt;

    @Column(name = "guide_last_read_at")
    private Instant guideLastReadAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Conversation() {
    }

    public Conversation(Long travelerUserId, Long guideProfileId, Long guideUserId) {
        this.travelerUserId = travelerUserId;
        this.guideProfileId = guideProfileId;
        this.guideUserId = guideUserId;
    }

    public Long getId() { return id; }
    public Long getTravelerUserId() { return travelerUserId; }
    public Long getGuideProfileId() { return guideProfileId; }
    public Long getGuideUserId() { return guideUserId; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public Instant getCreatedAt() { return createdAt; }

    public void touchLastMessage(String preview) {
        this.lastMessageAt = Instant.now();
        this.lastMessagePreview = preview;
    }

    public Instant getTravelerLastReadAt() { return travelerLastReadAt; }
    public Instant getGuideLastReadAt() { return guideLastReadAt; }

    /** 이 사용자 입장에서 대화를 방금 열었으니 지금까지를 읽음 처리 */
    public void markRead(Long userId) {
        Instant now = Instant.now();
        if (travelerUserId.equals(userId)) travelerLastReadAt = now;
        if (guideUserId.equals(userId)) guideLastReadAt = now;
    }

    public boolean isParticipant(Long userId) {
        return travelerUserId.equals(userId) || guideUserId.equals(userId);
    }
}
