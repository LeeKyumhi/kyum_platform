package com.guidematch.booking;

import com.guidematch.booking.dto.BookingResponse;
import com.guidematch.booking.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 예약(매칭) API. 모두 로그인 필요.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** 여행자: 예약 요청 생성 */
    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingResponse res = bookingService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /** 여행자: 내가 보낸 예약 목록 */
    @GetMapping("/traveler")
    public List<BookingResponse> myTravelerBookings(@AuthenticationPrincipal Long userId) {
        return bookingService.listForTraveler(userId);
    }

    /** 가이드: 내가 받은 예약 목록 */
    @GetMapping("/guide")
    public List<BookingResponse> myGuideBookings(@AuthenticationPrincipal Long userId) {
        return bookingService.listForGuide(userId);
    }

    /** 가이드: 대기 중 예약 요청 수 (사이드바 알림 배지용) */
    @GetMapping("/guide/pending-count")
    public java.util.Map<String, Long> pendingCount(@AuthenticationPrincipal Long userId) {
        return java.util.Map.of("count", bookingService.pendingCountForGuide(userId));
    }

    /** 가이드: 수락 */
    @PatchMapping("/{id}/accept")
    public BookingResponse accept(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return bookingService.accept(userId, id);
    }

    /** 가이드: 거절 */
    @PatchMapping("/{id}/reject")
    public BookingResponse reject(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return bookingService.reject(userId, id);
    }

    /** 여행자: 취소 */
    @PatchMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return bookingService.cancel(userId, id);
    }

    /** 완료 처리 (여행자/가이드) */
    @PatchMapping("/{id}/complete")
    public BookingResponse complete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return bookingService.complete(userId, id);
    }
}
