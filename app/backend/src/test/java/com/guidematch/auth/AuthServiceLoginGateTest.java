package com.guidematch.auth;

import com.guidematch.auth.dto.LoginRequest;
import com.guidematch.config.JwtProvider;
import com.guidematch.email.EmailService;
import com.guidematch.user.AuthProvider;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthServiceLoginGateTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final EmailVerificationTokenRepository verifyRepo = mock(EmailVerificationTokenRepository.class);
    private final PasswordResetTokenRepository resetRepo = mock(PasswordResetTokenRepository.class);
    private final EmailService emailService = mock(EmailService.class);

    private final AuthService service = new AuthService(
            userRepository, encoder, jwtProvider, verifyRepo, resetRepo, emailService);

    private User localUser(boolean verified) {
        User u = new User("a@b.com", encoder.encode("pw12345678"), "홍길동", "KR");
        u.setEmailVerified(verified);
        u.setProvider(AuthProvider.LOCAL);
        return u;
    }

    @Test
    void login_unverifiedLocal_throwsEmailNotVerified() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(localUser(false)));

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "pw12345678")))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_verifiedLocal_succeeds() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(localUser(true)));
        when(jwtProvider.createToken(any(), any(), any())).thenReturn("jwt-token");

        AuthService.LoginResult result = service.login(new LoginRequest("a@b.com", "pw12345678"));
        assertThat(result.token()).isEqualTo("jwt-token");
    }

    @Test
    void login_wrongPassword_throwsIllegalArgument_notEmailNotVerified() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(localUser(false)));

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "wrongpw123")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
