package com.guidematch.chat;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.chat.dto.MessageResponse;
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

    public MessageService(MessageRepository messageRepository,
                          BookingRepository bookingRepository,
                          GuideProfileService guideProfileService,
                          UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.bookingRepository = bookingRepository;
        this.guideProfileService = guideProfileService;
        this.userRepository = userRepository;
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
