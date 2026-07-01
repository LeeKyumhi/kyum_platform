package com.guidematch.guide.dto;

import com.guidematch.guide.PostComment;
import java.time.Instant;

public record PostCommentResponse(
        Long id,
        Long postId,
        Long userId,
        String userName,
        String content,
        Instant createdAt
) {
    public static PostCommentResponse from(PostComment c, String userName) {
        return new PostCommentResponse(
                c.getId(), c.getPostId(), c.getUserId(), userName, c.getContent(), c.getCreatedAt()
        );
    }
}
