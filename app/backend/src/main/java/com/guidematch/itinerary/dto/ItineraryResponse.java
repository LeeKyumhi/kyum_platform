package com.guidematch.itinerary.dto;

import com.guidematch.itinerary.Itinerary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 일정 상세 (아이템 포함). */
public record ItineraryResponse(
        Long id,
        String title,
        String city,
        LocalDate startDate,
        LocalDate endDate,
        Instant createdAt,
        List<ItineraryItemResponse> items
) {
    public static ItineraryResponse from(Itinerary it) {
        return new ItineraryResponse(
                it.getId(), it.getTitle(), it.getCity(), it.getStartDate(), it.getEndDate(), it.getCreatedAt(),
                it.getItems().stream().map(ItineraryItemResponse::from).toList()
        );
    }
}
