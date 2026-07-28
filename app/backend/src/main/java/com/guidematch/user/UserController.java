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

    /**
     * 결제용 연락처 저장/해제. 국가번호를 포함한 E.164로만 받는다.
     *
     * 한국 번호가 없는 외국인 여행자가 이 서비스의 주 사용자이므로 국내 형식(010-)을 강제하지 않는다.
     * 저장된 값은 본인과 PG에만 가고, 상대방에게 나가는 응답에는 넣지 않는다(PhonePrivacyTest).
     */
    @PatchMapping("/me/phone")
    public UserResponse updatePhone(
            @AuthenticationPrincipal Long userId,
            @RequestBody PhoneRequest req
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String raw = req.phone() == null ? "" : req.phone().replaceAll("[\\s\\-()]", "");
        if (raw.isEmpty()) {
            user.setPhone(null); // 해제
            return UserResponse.from(userRepository.save(user));
        }
        // E.164: +(1~9로 시작하는 국가번호) + 총 7~15자리
        if (!raw.matches("^\\+[1-9]\\d{6,14}$")) {
            throw new IllegalArgumentException("국가번호를 포함한 전화번호를 입력해 주세요. (예: +82 10-1234-5678)");
        }
        user.setPhone(raw);
        return UserResponse.from(userRepository.save(user));
    }

    record PhoneRequest(String phone) {}
}
