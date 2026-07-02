package com.guidematch.guide;

import com.guidematch.guide.dto.CreateGuideProfileRequest;
import com.guidematch.guide.dto.GuideProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 가이드 프로필 API.
 * 이 주소들은 보호되어 있어 로그인(JWT)이 필요하다.
 * @AuthenticationPrincipal Long userId = JWT 필터가 심어둔 현재 로그인 사용자 id.
 */
@RestController
@RequestMapping("/api/guide-profiles")
public class GuideProfileController {

    private final GuideProfileService guideProfileService;

    public GuideProfileController(GuideProfileService guideProfileService) {
        this.guideProfileService = guideProfileService;
    }

    /** 가이드 프로필 생성 (가이드로 활동하기) */
    @PostMapping
    public ResponseEntity<GuideProfileResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateGuideProfileRequest request
    ) {
        GuideProfile profile = guideProfileService.create(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(GuideProfileResponse.from(profile));
    }

    /** 내 가이드 프로필 조회 */
    @GetMapping("/me")
    public GuideProfileResponse getMine(@AuthenticationPrincipal Long userId) {
        GuideProfile profile = guideProfileService.getByUserId(userId);
        return GuideProfileResponse.from(profile);
    }

    /** 활동 상태 변경 (on/off 토글) */
    @PatchMapping("/me/active")
    public GuideProfileResponse setActive(
            @AuthenticationPrincipal Long userId,
            @RequestBody ActiveRequest request
    ) {
        GuideProfile profile = guideProfileService.setActive(userId, request.active());
        return GuideProfileResponse.from(profile);
    }

    record ActiveRequest(boolean active) {}

    /** MBTI · 관심사 업데이트 */
    @PatchMapping("/me/personality")
    public GuideProfileResponse updatePersonality(
            @AuthenticationPrincipal Long userId,
            @RequestBody PersonalityRequest request
    ) {
        GuideProfile profile = guideProfileService.updatePersonality(userId, request.mbti(), request.interests());
        return GuideProfileResponse.from(profile);
    }

    record PersonalityRequest(String mbti, List<String> interests) {}

    /** 활동 위치(도시 + 좌표) 업데이트 */
    @PatchMapping("/me/location")
    public GuideProfileResponse updateLocation(
            @AuthenticationPrincipal Long userId,
            @RequestBody LocationRequest request
    ) {
        GuideProfile profile = guideProfileService.updateLocation(
                userId, request.city(), request.latitude(), request.longitude());
        return GuideProfileResponse.from(profile);
    }

    record LocationRequest(String city, Double latitude, Double longitude) {}

    /** 내 프로필 사진 업로드 */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GuideProfileResponse uploadAvatar(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file
    ) {
        GuideProfile profile = guideProfileService.uploadAvatar(userId, file);
        return GuideProfileResponse.from(profile);
    }
}
