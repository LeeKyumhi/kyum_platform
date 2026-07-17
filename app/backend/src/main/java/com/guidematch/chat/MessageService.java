package com.guidematch.chat;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingService;
import com.guidematch.chat.dto.MessageResponse;
import com.guidematch.geo.GoogleTranslateClient;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final BookingService bookingService;
    private final UserRepository userRepository;
    private final GoogleTranslateClient googleClient;

    public MessageService(MessageRepository messageRepository,
                          BookingService bookingService,
                          UserRepository userRepository,
                          GoogleTranslateClient googleClient) {
        this.messageRepository = messageRepository;
        this.bookingService = bookingService;
        this.userRepository = userRepository;
        this.googleClient = googleClient;
    }

    /** 메시지 전송 */
    @Transactional
    public MessageResponse send(Long userId, Long bookingId, String content) {
        assertParticipant(userId, bookingId);
        Message saved = messageRepository.save(new Message(bookingId, userId, content));
        return toResponse(saved);
    }

    /** 대화 내역 조회 — 방을 열었으니 여기까지 읽음 처리(통합 인박스 안읽음 배지 반영) */
    @Transactional
    public List<MessageResponse> history(Long userId, Long bookingId) {
        Booking booking = assertParticipant(userId, bookingId);
        booking.markChatRead(booking.getTravelerId().equals(userId)); // 여행자면 traveler, 아니면 가이드
        return messageRepository.findByBookingIdOrderByCreatedAtAsc(bookingId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * 메시지 한 건을 지정 언어로 번역 (예약 당사자만).
     * 채팅은 대부분 고유 문장이라 DB 캐시(unique 인덱스)에 넣지 않고
     * Google API를 직접 호출한다 (긴 텍스트로 인한 인덱스 문제 방지).
     */
    @Transactional(readOnly = true)
    public String translate(Long userId, Long bookingId, Long messageId, String uiLang) {
        assertParticipant(userId, bookingId);
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
        if (!message.getBookingId().equals(bookingId)) {
            throw new IllegalArgumentException("이 대화의 메시지가 아닙니다.");
        }
        if (!googleClient.isEnabled()) {
            throw new IllegalArgumentException("번역 기능을 사용할 수 없습니다.");
        }

        // 채팅은 한국어 UI 사용자도 '한국어로' 번역이 필요하므로 ko를 스킵하지 않는다
        String target = switch (uiLang == null ? "ko" : uiLang) {
            case "en" -> "en";
            case "zh" -> "zh-CN";
            default   -> "ko";
        };

        // sourceLang=null → 자동 감지 (채팅은 원문이 어느 언어일지 알 수 없음)
        List<String> result = googleClient.translate(List.of(message.getContent()), null, target);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("번역에 실패했습니다.");
        }
        return result.get(0);
    }

    /** 예약 당사자 검증 — 접근 규칙은 BookingService가 단일 소스. */
    private Booking assertParticipant(Long userId, Long bookingId) {
        return bookingService.assertParticipant(userId, bookingId);
    }

    private MessageResponse toResponse(Message m) {
        String senderName = userRepository.findById(m.getSenderId())
                .map(User::getFullName).orElse("알 수 없음");
        return MessageResponse.of(m, senderName);
    }
}
