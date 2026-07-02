package com.guidematch.user;

import com.guidematch.auth.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return UserResponse.from(userRepository.save(user));
    }

    record PersonalityRequest(String mbti, List<String> interests) {}

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
