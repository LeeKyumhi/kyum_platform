package com.guidematch.guide.dto;

import com.guidematch.guide.GuidePost;
import com.guidematch.guide.GuideProfile;

import java.time.Instant;

public record GuidePostWithGuideResponse(
        Long id,
        Long guideProfileId,
        String guideName,
        String guideAvatarUrl,
        String guideHeadline,
        String guideRegion,
        String content,
        String imageUrl,
        Instant createdAt,
        long likeCount,
        long commentCount,
        boolean isLiked
) {
    public static GuidePostWithGuideResponse from(GuidePost post, GuideProfile profile, String guideName,
                                                  long likeCount, long commentCount, boolean isLiked) {
        return new GuidePostWithGuideResponse(
                post.getId(),
                profile.getId(),
                guideName,
                profile.getAvatarUrl(),
                profile.getHeadline(),
                profile.getRegion(),
                post.getContent(),
                post.getImageUrl(),
                post.getCreatedAt(),
                likeCount,
                commentCount,
                isLiked
        );
    }
}
