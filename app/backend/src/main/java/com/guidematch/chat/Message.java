package com.guidematch.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 채팅 메시지 한 건. 예약(booking) 단위로 대화가 묶인다.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어느 예약의 대화인지 */
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** 보낸 사람(User) id */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Message() {
    }

    public Message(Long bookingId, Long senderId, String content) {
        this.bookingId = bookingId;
        this.senderId = senderId;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
