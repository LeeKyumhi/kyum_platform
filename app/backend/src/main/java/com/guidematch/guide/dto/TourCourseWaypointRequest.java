package com.guidematch.guide.dto;

/**
 * 코스 등록/수정 시 동선 장소 하나의 입력.
 * multipart 폼의 "waypoints" 파라미터에 JSON 배열 문자열로 담아 보낸다
 * (예: [{"sortOrder":0,"placeName":"성수 어니언","latitude":37.5,"longitude":127.05}, ...]).
 */
public record TourCourseWaypointRequest(
        int sortOrder,
        String placeId,
        String placeName,
        String category,
        String address,
        Double latitude,
        Double longitude
) {}
