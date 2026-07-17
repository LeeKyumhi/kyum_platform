package com.guidematch.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByTravelerUserIdAndGuideProfileId(Long travelerUserId, Long guideProfileId);

    /** 내가 어느 쪽으로든 참여한 대화 전부 — 최근 메시지 순 (메시지 없는 방은 뒤로) */
    @Query("""
            select c from Conversation c
            where c.travelerUserId = :userId or c.guideUserId = :userId
            order by coalesce(c.lastMessageAt, c.createdAt) desc
            """)
    List<Conversation> findMine(@Param("userId") Long userId);

    /**
     * 내가 아직 안 읽은 DM 메시지 총 개수 — 사이드바 배지용, 원격 DB라 한 번의 집계로.
     * 내가 참여자이고, 상대가 보낸 메시지이며, 내 마지막 읽은 시각 이후(또는 한 번도 안 읽음)인 것만.
     */
    @Query("""
            select count(m) from ConversationMessage m, Conversation c
            where m.conversationId = c.id
              and m.senderId <> :userId
              and (
                (c.travelerUserId = :userId and (c.travelerLastReadAt is null or m.createdAt > c.travelerLastReadAt))
                or
                (c.guideUserId = :userId and (c.guideLastReadAt is null or m.createdAt > c.guideLastReadAt))
              )
              and not exists (
                select 1 from com.guidematch.safety.Block bl
                where (bl.blockerUserId = :userId and bl.blockedUserId = m.senderId)
                   or (bl.blockerUserId = m.senderId and bl.blockedUserId = :userId)
              )
            """)
    long unreadCount(@Param("userId") Long userId);

    /**
     * 내가 안 읽은 메시지가 있는 대화방 id 목록 (#2 인박스 점 표시). unreadCount와 동일 술어(차단 제외 포함)라
     * 사이드바 총 배지와 어긋나지 않는다.
     */
    @Query("""
            select distinct c.id from ConversationMessage m, Conversation c
            where m.conversationId = c.id
              and m.senderId <> :userId
              and (
                (c.travelerUserId = :userId and (c.travelerLastReadAt is null or m.createdAt > c.travelerLastReadAt))
                or
                (c.guideUserId = :userId and (c.guideLastReadAt is null or m.createdAt > c.guideLastReadAt))
              )
              and not exists (
                select 1 from com.guidematch.safety.Block bl
                where (bl.blockerUserId = :userId and bl.blockedUserId = m.senderId)
                   or (bl.blockerUserId = m.senderId and bl.blockedUserId = :userId)
              )
            """)
    List<Long> conversationIdsWithUnread(@Param("userId") Long userId);
}
