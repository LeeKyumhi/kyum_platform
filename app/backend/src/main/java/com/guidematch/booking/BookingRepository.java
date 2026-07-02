package com.guidematch.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 여행자가 보낸 예약들 (최신순)
    List<Booking> findByTravelerIdOrderByCreatedAtDesc(Long travelerId);

    // 가이드가 받은 예약들 (최신순)
    List<Booking> findByGuideProfileIdOrderByCreatedAtDesc(Long guideProfileId);

    // 가이드의 확정된 예약 수 (정렬용) — 수락됨/완료됨만 집계
    long countByGuideProfileIdAndStatusIn(Long guideProfileId, Collection<BookingStatus> statuses);
}
