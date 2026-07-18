package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuidePostRepository extends JpaRepository<GuidePost, Long> {
    List<GuidePost> findByGuideProfileIdOrderByCreatedAtDesc(Long guideProfileId);

    List<GuidePost> findAllByOrderByCreatedAtDesc();

    // 공개 피드용 — 숨김(hidden=true) 제외. null은 노출(기존 행 안전).
    @Query("SELECT p FROM GuidePost p WHERE p.hidden IS NULL OR p.hidden = false ORDER BY p.createdAt DESC")
    List<GuidePost> findVisibleOrderByCreatedAtDesc();

    // 내 게시글 전체 (author_user_id로 직접 작성한 것 + 과거 guide_profile 경유로 작성된 레거시 행 모두 포함)
    @Query("SELECT p FROM GuidePost p WHERE p.authorUserId = :userId " +
           "OR p.guideProfileId IN (SELECT gp.id FROM GuideProfile gp WHERE gp.userId = :userId) " +
           "ORDER BY p.createdAt DESC")
    List<GuidePost> findAllByAuthor(@Param("userId") Long userId);

    // 조회수 원자적 증가 (read-modify-write 경쟁 방지)
    @Modifying
    @Query("UPDATE GuidePost p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
