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

    // 여러 가이드의 평점 통계를 한 번에 집계 (목록 화면 N+1 방지).
    // 각 행: [guideProfileId, avg(rating), count(*)]
    @Query("select r.guideProfileId, avg(r.rating), count(r) from Review r " +
           "where r.guideProfileId in :ids group by r.guideProfileId")
    List<Object[]> ratingStatsByGuideProfileIds(@Param("ids") java.util.Collection<Long> ids);

    // 별점 분포 (1~5점별 개수)를 별당 개별 조회 없이 단일 GROUP BY로 집계.
    // 각 행: [rating, count(*)]
    @Query("select r.rating, count(r) from Review r where r.guideProfileId = :guideProfileId group by r.rating")
    List<Object[]> ratingDistributionByGuideProfileId(@Param("guideProfileId") Long guideProfileId);
}
