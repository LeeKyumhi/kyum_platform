package com.guidematch.chat;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.chat.dto.MessageResponse;
import com.guidematch.geo.GoogleTranslateClient;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final BookingRepository bookingRepository;
    private final GuideProfileService guideProfileService;
    private final UserRepository userRepository;
    private final GoogleTranslateClient googleClient;

    public MessageService(MessageRepository messageRepository,
                          BookingRepository bookingRepository,
                          GuideProfileService guideProfileService,
                          UserRepository userRepository,
                          GoogleTranslateClient googleClient) {
        this.messageRepository = messageRepository;
        this.bookingRepository = bookingRepository;
        this.guideProfileService = guideProfileService;
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

    /** 대화 내역 조회 */
    @Transactional(readOnly = true)
    public List<MessageResponse> history(Long userId, Long bookingId) {
        assertParticipant(userId, bookingId);
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

    /**
     * 이 사용자가 해당 예약의 당사자(여행자 또는 가이드)인지 검증.
     * 제3자가 남의 대화를 보거나 끼어드는 것을 막는다.
     */
    private Booking assertParticipant(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        boolean isTraveler = booking.getTravelerId().equals(userId);

        GuideProfile guide = guideProfileService.getById(booking.getGuideProfileId());
        boolean isGuide = guide.getUserId().equals(userId);

        if (!isTraveler && !isGuide) {
            throw new IllegalArgumentException("이 대화에 참여할 권한이 없습니다.");
        }
        return booking;
    }

    private MessageResponse toResponse(Message m) {
        String senderName = userRepository.findById(m.getSenderId())
                .map(User::getFullName).orElse("알 수 없음");
        return MessageResponse.of(m, senderName);
    }
}
