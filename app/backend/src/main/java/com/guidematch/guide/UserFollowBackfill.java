package com.guidematch.guide;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * ⚠ {@code ingest} 프로파일에서는 돌지 않는다.
 *
 * <p>이건 앱의 일회성 데이터 마이그레이션이지 적재 배치가 할 일이 아니다. 제외 이유가 둘이다.
 * <ul>
 *   <li><b>정확성</b> — 적재 배치는 자동 실행돼 하루에도 여러 번 뜬다. 그때마다 이 백필이
 *       돌면서 {@code user_follows}를 쓰는 건 배치의 책임 범위를 벗어난다.</li>
 *   <li><b>권한</b> — 적재는 knowledge 테이블 7개에만 권한이 있는 전용 롤로 붙는다
 *       (docs/ingest/db-role.sql). 이 백필이 돌면 {@code user_follows} 조회에서
 *       permission denied가 나 <b>기동 자체가 실패한다.</b></li>
 * </ul>
 */
@Profile("!ingest")
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
