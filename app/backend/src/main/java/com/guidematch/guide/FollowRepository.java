package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerUserIdAndGuideProfileId(Long followerUserId, Long guideProfileId);
    boolean existsByFollowerUserIdAndGuideProfileId(Long followerUserId, Long guideProfileId);
    long countByGuideProfileId(Long guideProfileId);
    List<Follow> findByFollowerUserIdOrderByCreatedAtDesc(Long followerUserId);

    // 여러 가이드의 팔로워 수를 한 번에 집계 (목록 화면 N+1 방지). 각 행: [guideProfileId, count]
    @Query("select f.guideProfileId, count(f) from Follow f where f.guideProfileId in :ids group by f.guideProfileId")
    List<Object[]> followerCountsByGuideProfileIds(@Param("ids") Collection<Long> ids);
}
