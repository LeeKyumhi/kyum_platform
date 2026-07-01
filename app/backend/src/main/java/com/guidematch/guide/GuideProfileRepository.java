package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuideProfileRepository extends JpaRepository<GuideProfile, Long> {

    Optional<GuideProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    // 활동 중인 가이드만 조회 (검색/목록용)
    List<GuideProfile> findByActiveTrue();

    // 활동 중 + 특정 지역 (대소문자 무시)
    List<GuideProfile> findByActiveTrueAndRegionIgnoreCase(String region);
}
