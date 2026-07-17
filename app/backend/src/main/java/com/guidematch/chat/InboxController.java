package com.guidematch.chat;

import com.guidematch.chat.dto.InboxThreadResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 통합 인박스 API — DM + 예약 채팅을 한 목록으로. 로그인 필요(SecurityConfig 기본 authenticated).
 */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private final InboxService inboxService;

    public InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public List<InboxThreadResponse> myInbox(@AuthenticationPrincipal Long userId) {
        return inboxService.myInbox(userId);
    }

    /** 안읽은 총 개수(DM + 예약 채팅) — 사이드바 배지 (30초 폴링) */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Long userId) {
        return Map.of("count", inboxService.unreadCount(userId));
    }
}
