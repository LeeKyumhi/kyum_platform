package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuidePostRepository extends JpaRepository<GuidePost, Long> {
    List<GuidePost> findByGuideProfileIdOrderByCreatedAtDesc(Long guideProfileId);

    List<GuidePost> findAllByOrderByCreatedAtDesc();
}
