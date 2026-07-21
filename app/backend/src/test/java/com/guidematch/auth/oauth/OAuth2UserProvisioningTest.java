package com.guidematch.auth.oauth;

import com.guidematch.user.AuthProvider;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OAuth2UserProvisioningTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomOAuth2UserService service = new CustomOAuth2UserService(userRepository);

    @Test
    void newSocialUser_createdVerified_withProvider() {
        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User u = service.provisionUser("google", "new@gmail.com", "홍길동", "g-123");

        assertThat(u.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(u.getProviderId()).isEqualTo("g-123");
        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.getPassword()).isNull();
    }

    @Test
    void existingEmail_linkedAndPromotedToVerified() {
        User existing = new User("me@gmail.com", "hash", "홍길동", "KR");
        existing.setEmailVerified(false);
        when(userRepository.findByEmail("me@gmail.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User u = service.provisionUser("google", "me@gmail.com", "홍길동", "g-999");

        assertThat(u.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(u.getProviderId()).isEqualTo("g-999");
        assertThat(u.isEmailVerified()).isTrue(); // 제공자가 검증 → 승격
        assertThat(u.getPassword()).isEqualTo("hash"); // 기존 비번 보존
    }

    @Test
    void kakao_registrationId_mapsToKakaoProvider() {
        when(userRepository.findByEmail("k@daum.net")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User u = service.provisionUser("kakao", "k@daum.net", "카카오유저", "k-1");

        assertThat(u.getProvider()).isEqualTo(AuthProvider.KAKAO);
    }
}
