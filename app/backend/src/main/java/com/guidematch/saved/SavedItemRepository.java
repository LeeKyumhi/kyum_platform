package com.guidematch.saved;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SavedItemRepository extends JpaRepository<SavedItem, Long> {
    Optional<SavedItem> findByUserIdAndItemTypeAndRefId(Long userId, SavedItemType itemType, Long refId);
    Optional<SavedItem> findByUserIdAndItemTypeAndPlaceRef(Long userId, SavedItemType itemType, String placeRef);
    boolean existsByUserIdAndItemTypeAndRefId(Long userId, SavedItemType itemType, Long refId);
    boolean existsByUserIdAndItemTypeAndPlaceRef(Long userId, SavedItemType itemType, String placeRef);
    List<SavedItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 여러 대상의 저장수를 한 번에 집계 (목록 화면 N+1 방지 — FollowRepository 패턴). 각 행: [refId, count]
    @Query("select s.refId, count(s) from SavedItem s where s.itemType = :type and s.refId in :ids group by s.refId")
    List<Object[]> countsByTypeAndRefIds(@Param("type") SavedItemType type, @Param("ids") Collection<Long> ids);
}
