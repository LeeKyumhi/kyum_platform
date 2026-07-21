package com.guidematch.auth.oauth;

import com.guidematch.user.AuthProvider;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 소셜 로그인 성공 후 제공자 프로필을 받아 우리 User로 매핑한다.
 * - 이메일이 식별키. 카카오가 이메일을 안 주면 가입 거부(kakao_no_email).
 * - 같은 이메일의 기존 계정이 있으면 자동 연결 + 인증 승격.
 */
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attrs = oauthUser.getAttributes();

        String email = extractEmail(registrationId, attrs);
        if (email == null || email.isBlank()) {
            // 카카오 이메일 미동의/미제공 → 가입 거부
            throw new OAuth2AuthenticationException(new OAuth2Error("kakao_no_email"),
                    "이메일이 제공되지 않았습니다.");
        }
        String name = extractName(registrationId, attrs, email);
        String providerId = String.valueOf(attrs.getOrDefault("id",
                attrs.getOrDefault("sub", email)));

        User user = provisionUser(registrationId, email, name, providerId);

        // successHandler가 userId/role을 읽을 수 있도록 attribute에 실어 반환한다.
        String nameKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                Map.of(
                        nameKey, attrs.getOrDefault(nameKey, providerId),
                        "userId", user.getId(),
                        "role", user.getRole().name(),
                        "email", user.getEmail()
                ),
                nameKey
        );
    }

    /** find-or-create + 자동 연결. 순수 로직으로 단위 테스트 대상. */
    public User provisionUser(String registrationId, String email, String name, String providerId) {
        AuthProvider provider = "kakao".equalsIgnoreCase(registrationId)
                ? AuthProvider.KAKAO : AuthProvider.GOOGLE;

        return userRepository.findByEmail(email)
                .map(existing -> {
                    // 자동 연결: provider/providerId 채우고 인증 승격
                    if (existing.getProvider() == AuthProvider.LOCAL) {
                        existing.setProvider(provider);
                    }
                    if (existing.getProviderId() == null) {
                        existing.setProviderId(providerId);
                    }
                    existing.setEmailVerified(true);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User created = new User(email, null, name, null);
                    created.setProvider(provider);
                    created.setProviderId(providerId);
                    created.setEmailVerified(true);
                    return userRepository.save(created);
                });
    }

    @SuppressWarnings("unchecked")
    private String extractEmail(String registrationId, Map<String, Object> attrs) {
        if ("kakao".equalsIgnoreCase(registrationId)) {
            Object account = attrs.get("kakao_account");
            if (account instanceof Map<?, ?> m) {
                Object email = ((Map<String, Object>) m).get("email");
                return email == null ? null : email.toString();
            }
            return null;
        }
        Object email = attrs.get("email"); // google
        return email == null ? null : email.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractName(String registrationId, Map<String, Object> attrs, String fallback) {
        if ("kakao".equalsIgnoreCase(registrationId)) {
            Object props = attrs.get("properties");
            if (props instanceof Map<?, ?> m) {
                Object nick = ((Map<String, Object>) m).get("nickname");
                if (nick != null) return nick.toString();
            }
            return fallback;
        }
        Object name = attrs.get("name"); // google
        return name == null ? fallback : name.toString();
    }
}
