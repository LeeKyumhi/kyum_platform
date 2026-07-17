package com.guidematch.chat;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.chat.dto.ConversationResponse;
import com.guidematch.chat.dto.InboxThreadResponse;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 통합 인박스 — 예약 전 문의(DM)와 예약 채팅을 한 목록으로 합친다.
 * 예약 채팅은 별도 last-message 컬럼이 없어 읽기 시점에 메시지에서 계산한다(스키마 무변경).
 * 원격 DB(왕복 ~250ms)라 예약별 마지막 메시지·상대 정보는 전부 일괄(batch) 조회로 구성한다.
 */
@Service
public class InboxService {

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final BookingRepository bookingRepository;
    private final MessageRepository messageRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final UserRepository userRepository;

    public InboxService(ConversationService conversationService,
                        ConversationRepository conversationRepository,
                        BookingRepository bookingRepository,
                        MessageRepository messageRepository,
                        GuideProfileRepository guideProfileRepository,
                        UserRepository userRepository) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.bookingRepository = bookingRepository;
        this.messageRepository = messageRepository;
        this.guideProfileRepository = guideProfileRepository;
        this.userRepository = userRepository;
    }

    /** 안읽은 총 개수(DM + 예약 채팅) — 사이드바 💬 배지. 원격 DB라 각 집계 쿼리 1회씩. */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        long dm = conversationService.unreadCount(userId);
        Long myGuideProfileId = guideProfileRepository.findByUserId(userId)
                .map(GuideProfile::getId).orElse(null);
        long booking = messageRepository.unreadCount(userId, myGuideProfileId);
        return dm + booking;
    }

    /** 내 모든 대화(DM + 예약 채팅)를 최근 메시지 순으로. */
    @Transactional(readOnly = true)
    public List<InboxThreadResponse> myInbox(Long userId) {
        List<InboxThreadResponse> threads = new ArrayList<>();

        // 1) DM — 기존 로직 재사용(차단 필터·배치 조회 포함). 안읽음 방 id 배치 1회.
        Set<Long> unreadConvIds = new HashSet<>(conversationRepository.conversationIdsWithUnread(userId));
        for (ConversationResponse c : conversationService.myConversations(userId)) {
            threads.add(InboxThreadResponse.dm(c, unreadConvIds.contains(c.id())));
        }

        // 2) 예약 채팅 — 여행자로 건 예약 + 가이드로 받은 예약(메시지 있는 방만)
        threads.addAll(bookingThreads(userId));

        // 3) 최근 메시지 순 (없으면 뒤로)
        threads.sort(Comparator.comparing(
                (InboxThreadResponse t) -> t.lastMessageAt() == null ? Instant.MIN : t.lastMessageAt())
                .reversed());
        return threads;
    }

    private List<InboxThreadResponse> bookingThreads(Long userId) {
        // 내가 당사자인 예약 모으기 (여행자 방향 + 가이드 방향)
        Map<Long, Booking> bookingsById = new LinkedHashMap<>();
        for (Booking b : bookingRepository.findByTravelerIdOrderByCreatedAtDesc(userId)) {
            bookingsById.put(b.getId(), b);
        }
        GuideProfile myGuideProfile = guideProfileRepository.findByUserId(userId).orElse(null);
        if (myGuideProfile != null) {
            for (Booking b : bookingRepository.findByGuideProfileIdOrderByCreatedAtDesc(myGuideProfile.getId())) {
                bookingsById.put(b.getId(), b);
            }
        }
        if (bookingsById.isEmpty()) return List.of();

        // 예약별 마지막 메시지(=대화가 실제로 오간 방만 인박스에 표시) — 배치 1회
        Map<Long, Message> lastByBooking = new HashMap<>();
        for (Message m : messageRepository.findByBookingIdInOrderByCreatedAtAsc(bookingsById.keySet())) {
            lastByBooking.put(m.getBookingId(), m); // 오름차순이라 마지막 put이 최신
        }
        if (lastByBooking.isEmpty()) return List.of();

        // 안읽음 예약 id (배치 1회, unreadCount와 동일 술어) — 메시지가 있는 경우에만 조회
        Long myGuideProfileId = myGuideProfile != null ? myGuideProfile.getId() : null;
        Set<Long> unreadBookingIds = new HashSet<>(messageRepository.bookingIdsWithUnread(userId, myGuideProfileId));

        // 상대방 표시정보 배치 조회 (가이드 프로필 + 사용자 이름)
        Set<Long> guideProfileIds = new HashSet<>();
        for (Long bookingId : lastByBooking.keySet()) {
            guideProfileIds.add(bookingsById.get(bookingId).getGuideProfileId());
        }
        Map<Long, GuideProfile> profiles = new HashMap<>();
        guideProfileRepository.findAllById(guideProfileIds)
                .forEach(p -> profiles.put(p.getId(), p));

        Set<Long> userIds = new HashSet<>();
        for (Long bookingId : lastByBooking.keySet()) {
            Booking b = bookingsById.get(bookingId);
            GuideProfile p = profiles.get(b.getGuideProfileId());
            if (p != null) userIds.add(p.getUserId()); // 가이드 쪽 사용자
            userIds.add(b.getTravelerId());            // 여행자 쪽 사용자
        }
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));

        List<InboxThreadResponse> result = new ArrayList<>();
        for (Map.Entry<Long, Message> e : lastByBooking.entrySet()) {
            Booking b = bookingsById.get(e.getKey());
            Message last = e.getValue();
            GuideProfile guide = profiles.get(b.getGuideProfileId());
            boolean viewerIsTraveler = b.getTravelerId().equals(userId);

            Long otherUserId;
            String otherName;
            String otherAvatarUrl;
            boolean otherIsGuide;
            if (viewerIsTraveler) {
                otherUserId = guide != null ? guide.getUserId() : null;
                otherName = otherUserId != null && users.containsKey(otherUserId)
                        ? users.get(otherUserId).getFullName() : "알 수 없음";
                otherAvatarUrl = guide != null ? guide.getAvatarUrl() : null;
                otherIsGuide = true;
            } else {
                otherUserId = b.getTravelerId();
                otherName = users.containsKey(otherUserId)
                        ? users.get(otherUserId).getFullName() : "알 수 없음";
                otherAvatarUrl = null; // 여행자는 아바타 없음
                otherIsGuide = false;
            }

            String preview = Previews.clip(last.getContent());

            result.add(new InboxThreadResponse(
                    "booking", null, b.getId(), b.getGuideProfileId(),
                    otherUserId, otherName, otherAvatarUrl, otherIsGuide,
                    preview, last.getCreatedAt(), b.getStatus().name(),
                    unreadBookingIds.contains(b.getId())));
        }
        return result;
    }
}
