package com.guidematch.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 여행자가 보낸 예약들 (최신순)
    List<Booking> findByTravelerIdOrderByCreatedAtDesc(Long travelerId);

    // 가이드가 받은 예약들 (최신순)
    List<Booking> findByGuideProfileIdOrderByCreatedAtDesc(Long guideProfileId);

    // 가이드의 확정된 예약 수 (정렬용) — 수락됨/완료됨만 집계
    long countByGuideProfileIdAndStatusIn(Long guideProfileId, Collection<BookingStatus> statuses);

    // 여행자의 미확인 거절 예약 수 (#4 배지). RejectionSeenFalse = false만 매칭(null=옛 예약 제외)
    long countByTravelerIdAndStatusAndRejectionSeenFalse(Long travelerId, BookingStatus status);

    // 특정 상태의 예약들 (수락 시 시간 겹침 검사용)
    List<Booking> findByGuideProfileIdAndStatus(Long guideProfileId, BookingStatus status);

    // 여러 가이드의 확정 예약 수를 한 번에 집계 (목록 화면 N+1 방지). 각 행: [guideProfileId, count]
    @Query("select b.guideProfileId, count(b) from Booking b " +
           "where b.guideProfileId in :ids and b.status in :statuses group by b.guideProfileId")
    List<Object[]> bookingCountsByGuideProfileIds(@Param("ids") Collection<Long> ids,
                                                  @Param("statuses") Collection<BookingStatus> statuses);

    // 어드민 대시보드: 상태별 예약 수 (count 쿼리, N+1 없음)
    long countByStatus(BookingStatus status);

    // 어드민 예약 조회: 상태 필터 + 전체, 최신순 페이지네이션
    Page<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);
    Page<Booking> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
