package com.guidematch.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "토큰은 필수입니다.")
        String token
) {
}
