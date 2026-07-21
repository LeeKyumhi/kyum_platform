package com.guidematch.guide;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 가이드 팔로우 어댑터. guideProfileId를 받아 내부적으로 userId로 해석한 뒤
 * user_follows(UserFollowRepository) 기반 로직(FollowService.followUser 등)으로 위임한다.
 * `/api/users/me/following` (통합 팔로잉 목록)은 UserFollowController로 이전됨 — 매핑 중복 방지.
 */
@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /** 가이드 팔로우 — guideProfileId → userId 해석 후 위임 */
    @PostMapping("/api/guides/{guideProfileId}/follow")
    public ResponseEntity<Void> follow(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long guideProfileId) {
        followService.follow(userId, guideProfileId);
        return ResponseEntity.ok().build();
    }

    /** 가이드 언팔로우 — guideProfileId → userId 해석 후 위임 */
    @DeleteMapping("/api/guides/{guideProfileId}/follow")
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long guideProfileId) {
        followService.unfollow(userId, guideProfileId);
        return ResponseEntity.noContent().build();
    }

    /** 팔로워 수 조회 (공개) — guideProfileId → userId 해석 후 위임 */
    @GetMapping("/api/guides/{guideProfileId}/followers/count")
    public Map<String, Long> followerCount(@PathVariable Long guideProfileId) {
        return Map.of("count", followService.followerCount(guideProfileId));
    }
}
