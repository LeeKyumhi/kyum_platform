package com.guidematch.config;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 필터(Filter) = 컨트롤러에 도달하기 "전에" 모든 요청을 가로채는 관문.
 *
 * 이 필터는 요청 헤더의 토큰(Authorization: Bearer xxx)을 확인해서
 * - 유효하면: "이 요청은 누구(userId)다"라고 SecurityContext에 기록 → 인증된 상태가 됨
 * - 없거나 유효하지 않으면: 인증 없이 통과 → 보호된 주소면 나중에 401로 막힘
 *
 * OncePerRequestFilter = 한 요청당 정확히 한 번만 실행되도록 보장하는 스프링 기본 필터.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final com.guidematch.user.UserRepository userRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider,
                                   com.guidematch.user.UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            try {
                Long userId = jwtProvider.getUserId(token);

                // 정지 계정 즉시 차단: 토큰이 유효해도 status=SUSPENDED면 인증하지 않는다.
                // 정지 즉시 반영을 위해 인증 요청마다 계정 상태를 1회 조회한다(인덱스 PK 조회).
                // 의도된 트레이드오프: 즉시성 우선. 부하가 문제되면 향후 캐시/클레임 기반으로 전환.
                boolean suspended = userRepository.findById(userId)
                        .map(com.guidematch.user.User::isSuspended)
                        .orElse(false);
                if (suspended) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                // 권한(role) 반영: ADMIN이면 ROLE_ADMIN 권한을 부여해 /api/admin/** 접근을 허용한다.
                // 그 외(기존 토큰·일반 사용자)는 권한 없음 — 기존 동작과 동일.
                var authorities = "ADMIN".equals(jwtProvider.getRole(token))
                        ? Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                        : AuthorityUtils.NO_AUTHORITIES;

                // 인증 객체 생성. principal(주체)에 userId를 담아두면
                // 이후 컨트롤러에서 @AuthenticationPrincipal로 꺼내 쓸 수 있다.
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException e) {
                // 토큰이 위조됐거나 만료됨 → 인증 정보를 비워둔다.
                SecurityContextHolder.clearContext();
            }
        }

        // 다음 단계로 요청을 넘긴다.
        filterChain.doFilter(request, response);
    }

    /** "Authorization: Bearer xxxxx" 헤더에서 토큰 부분만 추출한다. */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7); // "Bearer " 다음부터가 실제 토큰
        }
        return null;
    }
}
