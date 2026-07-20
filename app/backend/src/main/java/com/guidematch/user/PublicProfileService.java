package com.guidematch.user;

import com.guidematch.guide.FollowService;
import com.guidematch.guide.GuidePostService;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.dto.GuidePostWithGuideResponse;
import com.guidematch.user.dto.PublicProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 공개 프로필 + 사용자 게시글 (비로그인 방문자도 조회 가능). */
@Service
public class PublicProfileService {

    private final UserRepository userRepository;
    private final GuideProfileRepository profileRepository;
    private final FollowService followService;
    private final GuidePostService guidePostService;

    public PublicProfileService(UserRepository userRepository,
                                GuideProfileRepository profileRepository,
                                FollowService followService,
                                GuidePostService guidePostService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.followService = followService;
        this.guidePostService = guidePostService;
    }

    /**
     * 핸들(닉네임, 대소문자 무시)로 공개 프로필 조회.
     * 알려진 한계: 닉네임을 설정하지 않은 사용자는 User.getHandle()이 이메일 로컬파트로 폴백하지만,
     * 이 조회는 nickname 컬럼만 조회하므로 그런 사용자는 404가 된다.
     */
    @Transactional(readOnly = true)
    public Optional<PublicProfileResponse> byHandle(String handle, Long viewer) {
        return userRepository.findByNicknameIgnoreCase(handle).map(user -> {
            Long userId = user.getId();
            GuideProfile profile = profileRepository.findByUserId(userId).orElse(null);
            boolean isGuide = profile != null;

            return new PublicProfileResponse(
                    userId,
                    user.getHandle(),
                    user.getFullName(),
                    isGuide ? profile.getAvatarUrl() : null,
                    user.getNationality(),
                    user.getMbti(),
                    user.getInterestList(),
                    isGuide,
                    isGuide ? profile.getId() : null,
                    followService.followerCountOfUser(userId),
                    followService.followingCountOfUser(userId),
                    followService.isFollowingUser(viewer, userId)
            );
        });
    }

    /** 핸들 기준 사용자 게시글 전체 (최신순). 존재하지 않는 핸들이면 빈 목록. */
    @Transactional(readOnly = true)
    public List<GuidePostWithGuideResponse> postsByHandle(String handle, Long viewer) {
        return userRepository.findByNicknameIgnoreCase(handle)
                .map(user -> guidePostService.listByAuthor(user.getId(), viewer))
                .orElse(List.of());
    }
}
