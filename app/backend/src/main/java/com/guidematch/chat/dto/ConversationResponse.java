package com.guidematch.chat.dto;

import java.time.Instant;

/** 대화방 요약 — 인박스 목록/입장 시 사용. other*는 항상 "상대방" 기준. */
public record ConversationResponse(
        Long id,
        Long guideProfileId,
        String otherName,
        String otherAvatarUrl,
        boolean otherIsGuide,
        String lastMessagePreview,
        Instant lastMessageAt
) {
}
