package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuidePostRepository extends JpaRepository<GuidePost, Long> {
    List<GuidePost> findByGuideProfileIdOrderByCreatedAtDesc(Long guideProfileId);

    List<GuidePost> findAllByOrderByCreatedAtDesc();

    // 조회수 원자적 증가 (read-modify-write 경쟁 방지)
    @Modifying
    @Query("UPDATE GuidePost p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
