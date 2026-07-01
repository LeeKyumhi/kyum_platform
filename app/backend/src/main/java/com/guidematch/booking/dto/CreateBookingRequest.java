package com.guidematch.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * 예약 요청 데이터.
 *  guideId : 예약할 가이드 프로필 id
 *  startAt : 희망 시작 시각 (ISO-8601 문자열 → Instant 자동 변환)
 *  hours   : 이용 시간
 *  message : 가이드에게 남길 메시지 (선택)
 */
public record CreateBookingRequest(

        @NotNull(message = "가이드를 선택하세요.")
        Long guideId,

        @NotNull(message = "시작 시각은 필수입니다.")
        Instant startAt,

        @NotNull(message = "이용 시간은 필수입니다.")
        @Positive(message = "이용 시간은 1 이상이어야 합니다.")
        Integer hours,

        String message
) {
}
