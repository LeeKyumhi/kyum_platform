package com.guidematch.user.dto;

import java.util.List;

/** 공개 프로필 (비로그인 방문자도 조회 가능). GET /api/users/{handle} */
public record PublicProfileResponse(
        Long userId, String handle, String name, String avatarUrl,
        String nationality, String mbti, List<String> interests,
        boolean isGuide, Long guideProfileId,
        long followerCount, long followingCount, boolean isFollowing
) {}
