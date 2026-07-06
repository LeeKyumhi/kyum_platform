package com.guidematch.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateReviewRequest(

        @NotNull(message = "별점은 필수입니다.")
        @Min(value = 1, message = "별점은 1 이상이어야 합니다.")
        @Max(value = 5, message = "별점은 5 이하여야 합니다.")
        Integer rating,

        String comment,

        /** 선택. Review.CANONICAL_TAG_KEYS에 없는 값은 서버가 조용히 무시한다. */
        List<String> tags
) {
}
