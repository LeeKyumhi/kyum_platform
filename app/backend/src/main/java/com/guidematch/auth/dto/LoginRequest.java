package com.guidematch.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 데이터 (이메일 + 비밀번호).
 */
public record LoginRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
