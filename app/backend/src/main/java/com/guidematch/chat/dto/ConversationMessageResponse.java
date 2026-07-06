package com.guidematch.chat.dto;

import com.guidematch.chat.ConversationMessage;

import java.time.Instant;

public record ConversationMessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String content,
        Instant createdAt
) {
    public static ConversationMessageResponse of(ConversationMessage m, String senderName) {
        return new ConversationMessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                senderName,
                m.getContent(),
                m.getCreatedAt()
        );
    }
}
