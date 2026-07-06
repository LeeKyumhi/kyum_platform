package com.guidematch.chat.ws;

import com.guidematch.chat.ConversationService;
import com.guidematch.chat.MessageService;
import com.guidematch.chat.dto.ConversationMessageResponse;
import com.guidematch.chat.dto.MessageResponse;
import com.guidematch.chat.dto.SendMessageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 실시간 메시지 처리.
 *
 * 흐름:
 *  1) 클라이언트가 /app/bookings/{bookingId}/send 로 메시지를 발행(publish)
 *  2) 이 핸들러가 받아 DB에 저장 (권한 검증 포함)
 *  3) /topic/bookings/{bookingId} 를 구독 중인 모두(여행자+가이드)에게 broadcast
 */
@Controller
public class ChatWebSocketController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(MessageService messageService,
                                   ConversationService conversationService,
                                   SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/bookings/{bookingId}/send")
    public void send(
            @DestinationVariable Long bookingId,
            @Payload SendMessageRequest request,
            Principal principal
    ) {
        // CONNECT 때 심어둔 Principal에서 사용자 id를 꺼낸다.
        Long userId = Long.valueOf(principal.getName());

        // 저장 (참여자 권한 검증은 MessageService 내부에서 수행)
        MessageResponse saved = messageService.send(userId, bookingId, request.content());

        // 같은 예약을 구독 중인 모두에게 즉시 전송
        messagingTemplate.convertAndSend("/topic/bookings/" + bookingId, saved);
    }

    /** 예약 전 문의 대화 — 예약 채팅과 동일한 흐름, 토픽만 /topic/conversations/{id} */
    @MessageMapping("/conversations/{conversationId}/send")
    public void sendDirect(
            @DestinationVariable Long conversationId,
            @Payload SendMessageRequest request,
            Principal principal
    ) {
        Long userId = Long.valueOf(principal.getName());
        ConversationMessageResponse saved = conversationService.send(userId, conversationId, request.content());
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, saved);
    }
}
