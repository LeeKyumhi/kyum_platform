package com.guidematch.review;

import com.guidematch.review.dto.CreateReviewRequest;
import com.guidematch.review.dto.ReviewResponse;
import com.guidematch.review.dto.ReviewStatsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 리뷰 API.
 *  - 작성: POST /api/bookings/{bookingId}/review  (로그인 필요, 그 예약의 여행자만)
 *  - 조회: GET  /api/guides/{guideProfileId}/reviews  (공개)
 *  - 통계: GET  /api/guides/{guideProfileId}/review-stats  (공개 — 별점 분포 + 태그 집계)
 *  - 번역: GET  /api/reviews/{reviewId}/translate  (공개)
 */
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/bookings/{bookingId}/review")
    public ResponseEntity<ReviewResponse> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long bookingId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        ReviewResponse res = reviewService.create(userId, bookingId, request.rating(), request.comment(), request.tags());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/api/guides/{guideProfileId}/reviews")
    public List<ReviewResponse> listForGuide(@PathVariable Long guideProfileId) {
        return reviewService.listForGuide(guideProfileId);
    }

    /** 별점 분포 + 키워드 태그 집계 (공개, 로그인 불필요) */
    @GetMapping("/api/guides/{guideProfileId}/review-stats")
    public ReviewStatsResponse stats(@PathVariable Long guideProfileId) {
        return reviewService.stats(guideProfileId);
    }

    /** 리뷰 코멘트 번역 (공개 콘텐츠 — 로그인 불필요) */
    @GetMapping("/api/reviews/{reviewId}/translate")
    public java.util.Map<String, String> translate(
            @PathVariable Long reviewId,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        return java.util.Map.of("translated", reviewService.translate(reviewId, lang));
    }
}
