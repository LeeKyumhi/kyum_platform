package com.guidematch.guide.dto;

import com.guidematch.guide.GuidePost;

import java.time.Instant;

public record GuidePostResponse(
        Long id,
        Long guideProfileId,
        String content,
        String imageUrl,
        Instant createdAt,
        long likeCount,
        long commentCount,
        boolean isLiked
) {
    public static GuidePostResponse from(GuidePost post, long likeCount, long commentCount, boolean isLiked) {
        return new GuidePostResponse(
                post.getId(),
                post.getGuideProfileId(),
                post.getContent(),
                post.getImageUrl(),
                post.getCreatedAt(),
                likeCount,
                commentCount,
                isLiked
        );
    }

    /** 좋아요/댓글 없이 기본값으로 생성 (내 게시글 작성 응답 등) */
    public static GuidePostResponse from(GuidePost post) {
        return from(post, 0, 0, false);
    }
}
