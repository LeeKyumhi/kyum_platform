package com.guidematch.guide;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class UserFollowBackfill implements ApplicationRunner {
    private final FollowRepository follows;                 // 기존 guide-profile 팔로우
    private final GuideProfileRepository profiles;
    private final UserFollowRepository userFollows;

    public UserFollowBackfill(FollowRepository follows, GuideProfileRepository profiles,
                              UserFollowRepository userFollows) {
        this.follows = follows; this.profiles = profiles; this.userFollows = userFollows;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 일회성 마이그레이션: user_follows에 이미 데이터가 있으면 재실행하지 않는다.
        // 이유: 언팔로우는 user_follows 행만 삭제하고 레거시 follows 행은 절대 건드리지 않으므로,
        // 이 가드가 없으면 재부팅마다 백필이 삭제된 행을 다시 넣어 언팔로우를 조용히 되돌린다.
        if (userFollows.count() > 0) return;
        for (Follow f : follows.findAll()) {
            Long followedUserId = profiles.findById(f.getGuideProfileId())
                    .map(GuideProfile::getUserId).orElse(null);
            if (followedUserId == null) continue;
            if (userFollows.existsByFollowerUserIdAndFollowedUserId(f.getFollowerUserId(), followedUserId)) continue;
            userFollows.save(new UserFollow(f.getFollowerUserId(), followedUserId));
        }
    }
}
