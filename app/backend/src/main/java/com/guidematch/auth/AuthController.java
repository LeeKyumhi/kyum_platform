package com.guidematch.auth;

import com.guidematch.auth.dto.ForgotPasswordRequest;
import com.guidematch.auth.dto.LoginRequest;
import com.guidematch.auth.dto.MessageResponse;
import com.guidematch.auth.dto.ResendVerificationRequest;
import com.guidematch.auth.dto.ResetPasswordRequest;
import com.guidematch.auth.dto.SignupRequest;
import com.guidematch.auth.dto.TokenResponse;
import com.guidematch.auth.dto.UserResponse;
import com.guidematch.auth.dto.VerifyEmailRequest;
import com.guidematch.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 요청을 받는 창구(컨트롤러).
 * 주소: /api/auth/...
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 회원가입.
     * @Valid       : SignupRequest의 검증 규칙(@Email 등)을 자동 적용
     * @RequestBody : 요청 본문(JSON)을 SignupRequest 객체로 변환
     *
     * 성공 시 201(Created) 상태와 함께 가입된 사용자 정보(비밀번호 제외)를 반환.
     */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserResponse.from(user));
    }

    /**
     * 로그인. 성공하면 JWT 출입증(accessToken)을 반환.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok(TokenResponse.bearer(result.token(), result.role()));
    }

    /** 가입/재발송 메일의 인증 링크가 호출하는 엔드포인트. */
    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok(new MessageResponse("이메일 인증이 완료되었습니다."));
    }

    /**
     * 비밀번호를 잊었을 때. 계정 존재 여부를 노출하지 않기 위해
     * 이메일 존재 여부와 무관하게 항상 같은 성공 메시지를 반환한다.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(new MessageResponse("해당 이메일로 가입된 계정이 있다면 재설정 링크를 보냈습니다."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("비밀번호가 재설정되었습니다."));
    }

    /**
     * 비로그인 인증 메일 재발송. 계정 존재 여부를 노출하지 않도록 항상 같은 성공 메시지를 반환.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationByEmail(request.email());
        return ResponseEntity.ok(new MessageResponse("인증이 필요한 계정이라면 인증 메일을 다시 보냈습니다."));
    }
}
