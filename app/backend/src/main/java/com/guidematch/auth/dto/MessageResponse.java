package com.guidematch.auth.dto;

/** 성공/안내 메시지만 돌려주면 되는 엔드포인트용 공용 응답 (예: 비번 재설정 요청, 이메일 인증). */
public record MessageResponse(String message) {
}
