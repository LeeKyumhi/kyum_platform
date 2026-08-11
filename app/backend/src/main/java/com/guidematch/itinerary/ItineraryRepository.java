package com.guidematch.itinerary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {
    List<Itinerary> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /**
     * 🧳 근거용 — [ownerId, placeId] 쌍. 찜과 합쳐 <b>사람 단위</b>로 세기 위해 수가 아니라 쌍을 돌려준다.
     * {@code items}는 단방향 @OneToMany라 아이템에서 일정으로 거슬러 올라갈 수 없어 컬렉션 방향으로 조인한다.
     */
    @Query("select i.ownerId, it.placeId from Itinerary i join i.items it where it.placeId in :placeIds")
    List<Object[]> ownerPlaceIdPairs(@Param("placeIds") Collection<String> placeIds);
}
