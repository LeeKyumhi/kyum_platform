package com.guidematch.guide;

import com.guidematch.guide.dto.FollowingGuideResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /** 가이드 팔로우 */
    @PostMapping("/api/guides/{guideProfileId}/follow")
    public ResponseEntity<Void> follow(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long guideProfileId) {
        followService.follow(userId, guideProfileId);
        return ResponseEntity.ok().build();
    }

    /** 가이드 언팔로우 */
    @DeleteMapping("/api/guides/{guideProfileId}/follow")
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long guideProfileId) {
        followService.unfollow(userId, guideProfileId);
        return ResponseEntity.noContent().build();
    }

    /** 팔로워 수 조회 (공개) */
    @GetMapping("/api/guides/{guideProfileId}/followers/count")
    public Map<String, Long> followerCount(@PathVariable Long guideProfileId) {
        return Map.of("count", followService.followerCount(guideProfileId));
    }

    /** 내가 팔로우 중인 가이드 목록 */
    @GetMapping("/api/users/me/following")
    public List<FollowingGuideResponse> myFollowing(@AuthenticationPrincipal Long userId) {
        return followService.myFollowing(userId);
    }
}
