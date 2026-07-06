package com.guidematch.guide.dto;

import com.guidematch.guide.TourCourse;

import java.time.Instant;
import java.util.List;

/**
 * 투어 코스 응답. 공개 목록에서는 가이드 이름/아바타를 함께 내려준다
 * (내 코스 목록에서는 가이드 필드가 null이어도 무방).
 * waypoints는 sortOrder 오름차순 (코스 상세 TripMap 핀+폴리라인용).
 */
public record TourCourseResponse(
        Long id,
        Long guideProfileId,
        String title,
        String description,
        String city,
        Integer durationHours,
        Integer price,
        String currency,
        Integer maxPeople,
        String imageUrl,
        boolean active,
        Instant createdAt,
        String guideName,
        String guideAvatarUrl,
        List<TourCourseWaypointResponse> waypoints
) {
    public static TourCourseResponse from(TourCourse c, String guideName, String guideAvatarUrl) {
        return new TourCourseResponse(
                c.getId(), c.getGuideProfileId(), c.getTitle(), c.getDescription(), c.getCity(),
                c.getDurationHours(), c.getPrice(), c.getCurrency(), c.getMaxPeople(),
                c.getImageUrl(), c.isActive(), c.getCreatedAt(), guideName, guideAvatarUrl,
                c.getWaypoints().stream().map(TourCourseWaypointResponse::from).toList()
        );
    }

    public static TourCourseResponse from(TourCourse c) {
        return from(c, null, null);
    }
}
