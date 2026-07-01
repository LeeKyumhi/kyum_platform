package com.guidematch.guide.dto;

import com.guidematch.guide.GuideProfile;

import java.util.List;

/** 내가 팔로우 중인 가이드 목록 항목 */
public record FollowingGuideResponse(
        Long guideProfileId,
        String guideName,
        String guideAvatarUrl,
        String headline,
        String region,
        String mbti,
        List<String> interests
) {
    public static FollowingGuideResponse from(GuideProfile profile, String guideName) {
        return new FollowingGuideResponse(
                profile.getId(),
                guideName,
                profile.getAvatarUrl(),
                profile.getHeadline(),
                profile.getRegion(),
                profile.getMbti(),
                profile.getInterestList()
        );
    }
}
