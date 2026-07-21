package com.guidematch.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.*;

/**
 * OAuth2ClientRegistrationConfig 회귀 테스트.
 *
 * 배경: application.yml에 spring.security.oauth2.client.registration.* 블록이 있으면
 * client-id가 빈 문자열이어도 Spring Boot의 OAuth2ClientProperties#afterPropertiesSet()이
 * 컨텍스트 초기화 중 검증에 걸려 "Client id must not be empty"로 애플리케이션 기동 자체가
 * 실패한다. 그래서 등록 정보를 전부 코드(OAuth2ClientRegistrationConfig)로 옮겼는데,
 * 이 회귀 클래스가 없으면 누군가 yml 블록을 다시 추가했을 때 CI가 그 크래시를 잡아내지
 * 못한다. 여기서는 전체 Spring 컨텍스트 없이 @Bean 메서드를 순수 자바 메서드로 직접
 * 호출해서, 키가 비어있어도 크래시 없이 빈 레지스트리를 만들 수 있음을 고정한다.
 */
class OAuth2ClientRegistrationConfigTest {

    private final OAuth2ClientRegistrationConfig config = new OAuth2ClientRegistrationConfig();

    @Test
    void allKeysBlank_buildsRepositoryWithoutCrashing_andHasNoRegistrations() {
        ClientRegistrationRepository repo =
                config.clientRegistrationRepository("", "", "", "");

        assertThat(repo).isNotNull();
        assertThat(repo.findByRegistrationId("google")).isNull();
        assertThat(repo.findByRegistrationId("kakao")).isNull();
    }

    @Test
    void allKeysNull_buildsRepositoryWithoutCrashing_andHasNoRegistrations() {
        // @Value("${...:}")가 값이 아예 없을 때 null을 넘기는 경우는 없지만,
        // 방어 코드(googleClientId != null)가 실제로 null-safe함을 함께 고정한다.
        ClientRegistrationRepository repo =
                config.clientRegistrationRepository(null, null, null, null);

        assertThat(repo).isNotNull();
        assertThat(repo.findByRegistrationId("google")).isNull();
        assertThat(repo.findByRegistrationId("kakao")).isNull();
    }

    @Test
    void googleKeyPresent_registersGoogleOnly() {
        ClientRegistrationRepository repo =
                config.clientRegistrationRepository("google-client-id", "google-secret", "", "");

        ClientRegistration google = repo.findByRegistrationId("google");
        assertThat(google).isNotNull();
        assertThat(google.getClientId()).isEqualTo("google-client-id");
        assertThat(google.getClientSecret()).isEqualTo("google-secret");
        assertThat(google.getScopes()).contains("email", "profile");
        assertThat(google.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");

        assertThat(repo.findByRegistrationId("kakao")).isNull();
    }

    @Test
    void kakaoKeyPresent_registersKakaoOnly() {
        ClientRegistrationRepository repo =
                config.clientRegistrationRepository("", "", "kakao-client-id", "kakao-secret");

        ClientRegistration kakao = repo.findByRegistrationId("kakao");
        assertThat(kakao).isNotNull();
        assertThat(kakao.getClientId()).isEqualTo("kakao-client-id");
        assertThat(kakao.getClientSecret()).isEqualTo("kakao-secret");
        assertThat(kakao.getProviderDetails().getAuthorizationUri())
                .startsWith("https://kauth.kakao.com");
        assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                .isEqualTo("id");

        assertThat(repo.findByRegistrationId("google")).isNull();
    }

    @Test
    void bothKeysPresent_registersBoth() {
        ClientRegistrationRepository repo = config.clientRegistrationRepository(
                "google-client-id", "google-secret", "kakao-client-id", "kakao-secret");

        assertThat(repo.findByRegistrationId("google")).isNotNull();
        assertThat(repo.findByRegistrationId("kakao")).isNotNull();
    }
}
