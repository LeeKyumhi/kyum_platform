package com.guidematch.guide;

import com.guidematch.guide.dto.FollowingUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
public class UserFollowController {
    private final FollowService followService;
    public UserFollowController(FollowService followService) { this.followService = followService; }

    @PostMapping("/api/users/{userId}/follow")
    public ResponseEntity<Void> follow(@AuthenticationPrincipal Long me, @PathVariable Long userId) {
        followService.followUser(me, userId);
        return ResponseEntity.ok().build();
    }
    @DeleteMapping("/api/users/{userId}/follow")
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal Long me, @PathVariable Long userId) {
        followService.unfollowUser(me, userId);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/users/{userId}/followers/count")
    public Map<String, Long> followerCount(@PathVariable Long userId) {
        return Map.of("count", followService.followerCountOfUser(userId));
    }
    @GetMapping("/api/users/me/following")
    public List<FollowingUserResponse> myFollowing(@AuthenticationPrincipal Long me) {
        return followService.myFollowingUsers(me);
    }
}
