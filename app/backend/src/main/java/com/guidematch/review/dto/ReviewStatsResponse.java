package com.guidematch.review.dto;

import java.util.Map;

/**
 * 가이드 리뷰 통계 (공개).
 * distribution: 별점("5".."1") → 개수, 리뷰가 없는 별점도 0으로 채워진다.
 * tagCounts: canonical 태그 key(Review.CANONICAL_TAG_KEYS) → 태그가 달린 리뷰 수, 모든 key가 항상 포함된다.
 */
public record ReviewStatsResponse(
        double average,
        long count,
        Map<String, Long> distribution,
        Map<String, Long> tagCounts
) {}
