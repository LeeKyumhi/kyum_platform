package com.guidematch.auth;

import com.guidematch.auth.dto.LoginRequest;
import com.guidematch.auth.dto.SignupRequest;
import com.guidematch.config.JwtProvider;
import com.guidematch.email.EmailService;
import com.guidematch.user.AuthProvider;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 서비스(Service) = 실제 비즈니스 로직(규칙)을 담는 계층.
 * 컨트롤러는 "요청을 받고 응답을 주는" 역할만 하고,
 * "이메일 중복 검사 후 비밀번호를 암호화해서 저장한다" 같은 실제 처리는 여기서 한다.
 */
@Service
public class AuthService {

    private static final long VERIFICATION_TOKEN_TTL_HOURS = 24;
    private static final long RESET_TOKEN_TTL_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;

    // 스프링이 필요한 부품(Repository, PasswordEncoder, JwtProvider)을 자동으로 넣어준다 (의존성 주입).
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                        EmailVerificationTokenRepository verificationTokenRepository,
                        PasswordResetTokenRepository resetTokenRepository,
                        EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.verificationTokenRepository = verificationTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.emailService = emailService;
    }

    public User signup(SignupRequest request) {
        // 1) 이미 가입된 이메일이면 막는다.
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        // 2) 비밀번호를 BCrypt로 암호화(해시). 원문은 저장하지 않는다.
        String hashedPassword = passwordEncoder.encode(request.password());

        // 3) 새 사용자 생성 후 DB에 저장.
        User user = new User(
                request.email(),
                hashedPassword,
                request.fullName(),
                request.nationality()
        );
        if (request.gender() != null && !request.gender().isBlank()) {
            user.setGender(request.gender());
        }
        // 닉네임(선택) — 형식·중복 검증 후 설정. 잘못되면 무시하지 않고 막아 가입 단계에서 바로잡게 한다.
        if (request.nickname() != null && !request.nickname().isBlank()) {
            String nickname = request.nickname().trim();
            if (!nickname.matches("^[A-Za-z0-9_]{3,20}$")) {
                throw new IllegalArgumentException("닉네임은 3~20자의 영문·숫자·밑줄(_)만 사용할 수 있습니다.");
            }
            if (userRepository.existsByNicknameIgnoreCase(nickname)) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            }
            user.setNickname(nickname);
        }
        User saved = userRepository.save(user);

        // 이메일 발송은 계정 생성 트랜잭션과 분리한다 — 발송 실패가 가입 자체를 막으면 안 된다.
        issueVerificationTokenAndSend(saved);
        return saved;
    }

    private void issueVerificationTokenAndSend(User user) {
        EmailVerificationToken token = verificationTokenRepository.save(new EmailVerificationToken(
                user.getId(),
                UUID.randomUUID().toString(),
                Instant.now().plus(VERIFICATION_TOKEN_TTL_HOURS, ChronoUnit.HOURS)
        ));
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), token.getToken());
    }

    /** 로그인 상태에서 인증 메일 재발송 (프로필 배너의 "재발송" 액션). 이미 인증된 상태면 false. */
    public boolean resendVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (user.isEmailVerified()) return false;
        issueVerificationTokenAndSend(user);
        return true;
    }

    /**
     * 비로그인 상태의 인증 메일 재발송 (로그인 게이트에 막힌 유저용).
     * 계정 존재 여부를 노출하지 않기 위해, 미인증 로컬 계정이 있을 때만 조용히 발송하고
     * 그 외(계정 없음/이미 인증/소셜 계정)에는 아무 것도 하지 않는다. 호출부는 항상 같은 성공 응답.
     */
    public void resendVerificationByEmail(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getProvider() == AuthProvider.LOCAL && !user.isEmailVerified()) {
                issueVerificationTokenAndSend(user);
            }
        });
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 인증 링크입니다."));
        if (!verificationToken.isValid()) {
            throw new IllegalArgumentException("만료되었거나 이미 사용된 인증 링크입니다.");
        }
        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setEmailVerified(true);
        verificationToken.markConsumed();
    }

    /**
     * 비밀번호를 잊었을 때. 계정 존재 여부를 노출하지 않기 위해
     * 이메일이 없어도 예외 없이 조용히 리턴한다 — 호출부는 항상 같은 성공 메시지를 보여준다.
     */
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken token = resetTokenRepository.save(new PasswordResetToken(
                    user.getId(),
                    UUID.randomUUID().toString(),
                    Instant.now().plus(RESET_TOKEN_TTL_HOURS, ChronoUnit.HOURS)
            ));
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token.getToken());
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 재설정 링크입니다."));
        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("만료되었거나 이미 사용된 재설정 링크입니다.");
        }
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setPassword(passwordEncoder.encode(newPassword));
        resetToken.markConsumed();
    }

    /**
     * 로그인. 성공하면 JWT 출입증을 발급해 반환한다.
     *
     * 보안 포인트: 이메일이 없는 경우와 비밀번호가 틀린 경우의 에러 메시지를 똑같이 둔다.
     * "이메일은 있는데 비번이 틀렸다"는 정보를 노출하면 공격자에게 힌트를 주기 때문.
     */
    /** 로그인 결과 — 토큰과 권한(role)을 함께 돌려준다. */
    public record LoginResult(String token, String role) {}

    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 소셜 전용 계정(비밀번호 없음)으로 로컬 로그인 시도 → 자격증명 오류로 통일 처리
        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // 이메일 인증 게이트: 로컬 계정이 미인증이면 로그인 차단 (소셜 계정은 항상 verified)
        if (user.getProvider() == AuthProvider.LOCAL && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException("이메일 인증이 필요합니다. 받은 편지함을 확인해 주세요.");
        }

        if (user.isSuspended()) {
            throw new IllegalArgumentException("정지된 계정입니다. 고객센터에 문의하세요.");
        }

        String token = jwtProvider.createToken(user.getId(), user.getEmail(), user.getRole().name());
        return new LoginResult(token, user.getRole().name());
    }
}
