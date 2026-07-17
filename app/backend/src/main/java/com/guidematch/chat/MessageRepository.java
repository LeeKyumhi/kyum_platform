package com.guidematch.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 한 예약의 메시지를 오래된 순으로 (대화 흐름대로)
    List<Message> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    // 여러 예약의 메시지를 한 번에 (통합 인박스에서 예약별 마지막 메시지 계산용, N+1 방지).
    // 오래된 순이라 booking별로 마지막 원소가 최신 메시지가 된다.
    List<Message> findByBookingIdInOrderByCreatedAtAsc(Collection<Long> bookingIds);

    /**
     * 내가 아직 안 읽은 예약 채팅 메시지 총 개수 — 통합 인박스 배지용, 원격 DB라 한 번의 집계로.
     * 내가 여행자(travelerId)이거나 내 가이드 프로필(guideProfileId)의 예약이고,
     * 상대가 보낸 메시지이며, 내 마지막 읽은 시각 이후(또는 한 번도 안 읽음)인 것만.
     * guideProfileId가 null(가이드 아님)이면 가이드 분기는 매칭되지 않는다.
     * 예약 채팅은 차단(block) enforce 대상이 아니므로(Wave 5 결정) 차단 서브쿼리는 두지 않는다.
     */
    @Query("""
            select count(m) from Message m, com.guidematch.booking.Booking b
            where m.bookingId = b.id
              and m.senderId <> :userId
              and (
                (b.travelerId = :userId and (b.travelerLastReadAt is null or m.createdAt > b.travelerLastReadAt))
                or
                (:guideProfileId is not null and b.guideProfileId = :guideProfileId
                    and (b.guideLastReadAt is null or m.createdAt > b.guideLastReadAt))
              )
            """)
    long unreadCount(@Param("userId") Long userId, @Param("guideProfileId") Long guideProfileId);

    /** 내가 안 읽은 메시지가 있는 예약(booking) id 목록 (#2 인박스 점 표시). unreadCount와 동일 술어. */
    @Query("""
            select distinct m.bookingId from Message m, com.guidematch.booking.Booking b
            where m.bookingId = b.id
              and m.senderId <> :userId
              and (
                (b.travelerId = :userId and (b.travelerLastReadAt is null or m.createdAt > b.travelerLastReadAt))
                or
                (:guideProfileId is not null and b.guideProfileId = :guideProfileId
                    and (b.guideLastReadAt is null or m.createdAt > b.guideLastReadAt))
              )
            """)
    List<Long> bookingIdsWithUnread(@Param("userId") Long userId, @Param("guideProfileId") Long guideProfileId);
}
