package com.guidematch.auth.oauth;

import com.guidematch.config.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 소셜 인증 성공 → 우리 JWT 발급 후 프론트 콜백으로 fragment 전달. */
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final String frontendUrl;

    public OAuth2SuccessHandler(JwtProvider jwtProvider,
                                @Value("${app.frontend-url}") String frontendUrl) {
        this.jwtProvider = jwtProvider;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        Long userId = ((Number) principal.getAttribute("userId")).longValue();
        String email = principal.getAttribute("email");
        String role = principal.getAttribute("role");

        String token = jwtProvider.createToken(userId, email, role);
        // 토큰은 URL fragment로 — 서버 로그/리퍼러에 노출되지 않는다.
        String target = frontendUrl + "/auth/callback#token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&role=" + URLEncoder.encode(role, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
