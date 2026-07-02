package com.guidematch.itinerary.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** 새 일정 생성 입력 (제목만 필수, 나머지는 이후 빌더에서 채움). */
public record CreateItineraryRequest(
        @NotBlank(message = "제목을 입력해 주세요.") String title,
        String city,
        LocalDate startDate,
        LocalDate endDate
) {}
