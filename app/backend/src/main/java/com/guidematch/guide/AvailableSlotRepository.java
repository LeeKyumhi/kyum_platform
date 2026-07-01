package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface AvailableSlotRepository extends JpaRepository<AvailableSlot, Long> {
    /** 특정 가이드의 지정 일시 이후 슬롯을 시작 시간 순으로 반환 */
    List<AvailableSlot> findByGuideProfileIdAndStartAtAfterOrderByStartAtAsc(Long guideProfileId, LocalDateTime after);
}
