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

    /**
     * 🧳 근거용 — [userId, placeRef] 쌍. 수가 아니라 <b>사람</b>을 돌려주는 이유는,
     * 같은 사람이 찜도 하고 일정에도 넣었을 때 두 출처의 수를 더하면 2명이 되기 때문이다.
     * 합집합은 호출부({@code TravelerSignalLookup})가 Java에서 만든다.
     */
    @Query("select s.userId, s.placeRef from SavedItem s where s.itemType = :type and s.placeRef in :refs")
    List<Object[]> userPlaceRefPairs(@Param("type") SavedItemType type,
                                     @Param("refs") Collection<String> placeRefs);

    // 여러 대상의 저장수를 한 번에 집계 (목록 화면 N+1 방지 — FollowRepository 패턴). 각 행: [refId, count]
    @Query("select s.refId, count(s) from SavedItem s where s.itemType = :type and s.refId in :ids group by s.refId")
    List<Object[]> countsByTypeAndRefIds(@Param("type") SavedItemType type, @Param("ids") Collection<Long> ids);
}
