package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuideProfileRepository extends JpaRepository<GuideProfile, Long> {

    Optional<GuideProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    // 활동 중인 가이드만 조회 (검색/목록용)
    List<GuideProfile> findByActiveTrue();

    // 활동 중 + 특정 지역 (대소문자 무시)
    List<GuideProfile> findByActiveTrueAndRegionIgnoreCase(String region);

    // 목록용: languages를 fetch join으로 함께 로딩 (가이드별 lazy 조회 N+1 방지)
    @Query("select distinct p from GuideProfile p left join fetch p.languages where p.active = true")
    List<GuideProfile> findActiveWithLanguages();

    @Query("select distinct p from GuideProfile p left join fetch p.languages " +
           "where p.active = true and lower(p.region) = lower(:region)")
    List<GuideProfile> findActiveWithLanguagesByRegion(@Param("region") String region);

    // 피드용: 특정 id 목록을 languages와 함께 일괄 로딩
    @Query("select distinct p from GuideProfile p left join fetch p.languages where p.id in :ids")
    List<GuideProfile> findAllWithLanguagesByIdIn(@Param("ids") Collection<Long> ids);
}
