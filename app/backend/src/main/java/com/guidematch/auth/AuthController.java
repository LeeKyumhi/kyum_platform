package com.guidematch.auth;

import com.guidematch.auth.dto.LoginRequest;
import com.guidematch.auth.dto.SignupRequest;
import com.guidematch.auth.dto.TokenResponse;
import com.guidematch.auth.dto.UserResponse;
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
        String token = authService.login(request);
        return ResponseEntity.ok(TokenResponse.bearer(token));
    }
}
