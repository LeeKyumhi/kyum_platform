package com.guidematch.common;

import com.guidematch.auth.EmailNotVerifiedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 전역 예외 처리기.
 * 컨트롤러 어디서든 특정 예외가 발생하면 여기서 가로채
 * 일관된 형태의 에러 응답(JSON)으로 변환해 클라이언트에 돌려준다.
 *
 * @RestControllerAdvice = 모든 컨트롤러에 공통 적용되는 처리기.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 규칙 위반(예: 이메일 중복) → 400 Bad Request + 메시지
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * 이메일 인증 전 로그인 시도 → 403 + code=EMAIL_NOT_VERIFIED.
     * 프론트가 자격증명 오류와 구분해 "인증 필요 + 재발송" UI를 띄우도록 code를 실어 보낸다.
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleEmailNotVerified(EmailNotVerifiedException e) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage(), "code", "EMAIL_NOT_VERIFIED"));
    }

    /**
     * 입력값 검증 실패(예: 이메일 형식 오류, 비밀번호 너무 짧음) → 400 + 첫 번째 에러 메시지
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message));
    }
}
