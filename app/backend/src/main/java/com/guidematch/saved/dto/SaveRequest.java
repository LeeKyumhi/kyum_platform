package com.guidematch.saved.dto;

/** 찜 저장 입력. GUIDE/COURSE는 refId, PLACE는 place 스냅샷을 채운다. */
public record SaveRequest(
        String itemType,
        Long refId,
        PlaceSnapshot place
) {
    public record PlaceSnapshot(
            String ref,        // SPOTS slug 또는 "kakao:{placeId}"
            String name,
            String category,
            String address,
            Double lat,
            Double lng,
            String image
    ) {}
}
