package com.guidematch.chat;

import com.guidematch.chat.dto.ConversationMessageResponse;
import com.guidematch.chat.dto.ConversationResponse;
import com.guidematch.chat.dto.SendMessageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 예약 전 1:1 문의 대화 API. 전부 로그인 필요 (SecurityConfig 기본 authenticated).
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public record CreateConversationRequest(@NotNull Long guideProfileId) {}

    /** 가이드에게 보내는 대화방 얻기 (없으면 생성) — 가이드 상세의 "메시지 보내기" */
    @PostMapping
    public ConversationResponse getOrCreate(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        return conversationService.getOrCreate(userId, request.guideProfileId());
    }

    /** 내 대화 목록 (인박스) */
    @GetMapping
    public List<ConversationResponse> myConversations(@AuthenticationPrincipal Long userId) {
        return conversationService.myConversations(userId);
    }

    /** 안읽은 DM 총 개수 — 사이드바 배지 (30초 폴링) */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Long userId) {
        return Map.of("count", conversationService.unreadCount(userId));
    }

    /** 방 정보 — 대화 화면 헤더용 */
    @GetMapping("/{conversationId}")
    public ConversationResponse get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        return conversationService.get(userId, conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessageResponse> history(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        return conversationService.history(userId, conversationId);
    }

    /** REST 전송 폴백 (실시간은 WebSocket /app/conversations/{id}/send 사용) */
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ConversationMessageResponse> send(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        ConversationMessageResponse res = conversationService.send(userId, conversationId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/{conversationId}/messages/{messageId}/translate")
    public Map<String, String> translate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        return Map.of("translated", conversationService.translate(userId, conversationId, messageId, lang));
    }
}
