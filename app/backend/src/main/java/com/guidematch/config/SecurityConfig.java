package com.guidematch.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 보안 설정.
 * spring-boot-starter-security를 넣으면 기본적으로 모든 요청에 로그인을 요구하는데,
 * 우리는 토큰(JWT) 기반 API라서 그 기본 동작을 우리 방식으로 새로 정의한다.
 */
@Configuration
public class SecurityConfig {

    /**
     * 비밀번호 암호화 도구(BCrypt)를 스프링 빈으로 등록.
     * 이렇게 등록해두면 AuthService 등에서 자동으로 주입받아 쓸 수 있다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                // CSRF: 폼 기반 공격 방어 기능. 토큰 기반 API에선 불필요하므로 끈다.
                .csrf(csrf -> csrf.disable())
                // 아래 corsConfigurationSource 설정을 사용 (프론트와 통신 허용)
                .cors(cors -> {})
                // 세션을 쓰지 않는 무상태(STATELESS) 방식. 인증은 매 요청 토큰으로 처리.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 회원가입/로그인/헬스체크는 토큰 없이 누구나 접근 가능
                        .requestMatchers("/api/auth/**", "/api/health").permitAll()
                        // WebSocket 핸드셰이크 (실제 인증은 STOMP CONNECT 인터셉터에서 처리)
                        .requestMatchers("/ws/**").permitAll()
                        // 가이드 검색/조회(GET)는 비로그인 여행자도 둘러볼 수 있게 공개
                        .requestMatchers(HttpMethod.GET, "/api/guides", "/api/guides/**", "/api/posts", "/api/courses").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/guides/*/followers/count").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/saved/counts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/guides/*/slots").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/comments").permitAll()
                        // 게시글 조회수 증가는 비로그인 방문자도 카운트 (임프레션)
                        .requestMatchers(HttpMethod.POST, "/api/posts/*/view").permitAll()
                        // 게시글/리뷰는 공개 콘텐츠 — 번역도 비로그인 방문자가 사용 가능
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/translate", "/api/reviews/*/translate").permitAll()
                        // 도시 목록·지역 장소 검색은 비로그인도 사용 (도시 선택/둘러보기 UI)
                        .requestMatchers(HttpMethod.GET, "/api/cities", "/api/places", "/api/places/nearby").permitAll()
                        // 운영자 전용 API — ROLE_ADMIN 권한(JWT role=ADMIN)이 있어야 접근 가능
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 그 외 모든 요청은 유효한 토큰(로그인)이 있어야 접근 가능
                        .anyRequest().authenticated()
                )
                // 인증 안 된 요청이 보호된 자원에 접근하면 401(Unauthorized) 반환
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다.")
                ))
                // 우리가 만든 JWT 필터를, 스프링 기본 인증 필터보다 먼저 실행
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 설정.
     * 브라우저는 보안상 "다른 출처(origin)"로의 요청을 기본 차단한다.
     * 프론트(localhost:3000)가 백엔드(localhost:8080)를 부를 수 있도록 명시적으로 허용한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
