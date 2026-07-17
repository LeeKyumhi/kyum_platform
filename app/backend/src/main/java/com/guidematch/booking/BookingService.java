package com.guidematch.booking;

import com.guidematch.booking.dto.BookingResponse;
import com.guidematch.booking.dto.CreateBookingRequest;
import com.guidematch.booking.dto.SetMeetingPlaceRequest;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.itinerary.ItineraryService;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final GuideProfileService guideProfileService;
    private final GuideProfileRepository guideProfileRepository;
    private final UserRepository userRepository;
    private final ItineraryService itineraryService;

    public BookingService(BookingRepository bookingRepository,
                          GuideProfileService guideProfileService,
                          GuideProfileRepository guideProfileRepository,
                          UserRepository userRepository,
                          ItineraryService itineraryService) {
        this.bookingRepository = bookingRepository;
        this.guideProfileService = guideProfileService;
        this.guideProfileRepository = guideProfileRepository;
        this.userRepository = userRepository;
        this.itineraryService = itineraryService;
    }

    /**
     * 여행자가 예약 요청.
     * 가이드가 즉시 예약(instantBooking)을 켜둔 경우, 수락 대기 없이 바로 ACCEPTED로 확정한다 —
     * 단, accept()와 동일한 시간 겹침 검사를 통과해야 한다 (이중 예약 방지는 절대 우회하지 않는다).
     * 겹치면 REQUESTED로 조용히 낮추지 않고 명확한 에러를 던져 여행자가 다른 시간을 고르게 한다.
     */
    @Transactional
    public BookingResponse create(Long travelerId, CreateBookingRequest request) {
        GuideProfile guide = guideProfileService.getById(request.guideId());

        // 본인의 가이드 프로필은 예약할 수 없음
        if (guide.getUserId().equals(travelerId)) {
            throw new IllegalArgumentException("본인의 가이드 프로필은 예약할 수 없습니다.");
        }

        // 서비스 카테고리 게이팅 (관광/비관광 완전 분리):
        // ① 관광(자격 필수) 카테고리는 인증 가이드만 예약 가능 ② 가이드가 실제 제공하는 서비스만 예약 가능.
        com.guidematch.guide.ServiceCategory category =
                com.guidematch.guide.ServiceCategory.fromKey(request.serviceCategory());
        if (category.requiresGuideLicense() && !guide.isVerified()) {
            throw new IllegalArgumentException("이 가이드는 관광통역안내사 자격 인증이 없어 관광 서비스를 예약할 수 없습니다.");
        }
        if (!guide.getServiceCategoryList().contains(category.name())) {
            throw new IllegalArgumentException("이 가이드가 제공하지 않는 서비스입니다.");
        }

        int rateSnapshot = guide.getHourlyRate();             // 계약 시점 시급 복사
        int totalPrice = rateSnapshot * request.hours();      // 총액 계산

        Booking booking = new Booking(
                travelerId,
                guide.getId(),
                request.startAt(),
                request.hours(),
                rateSnapshot,
                guide.getCurrency(),
                totalPrice,
                request.message(),
                category
        );

        boolean instant = guide.isInstantBooking();
        if (instant) {
            if (hasOverlapWithAccepted(guide.getId(), booking.getStartAt(), booking.getHours())) {
                throw new IllegalArgumentException("선택하신 시간에 이미 다른 예약이 확정되어 있습니다. 다른 시간을 선택해주세요.");
            }
            booking.accept();
        }

        Booking saved = bookingRepository.save(booking);

        if (instant) {
            try {
                itineraryService.autoAddTourItem(saved.getTravelerId(), saved.getId(), saved.getStartAt(),
                        guide.getHeadline(), guide.getCity(), saved.getMessage());
            } catch (Exception e) {
                log.warn("즉시 예약 {}건의 일정 자동 추가 중 예기치 못한 오류: {}", saved.getId(), e.toString());
            }
        }

        return toResponse(saved);
    }

    /** 예약 상세 (T2) — 여행자/가이드 참여자만 조회 가능. */
    @Transactional(readOnly = true)
    public BookingResponse getDetail(Long userId, Long bookingId) {
        return toResponse(assertParticipant(userId, bookingId));
    }

    /**
     * 만남 장소 지정/변경 (T1) — 참여자(여행자/가이드) 누구나 가능(협의 상황).
     * 죽은 예약(거절/취소/완료)에는 지정 의미가 없어 REQUESTED/ACCEPTED에서만 허용.
     */
    @Transactional
    public BookingResponse setMeetingPlace(Long userId, Long bookingId, SetMeetingPlaceRequest req) {
        Booking booking = assertParticipant(userId, bookingId);
        if (booking.getStatus() != BookingStatus.REQUESTED && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new IllegalArgumentException("진행 중인 예약에만 만남 장소를 지정할 수 있습니다.");
        }
        booking.setMeetingPlace(req.name(), req.address(), req.lat(), req.lng(), req.url());
        return toResponse(booking);
    }

    /** 여행자: 내가 보낸 예약 목록. 목록을 열었으니 미확인 거절 배지를 클리어(#4, writable). */
    @Transactional
    public List<BookingResponse> listForTraveler(Long travelerId) {
        List<Booking> bookings = bookingRepository.findByTravelerIdOrderByCreatedAtDesc(travelerId);
        bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.REJECTED && Boolean.FALSE.equals(b.getRejectionSeen()))
                .forEach(Booking::markRejectionSeen);
        return toResponses(bookings);
    }

    /** 여행자: 미확인 거절 예약 수 — 사이드바 배지(#4). */
    @Transactional(readOnly = true)
    public long rejectedUnseenCount(Long travelerId) {
        return bookingRepository.countByTravelerIdAndStatusAndRejectionSeenFalse(travelerId, BookingStatus.REJECTED);
    }

    /** 가이드: 내가 받은 예약 목록 */
    @Transactional(readOnly = true)
    public List<BookingResponse> listForGuide(Long userId) {
        GuideProfile profile = guideProfileService.getByUserId(userId);
        return toResponses(bookingRepository.findByGuideProfileIdOrderByCreatedAtDesc(profile.getId()));
    }

    /** 가이드: 수락. 이미 수락한 예약과 시간이 겹치면 거부한다 (이중 예약 방지). */
    @Transactional
    public BookingResponse accept(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        GuideProfile guide = guideProfileService.getById(booking.getGuideProfileId());
        if (!guide.getUserId().equals(userId)) {
            throw new IllegalArgumentException("이 예약을 처리할 권한이 없습니다.");
        }

        if (hasOverlapWithAccepted(booking.getGuideProfileId(), booking.getStartAt(), booking.getHours())) {
            throw new IllegalArgumentException("이미 수락한 예약과 시간이 겹칩니다. 기존 일정을 확인해주세요.");
        }

        booking.accept();

        try {
            itineraryService.autoAddTourItem(booking.getTravelerId(), booking.getId(), booking.getStartAt(),
                    guide.getHeadline(), guide.getCity(), booking.getMessage());
        } catch (Exception e) {
            log.warn("예약 {}건 수락 후 일정 자동 추가 중 예기치 못한 오류: {}", booking.getId(), e.toString());
        }

        return toResponse(booking);
    }

    /** accept()와 즉시예약(create) 양쪽에서 공유하는 이중 예약 방지 검사 (단일 기준). */
    private boolean hasOverlapWithAccepted(Long guideProfileId, Instant newStart, Integer hours) {
        Instant newEnd = newStart.plusSeconds(hours * 3600L);
        return bookingRepository.findByGuideProfileIdAndStatus(guideProfileId, BookingStatus.ACCEPTED)
                .stream()
                .anyMatch(b -> {
                    Instant s = b.getStartAt();
                    Instant e = s.plusSeconds(b.getHours() * 3600L);
                    return s.isBefore(newEnd) && newStart.isBefore(e);
                });
    }

    /** 가이드: 대기 중(REQUESTED) 예약 요청 수 — 사이드바 알림 배지용. 가이드 프로필이 없으면 0. */
    @Transactional(readOnly = true)
    public long pendingCountForGuide(Long userId) {
        try {
            GuideProfile profile = guideProfileService.getByUserId(userId);
            return bookingRepository.countByGuideProfileIdAndStatusIn(
                    profile.getId(), List.of(BookingStatus.REQUESTED));
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /** 가이드: 거절 */
    @Transactional
    public BookingResponse reject(Long userId, Long bookingId) {
        Booking booking = getOwnedByGuide(userId, bookingId);
        booking.reject();
        return toResponse(booking);
    }

    /** 일정 종료(완료) 처리: 여행자/가이드 둘 다 가능 */
    @Transactional
    public BookingResponse complete(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        boolean isTraveler = booking.getTravelerId().equals(userId);
        GuideProfile guide = guideProfileService.getById(booking.getGuideProfileId());
        boolean isGuide = guide.getUserId().equals(userId);
        if (!isTraveler && !isGuide) {
            throw new IllegalArgumentException("이 예약을 처리할 권한이 없습니다.");
        }

        booking.complete();
        return toResponse(booking);
    }

    /** 여행자: 취소 */
    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        if (!booking.getTravelerId().equals(userId)) {
            throw new IllegalArgumentException("본인의 예약만 취소할 수 있습니다.");
        }
        booking.cancel();
        return toResponse(booking);
    }

    // --- 내부 도우미 ---

    /**
     * 해당 예약의 참여자(여행자 또는 가이드)인지 확인 후 반환. 제3자는 거부.
     * 예약 접근 규칙의 단일 소스 — MessageService(예약 채팅)도 이걸 사용한다.
     */
    public Booking assertParticipant(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        boolean isTraveler = booking.getTravelerId().equals(userId);
        GuideProfile guide = guideProfileService.getById(booking.getGuideProfileId());
        boolean isGuide = guide.getUserId().equals(userId);
        if (!isTraveler && !isGuide) {
            throw new IllegalArgumentException("이 예약에 접근할 권한이 없습니다.");
        }
        return booking;
    }

    /** 해당 예약이 이 사용자(가이드)의 것인지 확인 후 반환 */
    private Booking getOwnedByGuide(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        GuideProfile guide = guideProfileService.getById(booking.getGuideProfileId());
        if (!guide.getUserId().equals(userId)) {
            throw new IllegalArgumentException("이 예약을 처리할 권한이 없습니다.");
        }
        return booking;
    }

    /** 엔티티 → 응답 DTO (가이드/여행자 이름을 채워 넣음) — 단건 경로용 */
    private BookingResponse toResponse(Booking b) {
        GuideProfile guide = guideProfileService.getById(b.getGuideProfileId());
        String guideName = nameOf(guide.getUserId());
        String travelerName = nameOf(b.getTravelerId());
        return BookingResponse.of(b, guideName, guide.getHeadline(), guide.getAvatarUrl(), travelerName);
    }

    /**
     * 목록 경로용 — 가이드 프로필/사용자 이름을 일괄 조회(총 2쿼리)해 채운다.
     * 원격 DB(왕복 ~250ms)라 예약별 개별 조회(N+1)는 목록을 초 단위로 느리게 만든다.
     */
    private List<BookingResponse> toResponses(List<Booking> bookings) {
        if (bookings.isEmpty()) return List.of();

        Set<Long> profileIds = new HashSet<>();
        bookings.forEach(b -> profileIds.add(b.getGuideProfileId()));
        Map<Long, GuideProfile> profiles = new HashMap<>();
        guideProfileRepository.findAllById(profileIds).forEach(p -> profiles.put(p.getId(), p));

        Set<Long> userIds = new HashSet<>();
        bookings.forEach(b -> userIds.add(b.getTravelerId()));
        profiles.values().forEach(p -> userIds.add(p.getUserId()));
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getFullName()));

        return bookings.stream().map(b -> {
            GuideProfile guide = profiles.get(b.getGuideProfileId());
            String guideName = guide != null ? names.getOrDefault(guide.getUserId(), "알 수 없음") : "알 수 없음";
            String headline = guide != null ? guide.getHeadline() : null;
            String avatar = guide != null ? guide.getAvatarUrl() : null;
            return BookingResponse.of(b, guideName, headline, avatar, names.getOrDefault(b.getTravelerId(), "알 수 없음"));
        }).toList();
    }

    private String nameOf(Long userId) {
        return userRepository.findById(userId).map(User::getFullName).orElse("알 수 없음");
    }
}
