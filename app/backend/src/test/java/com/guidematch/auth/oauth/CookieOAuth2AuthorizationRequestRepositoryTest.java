package com.guidematch.auth.oauth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.*;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private static final String TEST_SECRET = "test-secret-value-used-only-for-unit-tests-not-for-prod";
    private static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";

    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository(TEST_SECRET);

    private OAuth2AuthorizationRequest sampleRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://x/auth")
                .clientId("c")
                .redirectUri("https://x/cb")
                .state("st")
                .build();
    }

    private Cookie saveAndCaptureCookie(OAuth2AuthorizationRequest authRequest) {
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authRequest, saveRequest, saveResponse);

        Cookie cookie = saveResponse.getCookie(COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie;
    }

    @Test
    void roundTrip_validSignature_loadsSameState() {
        OAuth2AuthorizationRequest original = sampleRequest();
        Cookie cookie = saveAndCaptureCookie(original);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new Cookie(COOKIE_NAME, cookie.getValue()));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("st");
        assertThat(loaded.getClientId()).isEqualTo("c");
    }

    @Test
    void tamperedPayload_rejectedWithoutException() {
        Cookie cookie = saveAndCaptureCookie(sampleRequest());
        String value = cookie.getValue();

        int lastDot = value.lastIndexOf('.');
        assertThat(lastDot).isGreaterThan(0);
        String payload = value.substring(0, lastDot);
        String signature = value.substring(lastDot + 1);

        // flip one char in the payload portion so the signature no longer matches
        char flipped = payload.charAt(0) == 'a' ? 'b' : 'a';
        String tamperedPayload = flipped + payload.substring(1);
        String tamperedValue = tamperedPayload + "." + signature;

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new Cookie(COOKIE_NAME, tamperedValue));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNull();
    }

    @Test
    void missingSignature_rejectedWithoutException() {
        Cookie cookie = saveAndCaptureCookie(sampleRequest());
        String value = cookie.getValue();
        int lastDot = value.lastIndexOf('.');
        String payloadOnly = value.substring(0, lastDot);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new Cookie(COOKIE_NAME, payloadOnly));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNull();
    }

    @Test
    void garbageValue_rejectedWithoutException() {
        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new Cookie(COOKIE_NAME, "not-a-valid-signed-token"));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNull();
    }
}
