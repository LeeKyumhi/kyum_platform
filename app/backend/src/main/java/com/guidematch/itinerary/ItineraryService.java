package com.guidematch.itinerary;

import com.guidematch.itinerary.dto.CreateItineraryRequest;
import com.guidematch.itinerary.dto.ItineraryItemRequest;
import com.guidematch.itinerary.dto.ItineraryResponse;
import com.guidematch.itinerary.dto.ItinerarySummaryResponse;
import com.guidematch.itinerary.dto.UpdateItineraryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ItineraryService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryService.class);

    /** 예약 슬롯은 한국 기준(KST)이므로 예약 시각 → 일정 날짜 변환에 이 존을 사용한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ItineraryRepository itineraryRepository;

    public ItineraryService(ItineraryRepository itineraryRepository) {
        this.itineraryRepository = itineraryRepository;
    }

    /** 내 일정 목록 (최신순). */
    @Transactional(readOnly = true)
    public List<ItinerarySummaryResponse> listMine(Long ownerId) {
        return itineraryRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(ItinerarySummaryResponse::from).toList();
    }

    /** 새 일정 생성. */
    @Transactional
    public ItineraryResponse create(Long ownerId, CreateItineraryRequest req) {
        Itinerary it = new Itinerary(ownerId, req.title(), req.city(), req.startDate(), req.endDate());
        return ItineraryResponse.from(itineraryRepository.save(it));
    }

    /** 내 일정 상세. */
    @Transactional(readOnly = true)
    public ItineraryResponse get(Long ownerId, Long id) {
        return ItineraryResponse.from(findOwned(ownerId, id));
    }

    /** 일정 전체 저장 (메타 + 아이템 통째로 교체). */
    @Transactional
    public ItineraryResponse update(Long ownerId, Long id, UpdateItineraryRequest req) {
        Itinerary it = findOwned(ownerId, id);
        it.updateMeta(req.title(), req.city(), req.startDate(), req.endDate());
        it.replaceItems(toItems(req.items()));
        // 새 아이템의 DB id를 응답에 채우기 위해 즉시 flush (기본은 커밋 시점에 flush돼 id가 null로 나감)
        Itinerary saved = itineraryRepository.saveAndFlush(it);
        return ItineraryResponse.from(saved);
    }

    /** 일정 삭제. */
    @Transactional
    public void delete(Long ownerId, Long id) {
        Itinerary it = findOwned(ownerId, id);
        itineraryRepository.delete(it);
    }

    private Itinerary findOwned(Long ownerId, Long id) {
        Itinerary it = itineraryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));
        if (!it.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("본인의 일정만 접근할 수 있습니다.");
        }
        return it;
    }

    /**
     * 예약 확정(ACCEPTED) 시 여행자의 일정에 투어를 자동으로 담아준다.
     *
     * 반드시 REQUIRES_NEW로 별도 트랜잭션에서 실행한다: 이 메서드가 예약 트랜잭션과 같은
     * 트랜잭션에서 실패하면 (예: 제약조건 위반) JPA가 전체 트랜잭션을 rollback-only로 표시해
     * 호출부의 try/catch로 잡아도 예약 확정 자체가 커밋되지 않는 사고로 이어진다.
     * 별도 트랜잭션으로 분리해야 이 메서드의 실패가 예약 확정과 완전히 무관해진다.
     * 그래서 관리 엔티티(Booking/GuideProfile)가 아니라 원시값만 받는다 — 예약 쪽 트랜잭션이
     * 아직 커밋되지 않았을 수 있으므로, 그 엔티티를 이 새 트랜잭션에서 다시 읽으려 해선 안 된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoAddTourItem(Long travelerId, Long bookingId, Instant startAt,
                               String guideHeadline, String guideCity, String travelerNote) {
        try {
            LocalDate tourDate = startAt.atZone(KST).toLocalDate();

            Itinerary target = itineraryRepository.findByOwnerIdOrderByCreatedAtDesc(travelerId).stream()
                    .filter(it -> it.getStartDate() != null && it.getEndDate() != null)
                    .filter(it -> !tourDate.isBefore(it.getStartDate()) && !tourDate.isAfter(it.getEndDate()))
                    .findFirst()
                    .orElse(null);

            if (target != null) {
                boolean alreadyAdded = target.getItems().stream()
                        .anyMatch(i -> bookingId.equals(i.getSourceBookingId()));
                if (alreadyAdded) return;
            } else {
                String title = (guideHeadline != null && !guideHeadline.isBlank())
                        ? guideHeadline + " 투어" : "가이드 투어";
                target = itineraryRepository.save(new Itinerary(travelerId, title, guideCity, tourDate, tourDate));
            }

            int dayIndex = (int) ChronoUnit.DAYS.between(target.getStartDate(), tourDate) + 1;
            int nextSort = target.getItems().stream()
                    .filter(i -> i.getDayIndex() == dayIndex)
                    .mapToInt(ItineraryItem::getSortOrder)
                    .max().orElse(-1) + 1;

            ItineraryItem item = new ItineraryItem(dayIndex, nextSort, null, "🎫 가이드 투어",
                    "tour", null, null, null, travelerNote);
            item.setSourceBookingId(bookingId);
            target.getItems().add(item);
            itineraryRepository.save(target);
        } catch (Exception e) {
            // 트립 자동 추가는 부가 기능이다 — 실패해도 예약 확정 자체는 반드시 성공해야 한다.
            log.warn("예약 {}건의 일정 자동 추가 실패: {}", bookingId, e.toString());
        }
    }

    private List<ItineraryItem> toItems(List<ItineraryItemRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream().map(r -> {
            ItineraryItem item = new ItineraryItem(
                    r.dayIndex(), r.sortOrder(), r.placeId(), r.placeName(),
                    r.category(), r.address(), r.latitude(), r.longitude(), r.memo());
            item.setStartHour(r.startHour());
            item.setDurationHours(r.durationHours());
            item.setLaneIndex(r.laneIndex());
            item.setLaneSpan(r.laneSpan());
            item.setSourceCourseId(r.sourceCourseId());
            return item;
        }).toList();
    }
}
