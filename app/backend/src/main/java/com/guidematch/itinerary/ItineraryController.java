package com.guidematch.itinerary;

import com.guidematch.itinerary.dto.CreateItineraryRequest;
import com.guidematch.itinerary.dto.ItineraryResponse;
import com.guidematch.itinerary.dto.ItinerarySummaryResponse;
import com.guidematch.itinerary.dto.UpdateItineraryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내 여행 일정 CRUD (모두 로그인 필요, 소유자 본인만).
 * 여행자·가이드 모두 사용. 저장은 PUT으로 메타+아이템을 통째로 교체한다.
 */
@RestController
@RequestMapping("/api/itineraries/me")
public class ItineraryController {

    private final ItineraryService itineraryService;

    public ItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @GetMapping
    public List<ItinerarySummaryResponse> list(@AuthenticationPrincipal Long userId) {
        return itineraryService.listMine(userId);
    }

    @PostMapping
    public ResponseEntity<ItineraryResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateItineraryRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itineraryService.create(userId, req));
    }

    @GetMapping("/{id}")
    public ItineraryResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return itineraryService.get(userId, id);
    }

    @PutMapping("/{id}")
    public ItineraryResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateItineraryRequest req
    ) {
        return itineraryService.update(userId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        itineraryService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
