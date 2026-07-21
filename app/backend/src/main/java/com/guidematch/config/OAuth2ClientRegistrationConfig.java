package com.guidematch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * 구글/카카오 OAuth2 클라이언트 등록을 직접 만든다 (application.yml이 아니라 코드에서).
 *
 * 왜 필요한가: Spring Boot의 OAuth2ClientProperties는 @ConfigurationProperties 빈이라
 * spring.security.oauth2.client.registration.* 아래에 google/kakao 블록이 "존재"하기만 하면
 * (client-id가 빈 문자열이어도) 컨텍스트 초기화 중 항상(다른 빈이 실제로 참조하는지와 무관하게)
 * validate()가 실행되어 "Client id ... must not be empty"로 애플리케이션 기동 자체가 실패한다.
 * 그래서 application.yml에서는 그 블록을 완전히 비웠고(있으면 검증이 걸리므로, 커스텀
 * ClientRegistrationRepository 빈을 따로 둬도 우회되지 않음을 실측 확인했다), 등록 정보는
 * 이 클래스에서 100% 코드로만 구성한다.
 *
 * 이 프로젝트의 다른 선택적 키(Kakao Map, Google Translate 등)는 "키가 없으면 해당 기능만
 * 비활성, 앱은 정상 기동"이 원칙이므로(CLAUDE.md), OAuth2도 동일하게 키가 없는 provider는
 * 등록 자체를 건너뛰어 앱이 항상 뜨도록 만든다.
 */
@Configuration
public class OAuth2ClientRegistrationConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_OAUTH_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_OAUTH_CLIENT_SECRET:}") String googleClientSecret,
            @Value("${KAKAO_OAUTH_CLIENT_ID:}") String kakaoClientId,
            @Value("${KAKAO_OAUTH_CLIENT_SECRET:}") String kakaoClientSecret) {

        List<ClientRegistration> registrations = new ArrayList<>();

        if (googleClientId != null && !googleClientId.isBlank()) {
            // CommonOAuth2Provider.GOOGLE는 Spring Security 6에서 제거됨 — 구글의 안정적인
            // 공개 OAuth2/OIDC 엔드포인트를 직접 지정한다 (application.yml의 provider.google 설정과 동일한 값).
            registrations.add(ClientRegistration.withRegistrationId("google")
                    .clientId(googleClientId)
                    .clientSecret(googleClientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/google")
                    .scope("email", "profile")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .userNameAttributeName("sub")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .issuerUri("https://accounts.google.com")
                    .clientName("Google")
                    .build());
        }

        if (kakaoClientId != null && !kakaoClientId.isBlank()) {
            registrations.add(ClientRegistration.withRegistrationId("kakao")
                    .clientId(kakaoClientId)
                    .clientSecret(kakaoClientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/kakao")
                    .scope("account_email")
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id")
                    .clientName("Kakao")
                    .build());
        }

        if (registrations.isEmpty()) {
            // 두 provider 모두 키가 없음 — /oauth2/authorization/* 로 들어오는 요청은
            // 등록된 클라이언트를 찾지 못해 401로 처리되지만(SecurityConfig의 401 entry point),
            // 그 외 API는 정상 동작한다.
            return registrationId -> null;
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }
}
