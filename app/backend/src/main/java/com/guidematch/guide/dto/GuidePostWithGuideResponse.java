package com.guidematch.guide.dto;

import com.guidematch.guide.GuidePost;
import com.guidematch.guide.GuideProfile;

import java.time.Instant;
import java.util.List;

public record GuidePostWithGuideResponse(
        Long id,
        Long guideProfileId,
        Long authorUserId,
        boolean isGuide,
        String guideName,
        /** 인스타그램식 @아이디 (이메일 로컬파트에서 유도) */
        String authorHandle,
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
    /** 가이드가 쓴 게시글 */
    public static GuidePostWithGuideResponse fromGuide(GuidePost post, GuideProfile profile, Long authorUserId, String guideName,
                                                       String authorHandle, List<String> guideLanguages,
                                                       long likeCount, long commentCount, boolean isLiked) {
        return new GuidePostWithGuideResponse(
                post.getId(),
                profile.getId(),
                authorUserId,
                true,
                guideName,
                authorHandle,
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

    /** 여행자가 쓴 게시글 (가이드 프로필 없음) */
    public static GuidePostWithGuideResponse fromTraveler(GuidePost post, Long authorUserId, String authorName,
                                                          String authorHandle,
                                                          long likeCount, long commentCount, boolean isLiked) {
        return new GuidePostWithGuideResponse(
                post.getId(),
                null,
                authorUserId,
                false,
                authorName,
                authorHandle,
                null,
                null,
                null,
                List.of(),
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
