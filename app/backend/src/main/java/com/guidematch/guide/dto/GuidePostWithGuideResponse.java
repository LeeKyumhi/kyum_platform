package com.guidematch.guide.dto;

import com.guidematch.guide.GuidePost;
import com.guidematch.guide.GuideProfile;

import java.time.Instant;
import java.util.List;

public record GuidePostWithGuideResponse(
        Long id,
        Long guideProfileId,
        String guideName,
        String guideAvatarUrl,
        String guideHeadline,
        String guideRegion,
        List<String> guideLanguages,
        String content,
        String imageUrl,
        String category,
        long viewCount,
        Instant createdAt,
        long likeCount,
        long commentCount,
        boolean isLiked
) {
    public static GuidePostWithGuideResponse from(GuidePost post, GuideProfile profile, String guideName,
                                                  List<String> guideLanguages,
                                                  long likeCount, long commentCount, boolean isLiked) {
        return new GuidePostWithGuideResponse(
                post.getId(),
                profile.getId(),
                guideName,
                profile.getAvatarUrl(),
                profile.getHeadline(),
                profile.getRegion(),
                guideLanguages,
                post.getContent(),
                post.getImageUrl(),
                post.getCategory(),
                post.getViewCount(),
                post.getCreatedAt(),
                likeCount,
                commentCount,
                isLiked
        );
    }
}
