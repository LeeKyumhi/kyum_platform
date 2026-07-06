package com.guidematch.guide.dto;

import com.guidematch.guide.TourCourseWaypoint;

public record TourCourseWaypointResponse(
        Long id,
        int sortOrder,
        String placeId,
        String placeName,
        String category,
        String address,
        Double latitude,
        Double longitude
) {
    public static TourCourseWaypointResponse from(TourCourseWaypoint w) {
        return new TourCourseWaypointResponse(
                w.getId(), w.getSortOrder(), w.getPlaceId(), w.getPlaceName(),
                w.getCategory(), w.getAddress(), w.getLatitude(), w.getLongitude()
        );
    }
}
