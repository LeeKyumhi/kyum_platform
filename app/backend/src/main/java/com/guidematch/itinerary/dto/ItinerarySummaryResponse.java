package com.guidematch.itinerary.dto;

import com.guidematch.itinerary.Itinerary;

import java.time.Instant;
import java.time.LocalDate;

/** 일정 목록용 요약 (아이템 개수만). */
public record ItinerarySummaryResponse(
        Long id,
        String title,
        String city,
        LocalDate startDate,
        LocalDate endDate,
        Instant createdAt,
        int itemCount
) {
    public static ItinerarySummaryResponse from(Itinerary it) {
        return new ItinerarySummaryResponse(
                it.getId(), it.getTitle(), it.getCity(), it.getStartDate(), it.getEndDate(),
                it.getCreatedAt(), it.getItems().size()
        );
    }
}
