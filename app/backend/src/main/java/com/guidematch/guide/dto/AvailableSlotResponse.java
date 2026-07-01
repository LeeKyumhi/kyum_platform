package com.guidematch.guide.dto;

import com.guidematch.guide.AvailableSlot;
import java.time.LocalDateTime;

public record AvailableSlotResponse(
        Long id,
        Long guideProfileId,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static AvailableSlotResponse from(AvailableSlot s) {
        return new AvailableSlotResponse(s.getId(), s.getGuideProfileId(), s.getStartAt(), s.getEndAt());
    }
}
