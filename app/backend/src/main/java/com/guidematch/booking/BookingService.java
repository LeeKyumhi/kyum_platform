package com.guidematch.booking;

import com.guidematch.booking.dto.BookingResponse;
import com.guidematch.booking.dto.CreateBookingRequest;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final GuideProfileService guideProfileService;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository,
                          GuideProfileService guideProfileService,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.guideProfileService = guideProfileService;
        this.userRepository = userRepository;
    }

    /** 여행자가 예약 요청 */
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

        return toResponse(bookingRepository.save(booking));
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

    /** 가이드: 수락 */
    @Transactional
    public BookingResponse accept(Long userId, Long bookingId) {
        Booking booking = getOwnedByGuide(userId, bookingId);
        booking.accept();
        return toResponse(booking);
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
