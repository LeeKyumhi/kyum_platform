package com.guidematch.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 소셜 인증 실패 → 프론트 로그인으로 에러코드 전달 (예: kakao_no_email). */
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final String frontendUrl;

    public OAuth2FailureHandler(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String code = "oauth_failed";
        if (exception instanceof OAuth2AuthenticationException oae && oae.getError() != null) {
            code = oae.getError().getErrorCode();
        }
        String target = frontendUrl + "/login?error="
                + URLEncoder.encode(code, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
