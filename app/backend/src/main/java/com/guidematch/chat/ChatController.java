package com.guidematch.chat;

import com.guidematch.chat.dto.MessageResponse;
import com.guidematch.chat.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 채팅 메시지 API (예약 단위). 로그인 + 예약 당사자만 접근 가능.
 */
@RestController
@RequestMapping("/api/bookings/{bookingId}/messages")
public class ChatController {

    private final MessageService messageService;

    public ChatController(MessageService messageService) {
        this.messageService = messageService;
    }

    /** 대화 내역 조회 */
    @GetMapping
    public List<MessageResponse> history(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long bookingId
    ) {
        return messageService.history(userId, bookingId);
    }

    /** 메시지 전송 */
    @PostMapping
    public ResponseEntity<MessageResponse> send(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long bookingId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        MessageResponse res = messageService.send(userId, bookingId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }
}
