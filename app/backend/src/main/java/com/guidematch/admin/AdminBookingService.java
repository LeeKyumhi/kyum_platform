package com.guidematch.admin;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 관리자 예약 조회 + 강제 취소. 가격은 스냅샷 표시만(재계산 금지). */
@Service
public class AdminBookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final GuideProfileRepository guideProfileRepository;

    public AdminBookingService(BookingRepository bookingRepository, UserRepository userRepository,
                               GuideProfileRepository guideProfileRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.guideProfileRepository = guideProfileRepository;
    }

    public record BookingRow(Long id, Long travelerId, String travelerName,
                             Long guideProfileId, String guideName, String status,
                             Integer totalPrice, String currency, Instant startAt, Instant createdAt) {}

    @Transactional(readOnly = true)
    public AdminUserService.PageResult<BookingRow> list(String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Booking> result = (status == null || status.isBlank())
                ? bookingRepository.findAllByOrderByCreatedAtDesc(pr)
                : bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.valueOf(status), pr);

        // 이름 배치 조회(N+1 방지): 여행자 user + 가이드 프로필→user
        List<Long> profileIds = result.getContent().stream().map(Booking::getGuideProfileId).distinct().toList();
        Map<Long, GuideProfile> profiles = new HashMap<>();
        guideProfileRepository.findAllById(profileIds).forEach(p -> profiles.put(p.getId(), p));
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        result.getContent().forEach(b -> userIds.add(b.getTravelerId()));
        profiles.values().forEach(p -> userIds.add(p.getUserId()));
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getFullName()));

        List<BookingRow> rows = result.getContent().stream().map(b -> {
            GuideProfile p = profiles.get(b.getGuideProfileId());
            String guideName = p != null ? names.getOrDefault(p.getUserId(), "알 수 없음") : "알 수 없음";
            return new BookingRow(
                    b.getId(), b.getTravelerId(), names.getOrDefault(b.getTravelerId(), "알 수 없음"),
                    b.getGuideProfileId(), guideName, b.getStatus().name(),
                    b.getTotalPrice(), b.getCurrency(), b.getStartAt(), b.getCreatedAt());
        }).toList();
        return new AdminUserService.PageResult<>(rows, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    /** 강제 취소 — REQUESTED/ACCEPTED만 취소 가능(Booking.cancel의 상태 가드 재사용). */
    @Transactional
    public void cancel(Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));
        b.cancel();
        bookingRepository.save(b);
    }
}
