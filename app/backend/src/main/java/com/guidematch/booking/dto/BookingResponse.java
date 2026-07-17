package com.guidematch.booking.dto;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingStatus;

import java.time.Instant;

/**
 * 예약 응답. 목록에서 양쪽 이름을 바로 보여주기 위해 이름을 포함한다.
 */
public record BookingResponse(
        Long id,
        Long guideProfileId,
        String guideName,
        String guideHeadline,
        /** 가이드 프로필 사진 — 예약 상세에서 여행자가 "예약한 그 사람인지" 대조(계정 대여 탐지). */
        String guideAvatarUrl,
        Long travelerId,
        String travelerName,
        Instant startAt,
        Integer hours,
        Integer hourlyRateSnapshot,
        Integer totalPrice,
        String currency,
        BookingStatus status,
        String message,
        /** 예약한 서비스 카테고리 (ServiceCategory 키). 기존 예약은 null. */
        String serviceCategory,
        /** 동행 예약의 카테고리별 요청 내용 (프론트 JSON 규약). 투어/기존 예약은 null. */
        String requestDetails,
        Instant createdAt,
        MeetingPlace meetingPlace
) {
    /** 만남 장소 (T1). 미지정이면 null. */
    public record MeetingPlace(String name, String address, Double lat, Double lng, String url) {}

    public static BookingResponse of(Booking b, String guideName, String guideHeadline,
                                     String guideAvatarUrl, String travelerName) {
        MeetingPlace mp = b.getMeetingPlaceLat() != null && b.getMeetingPlaceLng() != null
                ? new MeetingPlace(b.getMeetingPlaceName(), b.getMeetingPlaceAddress(),
                        b.getMeetingPlaceLat(), b.getMeetingPlaceLng(), b.getMeetingPlaceUrl())
                : null;
        return new BookingResponse(
                b.getId(),
                b.getGuideProfileId(),
                guideName,
                guideHeadline,
                guideAvatarUrl,
                b.getTravelerId(),
                travelerName,
                b.getStartAt(),
                b.getHours(),
                b.getHourlyRateSnapshot(),
                b.getTotalPrice(),
                b.getCurrency(),
                b.getStatus(),
                b.getMessage(),
                b.getServiceCategory() != null ? b.getServiceCategory().name() : null,
                b.getRequestDetails(),
                b.getCreatedAt(),
                mp
        );
    }
}
