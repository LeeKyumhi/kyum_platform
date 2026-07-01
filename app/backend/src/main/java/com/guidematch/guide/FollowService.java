package com.guidematch.guide;

import com.guidematch.guide.dto.FollowingGuideResponse;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final GuideProfileRepository profileRepository;
    private final UserRepository userRepository;

    public FollowService(FollowRepository followRepository,
                         GuideProfileRepository profileRepository,
                         UserRepository userRepository) {
        this.followRepository = followRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void follow(Long userId, Long guideProfileId) {
        if (!profileRepository.existsById(guideProfileId)) {
            throw new IllegalArgumentException("가이드를 찾을 수 없습니다.");
        }
        if (followRepository.existsByFollowerUserIdAndGuideProfileId(userId, guideProfileId)) {
            return; // 이미 팔로우 중이면 무시 (idempotent)
        }
        followRepository.save(new Follow(userId, guideProfileId));
    }

    @Transactional
    public void unfollow(Long userId, Long guideProfileId) {
        followRepository.findByFollowerUserIdAndGuideProfileId(userId, guideProfileId)
                .ifPresent(followRepository::delete);
    }

    @Transactional(readOnly = true)
    public long followerCount(Long guideProfileId) {
        return followRepository.countByGuideProfileId(guideProfileId);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(Long userId, Long guideProfileId) {
        return followRepository.existsByFollowerUserIdAndGuideProfileId(userId, guideProfileId);
    }

    @Transactional(readOnly = true)
    public List<FollowingGuideResponse> myFollowing(Long userId) {
        return followRepository.findByFollowerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> profileRepository.findById(f.getGuideProfileId())
                        .map(profile -> {
                            String name = userRepository.findById(profile.getUserId())
                                    .map(User::getFullName).orElse("Unknown");
                            return FollowingGuideResponse.from(profile, name);
                        }).orElse(null))
                .filter(r -> r != null)
                .toList();
    }
}
