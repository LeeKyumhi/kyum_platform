package com.guidematch.itinerary.dto;

/**
 * 일정 저장 시 아이템 하나의 입력.
 * 프론트가 Kakao 장소를 담을 때/직접 만들 때 모두 이 형태로 보낸다.
 */
public record ItineraryItemRequest(
        int dayIndex,
        int sortOrder,
        String placeId,
        String placeName,
        String category,
        String address,
        Double latitude,
        Double longitude,
        String memo,
        // 타임테이블 배치 (nullable — 미지정 시 시간 없는 자유 아이템)
        Integer startHour,
        Integer durationHours,
        Integer laneIndex,
        Integer laneSpan,
        Long sourceCourseId
) {}
