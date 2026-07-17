package com.guidematch.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 만남 장소 지정 요청 (T1). 좌표는 지도 핀에 필요하므로 필수, 이름도 필수.
 * 주소·URL은 선택(Kakao 결과에 따라 없을 수 있음).
 */
public record SetMeetingPlaceRequest(
        @NotBlank(message = "장소명은 필수입니다.")
        String name,
        String address,
        @NotNull(message = "위도는 필수입니다.")
        Double lat,
        @NotNull(message = "경도는 필수입니다.")
        Double lng,
        String url
) {
}
