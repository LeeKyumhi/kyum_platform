package com.guidematch.safety;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    boolean existsByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    /** 내가 차단한 사용자 목록 (차단 관리 화면용) — 최근 차단 순 */
    List<Block> findByBlockerUserIdOrderByCreatedAtDesc(Long blockerUserId);

    /**
     * 나와 차단 관계에 있는 상대 userId 전부 — 내가 차단했거나, 나를 차단한 사용자.
     * 목록/집계에서 상호 숨김에 쓰는 단일 배치 조회(원격 DB N+1 방지).
     */
    @Query("""
            select case when b.blockerUserId = :userId then b.blockedUserId else b.blockerUserId end
            from Block b
            where b.blockerUserId = :userId or b.blockedUserId = :userId
            """)
    List<Long> relatedUserIds(@Param("userId") Long userId);

    /** 두 사용자 사이에 (어느 방향으로든) 차단이 있는가 — 단건 검사(DM 시작/전송 시). */
    @Query("""
            select count(b) > 0 from Block b
            where (b.blockerUserId = :a and b.blockedUserId = :b)
               or (b.blockerUserId = :b and b.blockedUserId = :a)
            """)
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);
}
