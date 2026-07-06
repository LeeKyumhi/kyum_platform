package com.guidematch.user;

import com.guidematch.auth.AuthService;
import com.guidematch.auth.dto.MessageResponse;
import com.guidematch.auth.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserController(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    /**
     * 이메일 인증 메일 재발송. 로그인 상태에서만 호출 가능(누구 메일로 보낼지 토큰으로 특정) —
     * /api/auth/**가 아닌 인증이 필요한 경로에 둔다 (미인증 principal에 401 던지지 않기 위함).
     */
    @PostMapping("/me/resend-verification")
    public MessageResponse resendVerification(@AuthenticationPrincipal Long userId) {
        boolean sent = authService.resendVerification(userId);
        return new MessageResponse(sent ? "인증 메일을 다시 보냈습니다." : "이미 인증된 이메일입니다.");
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    /** 여행자 MBTI + 관심사 수정 */
    @PatchMapping("/me/personality")
    public UserResponse updatePersonality(
            @AuthenticationPrincipal Long userId,
            @RequestBody PersonalityRequest req
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (req.mbti() != null) user.setMbti(req.mbti().isBlank() ? null : req.mbti().toUpperCase());
        if (req.interests() != null) user.setInterestList(req.interests());
        if (req.gender() != null) user.setGender(req.gender().isBlank() ? null : req.gender());
        return UserResponse.from(userRepository.save(user));
    }

    record PersonalityRequest(String mbti, List<String> interests, String gender) {}

    /** 공개 핸들(닉네임) 설정/변경. 3~20자 영문·숫자·밑줄, 대소문자 무시 유니크. 빈 값이면 해제. */
    @PatchMapping("/me/nickname")
    public UserResponse updateNickname(
            @AuthenticationPrincipal Long userId,
            @RequestBody NicknameRequest req
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String nickname = req.nickname() == null ? null : req.nickname().trim();
        if (nickname == null || nickname.isEmpty()) {
            user.setNickname(null); // 닉네임 해제 → 이메일 로컬파트로 폴백
            return UserResponse.from(userRepository.save(user));
        }
        if (!nickname.matches("^[A-Za-z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("닉네임은 3~20자의 영문·숫자·밑줄(_)만 사용할 수 있습니다.");
        }
        // 변경이 없으면 통과, 있으면 중복 검사 (대소문자 무시)
        boolean unchanged = nickname.equalsIgnoreCase(user.getNickname());
        if (!unchanged && userRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
        user.setNickname(nickname);
        return UserResponse.from(userRepository.save(user));
    }

    record NicknameRequest(String nickname) {}

    /** 여행자 위치(도시 + 좌표) 수정 */
    @PatchMapping("/me/location")
    public UserResponse updateLocation(
            @AuthenticationPrincipal Long userId,
            @RequestBody LocationRequest req
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.updateLocation(
                (req.city() != null && req.city().isBlank()) ? null : req.city(),
                req.latitude(), req.longitude());
        return UserResponse.from(userRepository.save(user));
    }

    record LocationRequest(String city, Double latitude, Double longitude) {}
}
