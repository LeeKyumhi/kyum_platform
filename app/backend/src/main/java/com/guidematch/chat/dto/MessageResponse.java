package com.guidematch.chat.dto;

import com.guidematch.chat.Message;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long bookingId,
        Long senderId,
        String senderName,
        String content,
        Instant createdAt
) {
    public static MessageResponse of(Message m, String senderName) {
        return new MessageResponse(
                m.getId(),
                m.getBookingId(),
                m.getSenderId(),
                senderName,
                m.getContent(),
                m.getCreatedAt()
        );
    }
}
