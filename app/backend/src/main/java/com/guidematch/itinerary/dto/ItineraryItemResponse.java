package com.guidematch.itinerary.dto;

import com.guidematch.itinerary.ItineraryItem;

public record ItineraryItemResponse(
        Long id,
        int dayIndex,
        int sortOrder,
        String placeId,
        String placeName,
        String category,
        String address,
        Double latitude,
        Double longitude,
        String memo
) {
    public static ItineraryItemResponse from(ItineraryItem i) {
        return new ItineraryItemResponse(
                i.getId(), i.getDayIndex(), i.getSortOrder(), i.getPlaceId(), i.getPlaceName(),
                i.getCategory(), i.getAddress(), i.getLatitude(), i.getLongitude(), i.getMemo()
        );
    }
}
