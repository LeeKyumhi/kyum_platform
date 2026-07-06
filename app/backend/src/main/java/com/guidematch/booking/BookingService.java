package com.guidematch.booking;

import com.guidematch.booking.dto.BookingResponse;
import com.guidematch.booking.dto.CreateBookingRequest;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.itinerary.ItineraryService;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final GuideProfileService guideProfileService;
    private final UserRepository userRepository;
    private final ItineraryService itineraryService;

    public BookingService(BookingRepository bookingRepository,
                          GuideProfileService guideProfileService,
                          UserRepository userRepository,
                          ItineraryService itineraryService) {
        this.bookingRepository = bookingRepository;
        this.guideProfileService = guideProfileService;
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
                request.message()
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

    /** 여행자: 내가 보낸 예약 목록 */
    @Transactional(readOnly = true)
    public List<BookingResponse> listForTraveler(Long travelerId) {
        return bookingRepository.findByTravelerIdOrderByCreatedAtDesc(travelerId)
                .stream().map(this::toResponse).toList();
    }

    /** 가이드: 내가 받은 예약 목록 */
    @Transactional(readOnly = true)
    public List<BookingResponse> listForGuide(Long userId) {
        GuideProfile profile = guideProfileService.getByUserId(userId);
        return bookingRepository.findByGuideProfileIdOrderByCreatedAtDesc(profile.getId())
                .stream().map(this::toResponse).toList();
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

    /** 엔티티 → 응답 DTO (가이드/여행자 이름을 채워 넣음) */
    private BookingResponse toResponse(Booking b) {
        GuideProfile guide = guideProfileService.getById(b.getGuideProfileId());
        String guideName = nameOf(guide.getUserId());
        String travelerName = nameOf(b.getTravelerId());
        return BookingResponse.of(b, guideName, guide.getHeadline(), travelerName);
    }

    private String nameOf(Long userId) {
        return userRepository.findById(userId).map(User::getFullName).orElse("알 수 없음");
    }
}
