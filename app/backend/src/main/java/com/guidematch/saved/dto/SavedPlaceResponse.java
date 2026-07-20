package com.guidematch.saved.dto;

import com.guidematch.saved.SavedItem;

import java.time.Instant;

/** 저장된 장소 (저장 시점 스냅샷 그대로). */
public record SavedPlaceResponse(
        String placeRef,
        String name,
        String category,
        String address,
        Double latitude,
        Double longitude,
        String image,
        Instant createdAt
) {
    public static SavedPlaceResponse from(SavedItem s) {
        return new SavedPlaceResponse(s.getPlaceRef(), s.getPlaceName(), s.getPlaceCategory(),
                s.getPlaceAddress(), s.getPlaceLat(), s.getPlaceLng(), s.getPlaceImage(), s.getCreatedAt());
    }
}
