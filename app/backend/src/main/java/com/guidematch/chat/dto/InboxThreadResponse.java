package com.guidematch.chat.dto;

import java.time.Instant;

/**
 * 통합 인박스의 대화 한 줄. 예약 전 문의(DM)와 예약 채팅을 하나의 목록으로 합쳐 보여준다.
 * {@code type}으로 구분하고, 프론트는 dm이면 /messages/{conversationId},
 * booking이면 /chat/{bookingId}로 링크한다. other*는 항상 "상대방" 기준.
 */
public record InboxThreadResponse(
        String type,               // "dm" | "booking"
        Long conversationId,       // dm일 때만 (booking이면 null)
        Long bookingId,            // booking일 때만 (dm이면 null)
        Long guideProfileId,
        Long otherUserId,
        String otherName,
        String otherAvatarUrl,
        boolean otherIsGuide,
        String lastMessagePreview,
        Instant lastMessageAt,
        String bookingStatus,      // booking일 때만 상태 배지용 (dm이면 null)
        boolean unread             // 내가 안 읽은 메시지가 있는 방인지 (#2 인박스 점 표시)
) {
    public static InboxThreadResponse dm(ConversationResponse c, boolean unread) {
        return new InboxThreadResponse(
                "dm", c.id(), null, c.guideProfileId(), c.otherUserId(),
                c.otherName(), c.otherAvatarUrl(), c.otherIsGuide(),
                c.lastMessagePreview(), c.lastMessageAt(), null, unread);
    }
}
