package com.guidematch.config;

import com.guidematch.auth.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.guidematch.auth.oauth.CustomOAuth2UserService;
import com.guidematch.auth.oauth.OAuth2FailureHandler;
import com.guidematch.auth.oauth.OAuth2SuccessHandler;
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
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           CustomOAuth2UserService customOAuth2UserService,
                                           OAuth2SuccessHandler oAuth2SuccessHandler,
                                           OAuth2FailureHandler oAuth2FailureHandler,
                                           CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository) throws Exception {
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
                        // 구글/카카오 OAuth2 진입·콜백 경로 — 핸드셰이크 자체는 토큰 없이 시작한다
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // WebSocket 핸드셰이크 (실제 인증은 STOMP CONNECT 인터셉터에서 처리)
                        .requestMatchers("/ws/**").permitAll()
                        // 가이드 검색/조회(GET)는 비로그인 여행자도 둘러볼 수 있게 공개
                        .requestMatchers(HttpMethod.GET, "/api/guides", "/api/guides/**", "/api/posts", "/api/courses").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/guides/*/followers/count").permitAll()
                        // 사용자 팔로워 수 조회(공개) — 팔로우 POST/DELETE·/api/users/me/following은 인증 필요(기본 anyRequest)
                        .requestMatchers(HttpMethod.GET, "/api/users/*/followers/count").permitAll()
                        // 공개 프로필(GET /api/users/{handle}, /api/users/{handle}/posts) — 비로그인도 조회 가능.
                        // 반드시 /me·/me/** authenticated 규칙보다 뒤에 두면 안 됨: /api/users/*가 /api/users/me와도 매칭되므로,
                        // authenticated 규칙을 먼저 선언해 우선 매칭시킨다(스프링 시큐리티는 선언 순서대로 첫 매칭을 적용).
                        .requestMatchers(HttpMethod.GET, "/api/users/me", "/api/users/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/*", "/api/users/*/posts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/saved/counts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/guides/*/slots").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/comments").permitAll()
                        // 게시글 조회수 증가는 비로그인 방문자도 카운트 (임프레션)
                        .requestMatchers(HttpMethod.POST, "/api/posts/*/view").permitAll()
                        // 게시글/리뷰는 공개 콘텐츠 — 번역도 비로그인 방문자가 사용 가능
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/translate", "/api/reviews/*/translate").permitAll()
                        // 도시 목록·지역 장소 검색은 비로그인도 사용 (도시 선택/둘러보기 UI)
                        // 노트 읽기(GET /api/places/notes)도 비로그인 탐색에서 보여야 한다.
                        // ⚠ 경로 정확 일치다. "/api/places/**"로 넓히면 앞으로 추가되는 GET 하위 경로가
                        //   의도치 않게 전부 공개된다 — 하나씩 명시한다. (POST/DELETE는 여기 없어 인증 필요)
                        .requestMatchers(HttpMethod.GET, "/api/cities", "/api/places", "/api/places/nearby",
                                         "/api/places/notes", "/api/places/media").permitAll()
                        // PortOne 웹훅 — 페이로드를 신뢰하지 않고 PortOne 재조회로만 확정하므로 public 안전
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        // 운영자 전용 API — ROLE_ADMIN 권한(JWT role=ADMIN)이 있어야 접근 가능
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 그 외 모든 요청은 유효한 토큰(로그인)이 있어야 접근 가능
                        .anyRequest().authenticated()
                )
                // 구글/카카오 OAuth2 로그인. authorization request는 세션이 아니라 쿠키에 저장해
                // STATELESS 세션 정책과 공존시킨다. 성공 시 우리 JWT 발급, 실패 시 에러코드와 함께 리다이렉트.
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(a -> a
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository))
                        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler))
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
