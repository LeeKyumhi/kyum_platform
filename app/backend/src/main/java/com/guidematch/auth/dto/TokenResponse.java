package com.guidematch.auth.dto;

/**
 * 로그인 성공 시 돌려주는 응답.
 * accessToken = 앞으로 요청에 첨부할 JWT 출입증.
 * 클라이언트(프론트)는 이 값을 저장해두고, 이후 요청 헤더에 담아 보낸다.
 */
public record TokenResponse(
        String accessToken,
        String tokenType   // 보통 "Bearer"
) {
    public static TokenResponse bearer(String accessToken) {
        return new TokenResponse(accessToken, "Bearer");
    }
}
