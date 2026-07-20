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
        for (Follow f : follows.findAll()) {
            Long followedUserId = profiles.findById(f.getGuideProfileId())
                    .map(GuideProfile::getUserId).orElse(null);
            if (followedUserId == null) continue;
            if (userFollows.existsByFollowerUserIdAndFollowedUserId(f.getFollowerUserId(), followedUserId)) continue;
            userFollows.save(new UserFollow(f.getFollowerUserId(), followedUserId));
        }
    }
}
