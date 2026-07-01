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
        Long travelerId,
        String travelerName,
        Instant startAt,
        Integer hours,
        Integer hourlyRateSnapshot,
        Integer totalPrice,
        String currency,
        BookingStatus status,
        String message,
        Instant createdAt
) {
    public static BookingResponse of(Booking b, String guideName, String guideHeadline, String travelerName) {
        return new BookingResponse(
                b.getId(),
                b.getGuideProfileId(),
                guideName,
                guideHeadline,
                b.getTravelerId(),
                travelerName,
                b.getStartAt(),
                b.getHours(),
                b.getHourlyRateSnapshot(),
                b.getTotalPrice(),
                b.getCurrency(),
                b.getStatus(),
                b.getMessage(),
                b.getCreatedAt()
        );
    }
}
