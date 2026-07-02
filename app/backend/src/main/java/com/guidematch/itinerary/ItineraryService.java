package com.guidematch.itinerary;

import com.guidematch.itinerary.dto.CreateItineraryRequest;
import com.guidematch.itinerary.dto.ItineraryItemRequest;
import com.guidematch.itinerary.dto.ItineraryResponse;
import com.guidematch.itinerary.dto.ItinerarySummaryResponse;
import com.guidematch.itinerary.dto.UpdateItineraryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItineraryService {

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

    private List<ItineraryItem> toItems(List<ItineraryItemRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream().map(r -> new ItineraryItem(
                r.dayIndex(), r.sortOrder(), r.placeId(), r.placeName(),
                r.category(), r.address(), r.latitude(), r.longitude(), r.memo()
        )).toList();
    }
}
