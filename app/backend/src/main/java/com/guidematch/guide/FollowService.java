package com.guidematch.guide;

import com.guidematch.guide.dto.FollowingGuideResponse;
import com.guidematch.guide.dto.FollowingUserResponse;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FollowService {

    private final UserFollowRepository userFollows;
    private final GuideProfileRepository profileRepository;
    private final UserRepository userRepository;
    // 레거시 guide-profile 팔로우 리포지토리. 더 이상 읽거나 쓰지 않음 —
    // 4-arg 생성자 시그니처(FollowServiceTest, 브리프 지정)를 만족시키기 위해서만 보관.
    private final FollowRepository followRepository;

    public FollowService(UserFollowRepository userFollows,
                         GuideProfileRepository profileRepository,
                         UserRepository userRepository,
                         FollowRepository followRepository) {
        this.userFollows = userFollows;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
    }

    // ── user↔user ──────────────────────────────────────────────

    @Transactional
    public void followUser(Long follower, Long followed) {
        if (follower.equals(followed)) throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        if (userFollows.existsByFollowerUserIdAndFollowedUserId(follower, followed)) return; // idempotent
        userFollows.save(new UserFollow(follower, followed));
    }

    @Transactional
    public void unfollowUser(Long follower, Long followed) {
        userFollows.findByFollowerUserIdAndFollowedUserId(follower, followed)
                .ifPresent(userFollows::delete);
    }

    @Transactional(readOnly = true)
    public long followerCountOfUser(Long userId) {
        return userFollows.countByFollowedUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isFollowingUser(Long viewer, Long target) {
        return viewer != null && userFollows.existsByFollowerUserIdAndFollowedUserId(viewer, target);
    }

    @Transactional(readOnly = true)
    public long followingCountOfUser(Long userId) {
        return userFollows.countByFollowerUserId(userId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> followerCountsByUserIds(Collection<Long> ids) {
        Map<Long, Long> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        userFollows.countsByFollowedUserIds(ids).forEach(r -> out.put((Long) r[0], (Long) r[1]));
        return out;
    }

    @Transactional(readOnly = true)
    public List<FollowingUserResponse> myFollowingUsers(Long follower) {
        List<UserFollow> rows = userFollows.findByFollowerUserId(follower);
        if (rows.isEmpty()) return List.of();

        List<Long> followedIds = rows.stream().map(UserFollow::getFollowedUserId).toList();

        Map<Long, User> usersById = new HashMap<>();
        userRepository.findAllById(followedIds).forEach(u -> usersById.put(u.getId(), u));

        Map<Long, GuideProfile> profilesByUserId = new HashMap<>();
        for (Long userId : followedIds) {
            profileRepository.findByUserId(userId).ifPresent(p -> profilesByUserId.put(userId, p));
        }

        return followedIds.stream()
                .map(userId -> {
                    User user = usersById.get(userId);
                    if (user == null) return null;
                    GuideProfile profile = profilesByUserId.get(userId);
                    boolean isGuide = profile != null;
                    return new FollowingUserResponse(
                            userId,
                            user.getPublicHandle(),
                            user.getFullName(),
                            isGuide ? profile.getAvatarUrl() : null,
                            isGuide,
                            isGuide ? profile.getId() : null,
                            isGuide ? profile.getHeadline() : null
                    );
                })
                .filter(r -> r != null)
                .toList();
    }

    // ── guide-profile 어댑터 (하위호환) ───────────────────────────
    // 기존 /api/guides/{id}/follow 엔드포인트가 그대로 동작하도록,
    // guideProfileId → userId를 해석해 위의 user↔user 메서드로 위임한다.

    @Transactional
    public void follow(Long userId, Long guideProfileId) {
        GuideProfile profile = profileRepository.findById(guideProfileId)
                .orElseThrow(() -> new IllegalArgumentException("가이드를 찾을 수 없습니다."));
        followUser(userId, profile.getUserId());
    }

    @Transactional
    public void unfollow(Long userId, Long guideProfileId) {
        profileRepository.findById(guideProfileId)
                .ifPresent(profile -> unfollowUser(userId, profile.getUserId()));
    }

    @Transactional(readOnly = true)
    public long followerCount(Long guideProfileId) {
        return profileRepository.findById(guideProfileId)
                .map(profile -> followerCountOfUser(profile.getUserId()))
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(Long userId, Long guideProfileId) {
        if (userId == null) return false;
        return profileRepository.findById(guideProfileId)
                .map(profile -> isFollowingUser(userId, profile.getUserId()))
                .orElse(false);
    }

    /** 하위호환: 내가 팔로우 중인 "가이드" 목록 (여행자 팔로우는 제외). Task 7에서 정리 예정. */
    @Transactional(readOnly = true)
    public List<FollowingGuideResponse> myFollowing(Long userId) {
        return userFollows.findByFollowerUserId(userId).stream()
                .map(f -> profileRepository.findByUserId(f.getFollowedUserId())
                        .map(profile -> {
                            String name = userRepository.findById(profile.getUserId())
                                    .map(User::getFullName).orElse("Unknown");
                            return FollowingGuideResponse.from(profile, name);
                        }).orElse(null))
                .filter(r -> r != null)
                .toList();
    }
}
