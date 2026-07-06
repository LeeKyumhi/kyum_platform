package com.guidematch.review;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.geo.GoogleTranslateClient;
import com.guidematch.review.dto.ReviewResponse;
import com.guidematch.review.dto.ReviewStatsResponse;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final GoogleTranslateClient googleClient;

    public ReviewService(ReviewRepository reviewRepository,
                         BookingRepository bookingRepository,
                         UserRepository userRepository,
                         GoogleTranslateClient googleClient) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.googleClient = googleClient;
    }

    /** 여행자가 완료된 예약에 리뷰 작성 */
    @Transactional
    public ReviewResponse create(Long travelerId, Long bookingId, Integer rating, String comment, List<String> tags) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        // 리뷰는 그 예약의 여행자만
        if (!booking.getTravelerId().equals(travelerId)) {
            throw new IllegalArgumentException("본인의 예약만 리뷰할 수 있습니다.");
        }
        // 완료된 예약만 리뷰 가능
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("완료된 예약만 리뷰할 수 있습니다.");
        }
        // 중복 방지
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new IllegalArgumentException("이미 리뷰를 작성했습니다.");
        }

        String tagsCsv = toTagsCsv(tags);
        Review review = new Review(bookingId, booking.getGuideProfileId(), travelerId, rating, comment, tagsCsv);
        Review saved = reviewRepository.save(review);
        return toResponse(saved);
    }

    /** 알 수 없는 태그 key는 조용히 걸러내고, canonical key만 콤마로 이어붙인다. */
    private String toTagsCsv(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        String csv = tags.stream()
                .filter(t -> t != null && Review.CANONICAL_TAG_KEYS.contains(t))
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
        return (csv == null || csv.isBlank()) ? null : csv;
    }

    /** 가이드의 리뷰 목록 (공개) */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listForGuide(Long guideProfileId) {
        return reviewRepository.findByGuideProfileIdOrderByCreatedAtDesc(guideProfileId)
                .stream().map(this::toResponse).toList();
    }

    /** 평균 별점 */
    @Transactional(readOnly = true)
    public double averageRating(Long guideProfileId) {
        return reviewRepository.averageRatingByGuideProfileId(guideProfileId);
    }

    /** 리뷰 개수 */
    @Transactional(readOnly = true)
    public long reviewCount(Long guideProfileId) {
        return reviewRepository.countByGuideProfileId(guideProfileId);
    }

    /**
     * 별점 분포 + 키워드 태그 집계 (공개, N+1 방지).
     * 별점 분포는 단일 GROUP BY 쿼리로, 태그 집계는 findByGuideProfileId 한 번 조회 후
     * 메모리에서 태그별로 합산한다(가이드당 리뷰 수는 유한하므로 태그별 개별 쿼리는 하지 않는다).
     */
    @Transactional(readOnly = true)
    public ReviewStatsResponse stats(Long guideProfileId) {
        List<Object[]> rows = reviewRepository.ratingDistributionByGuideProfileId(guideProfileId);

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) distribution.put(String.valueOf(star), 0L);

        long count = 0;
        double weightedSum = 0;
        for (Object[] row : rows) {
            Integer rating = (Integer) row[0];
            Long ratingCount = (Long) row[1];
            distribution.put(String.valueOf(rating), ratingCount);
            count += ratingCount;
            weightedSum += rating * ratingCount;
        }
        double average = count > 0 ? weightedSum / count : 0.0;

        Map<String, Long> tagCounts = new LinkedHashMap<>();
        for (String key : Review.CANONICAL_TAG_KEYS) tagCounts.put(key, 0L);
        List<Review> reviews = reviewRepository.findByGuideProfileIdOrderByCreatedAtDesc(guideProfileId);
        for (Review r : reviews) {
            if (r.getTags() == null || r.getTags().isBlank()) continue;
            for (String tag : r.getTags().split(",")) {
                tagCounts.computeIfPresent(tag, (k, v) -> v + 1);
            }
        }

        return new ReviewStatsResponse(average, count, distribution, tagCounts);
    }

    /**
     * 리뷰 코멘트를 지정 언어로 번역 (공개 콘텐츠 — 참여자 검증 불필요).
     * 리뷰는 대부분 고유 문장이라 채팅과 동일하게 DB 캐시 없이 Google API를 직접 호출한다.
     */
    @Transactional(readOnly = true)
    public String translate(Long reviewId, String uiLang) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        String comment = review.getComment();
        if (comment == null || comment.isBlank()) {
            return comment == null ? "" : comment;
        }
        if (!googleClient.isEnabled()) {
            throw new IllegalArgumentException("번역 기능을 사용할 수 없습니다.");
        }

        String target = switch (uiLang == null ? "ko" : uiLang) {
            case "en" -> "en";
            case "zh" -> "zh-CN";
            default   -> "ko";
        };

        List<String> result = googleClient.translate(List.of(comment), null, target);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("번역에 실패했습니다.");
        }
        return result.get(0);
    }

    private ReviewResponse toResponse(Review r) {
        String reviewerName = userRepository.findById(r.getReviewerId())
                .map(User::getFullName).orElse("알 수 없음");
        return ReviewResponse.of(r, reviewerName);
    }
}
