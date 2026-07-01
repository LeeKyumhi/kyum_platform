package com.guidematch.auth;

import com.guidematch.auth.dto.LoginRequest;
import com.guidematch.auth.dto.SignupRequest;
import com.guidematch.config.JwtProvider;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 서비스(Service) = 실제 비즈니스 로직(규칙)을 담는 계층.
 * 컨트롤러는 "요청을 받고 응답을 주는" 역할만 하고,
 * "이메일 중복 검사 후 비밀번호를 암호화해서 저장한다" 같은 실제 처리는 여기서 한다.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // 스프링이 필요한 부품(Repository, PasswordEncoder, JwtProvider)을 자동으로 넣어준다 (의존성 주입).
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
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
        return userRepository.save(user);
    }

    /**
     * 로그인. 성공하면 JWT 출입증을 발급해 반환한다.
     *
     * 보안 포인트: 이메일이 없는 경우와 비밀번호가 틀린 경우의 에러 메시지를 똑같이 둔다.
     * "이메일은 있는데 비번이 틀렸다"는 정보를 노출하면 공격자에게 힌트를 주기 때문.
     */
    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 입력한 비밀번호(원문)와 DB의 해시를 비교. matches가 내부에서 안전하게 대조한다.
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return jwtProvider.createToken(user.getId(), user.getEmail());
    }
}
