package com.guidematch.guide.dto;

import com.guidematch.guide.LanguageLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 가이드 프로필 생성 요청.
 */
public record CreateGuideProfileRequest(

        @NotBlank(message = "한 줄 소개는 필수입니다.")
        String headline,

        String introduction,

        @NotNull(message = "시간당 요금은 필수입니다.")
        @Positive(message = "시간당 요금은 0보다 커야 합니다.")
        Integer hourlyRate,

        String currency,   // 비어있으면 서비스에서 KRW로 기본 설정

        @NotBlank(message = "활동 지역은 필수입니다.")
        String region,

        // 정형화된 위치 (선택). 프론트 CitySelect가 채움. region엔 도시명이 함께 들어온다.
        String city,
        Double latitude,
        Double longitude,

        @NotEmpty(message = "가능 언어를 최소 1개 입력하세요.")
        @Valid
        List<LanguageDto> languages,

        String mbti,           // 선택 (예: "ENFP")
        List<String> interests // 선택 (예: ["FOOD", "CAFE"])
) {

    /**
     * 언어 한 개 입력. (중첩 record)
     */
    public record LanguageDto(
            @NotBlank(message = "언어명은 필수입니다.")
            String language,

            @NotNull(message = "언어 수준은 필수입니다.")
            LanguageLevel level
    ) {
    }
}
