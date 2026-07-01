package com.guidematch.review;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.review.dto.ReviewResponse;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         BookingRepository bookingRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    /** 여행자가 완료된 예약에 리뷰 작성 */
    @Transactional
    public ReviewResponse create(Long travelerId, Long bookingId, Integer rating, String comment) {
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

        Review review = new Review(bookingId, booking.getGuideProfileId(), travelerId, rating, comment);
        Review saved = reviewRepository.save(review);
        return toResponse(saved);
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

    private ReviewResponse toResponse(Review r) {
        String reviewerName = userRepository.findById(r.getReviewerId())
                .map(User::getFullName).orElse("알 수 없음");
        return ReviewResponse.of(r, reviewerName);
    }
}
