package com.guidematch.guide.dto;

/** 내가 팔로우 중인 사용자(여행자/가이드 공통) 목록 항목 */
public record FollowingUserResponse(
        Long userId, String handle, String name, String avatarUrl,
        boolean isGuide, Long guideProfileId, String headline
) {}
