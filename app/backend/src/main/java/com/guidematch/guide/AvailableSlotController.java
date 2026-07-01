package com.guidematch.guide;

import com.guidematch.guide.dto.AvailableSlotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class AvailableSlotController {

    private final AvailableSlotService slotService;

    public AvailableSlotController(AvailableSlotService slotService) {
        this.slotService = slotService;
    }

    /** 특정 가이드의 예약 가능한 슬롯 목록 (공개) */
    @GetMapping("/api/guides/{guideProfileId}/slots")
    public List<AvailableSlotResponse> list(@PathVariable Long guideProfileId) {
        return slotService.listForGuide(guideProfileId);
    }

    /** 내 슬롯 추가 */
    @PostMapping("/api/guide-profiles/me/slots")
    public ResponseEntity<AvailableSlotResponse> add(
            @AuthenticationPrincipal Long userId,
            @RequestBody SlotRequest req
    ) {
        AvailableSlotResponse slot = slotService.addSlot(userId, req.startAt(), req.endAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(slot);
    }

    /** 내 슬롯 삭제 */
    @DeleteMapping("/api/guide-profiles/me/slots/{slotId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long slotId
    ) {
        slotService.deleteSlot(userId, slotId);
        return ResponseEntity.noContent().build();
    }

    record SlotRequest(LocalDateTime startAt, LocalDateTime endAt) {}
}
