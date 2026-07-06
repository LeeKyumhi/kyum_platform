package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AvailableSlotRepository extends JpaRepository<AvailableSlot, Long> {
    /** 특정 가이드의 지정 일시 이후 슬롯을 시작 시간 순으로 반환 */
    List<AvailableSlot> findByGuideProfileIdAndStartAtAfterOrderByStartAtAsc(Long guideProfileId, LocalDateTime after);

    /** 지정 기간에 가능 슬롯이 하나라도 있는 가이드 프로필 id 목록 (날짜 기반 가이드 검색용) */
    @Query("select distinct s.guideProfileId from AvailableSlot s where s.startAt >= :from and s.startAt < :to")
    List<Long> guideProfileIdsWithSlotBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
