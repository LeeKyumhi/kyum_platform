package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerUserIdAndGuideProfileId(Long followerUserId, Long guideProfileId);
    boolean existsByFollowerUserIdAndGuideProfileId(Long followerUserId, Long guideProfileId);
    long countByGuideProfileId(Long guideProfileId);
    List<Follow> findByFollowerUserIdOrderByCreatedAtDesc(Long followerUserId);
}
