package com.guidematch.auth.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Arrays;

/**
 * stateless JWT 체인을 유지하기 위해 OAuth 핸드셰이크 상태(authorization request)를
 * 세션 대신 짧은 수명 쿠키에 저장한다.
 *
 * 쿠키 값은 클라이언트가 임의로 조작할 수 있으므로, 역직렬화 전에 반드시
 * HMAC-SHA256 서명을 검증한다. (기밀성은 불필요 — state/nonce/redirect만 담겨
 * 있음 — 하지만 신뢰할 수 없는 바이트를 역직렬화하면 DoS/RCE 위험이 있어 무결성
 * 검증이 필수.) 서명이 없거나 일치하지 않으면 쿠키가 없는 것처럼 취급한다.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";
    private static final int MAX_AGE_SECONDS = 180;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec hmacKey;

    public CookieOAuth2AuthorizationRequestRepository(@Value("${jwt.secret}") String secret) {
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request)
                .map(c -> deserialize(c.getValue()))
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }
        Cookie cookie = new Cookie(COOKIE_NAME, serialize(authorizationRequest));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(MAX_AGE_SECONDS);
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
        deleteCookie(response);
        return authRequest;
    }

    private java.util.Optional<Cookie> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return java.util.Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .findFirst();
    }

    private void deleteCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        String payload = Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(request));
        String signature = Base64.getUrlEncoder().encodeToString(hmac(payload));
        return payload + "." + signature;
    }

    /**
     * 서명을 먼저 검증한 뒤에만 역직렬화한다. 형식이 잘못됐거나, 서명이 일치하지
     * 않거나, 역직렬화 중 예외가 발생하면 null을 반환한다 — 절대로 검증되지 않은
     * 바이트를 역직렬화하지 않는다.
     */
    private OAuth2AuthorizationRequest deserialize(String value) {
        int lastDot = value.lastIndexOf('.');
        if (lastDot < 0 || lastDot == value.length() - 1) {
            return null;
        }
        String payload = value.substring(0, lastDot);
        String signature = value.substring(lastDot + 1);

        byte[] expectedSignature;
        byte[] providedSignature;
        try {
            expectedSignature = hmac(payload);
            providedSignature = Base64.getUrlDecoder().decode(signature);
        } catch (Exception e) {
            return null;
        }

        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            return null;
        }

        try {
            return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(
                    Base64.getUrlDecoder().decode(payload));
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC for OAuth2 authorization request cookie", e);
        }
    }
}
