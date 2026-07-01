package com.guidematch.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByGuideProfileIdOrderByCreatedAtDesc(Long guideProfileId);

    boolean existsByBookingId(Long bookingId);

    long countByGuideProfileId(Long guideProfileId);

    // 평균 별점 (리뷰 없으면 0). @Query = SQL 비슷한 JPQL을 직접 작성.
    @Query("select coalesce(avg(r.rating), 0) from Review r where r.guideProfileId = :guideProfileId")
    double averageRatingByGuideProfileId(@Param("guideProfileId") Long guideProfileId);
}
