package com.guidematch.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT(JSON Web Token)를 만들고 검증하는 도구.
 *
 * JWT는 "서버가 서명한 출입증"이다. 구조는 세 부분(헤더.내용.서명).
 * - 내용(payload)에 사용자 id 같은 정보를 담는다.
 * - 서버만 아는 비밀키로 서명하므로, 위조하면 검증 단계에서 걸러진다.
 * - 로그인 시 발급해서 클라이언트가 보관하고, 이후 요청마다 첨부한다.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        // 문자열 비밀키를 서명용 키 객체로 변환
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 토큰 발급. 사용자 id를 subject(주체)로, 이메일을 부가 정보로 담는다.
     */
    public String createToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 토큰을 검증하고 내용(Claims)을 꺼낸다.
     * 서명이 틀리거나 만료됐으면 예외가 발생한다. (다음 단계의 인증 필터에서 사용)
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 토큰에서 사용자 id를 꺼낸다. */
    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }
}
