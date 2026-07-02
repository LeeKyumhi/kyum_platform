package com.guidematch.itinerary.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/** 일정 전체 저장(메타 + 아이템 통째로 교체) 입력. */
public record UpdateItineraryRequest(
        @NotBlank(message = "제목을 입력해 주세요.") String title,
        String city,
        LocalDate startDate,
        LocalDate endDate,
        List<ItineraryItemRequest> items
) {}
