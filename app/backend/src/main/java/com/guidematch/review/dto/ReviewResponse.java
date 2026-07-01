package com.guidematch.review.dto;

import com.guidematch.review.Review;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        String reviewerName,
        Integer rating,
        String comment,
        Instant createdAt
) {
    public static ReviewResponse of(Review r, String reviewerName) {
        return new ReviewResponse(r.getId(), reviewerName, r.getRating(), r.getComment(), r.getCreatedAt());
    }
}
