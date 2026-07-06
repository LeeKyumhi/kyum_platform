package com.guidematch.review.dto;

import com.guidematch.review.Review;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record ReviewResponse(
        Long id,
        String reviewerName,
        Integer rating,
        String comment,
        List<String> tags,
        Instant createdAt
) {
    public static ReviewResponse of(Review r, String reviewerName) {
        List<String> tags = (r.getTags() == null || r.getTags().isBlank())
                ? List.of()
                : Arrays.stream(r.getTags().split(",")).filter(t -> !t.isBlank()).toList();
        return new ReviewResponse(r.getId(), reviewerName, r.getRating(), r.getComment(), tags, r.getCreatedAt());
    }
}
