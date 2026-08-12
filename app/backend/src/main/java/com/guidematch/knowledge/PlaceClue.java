package com.guidematch.knowledge;

import java.util.List;

/**
 * 추출기가 보내는 장소 <b>단서</b> — 결론이 아니다.
 *
 * <p>Codex는 "이게 어느 장소인지"를 판정하지 않는다. 이름·좌표·외부ID 같은 관측값만 낸다.
 * 병합 판정은 {@link PlaceResolver}만 한다. 추출기가 합치면 잘못된 병합을 되돌릴 수 없다.
 */
public record PlaceClue(
        String nameRaw,
        List<String> aliases,
        String city,
        String district,
        Double lat,
        Double lng,
        String kakaoPlaceId,
        String tourApiContentId,
        String category,
        /**
         * 한국어 주소 원문. 계약(place.schema.json)에는 1일차부터 있었는데 이 필드가 없어
         * 조용히 버려져 왔다 — 실제 run 디렉터리의 JSONL에는 53/53건 전부 들어 있다.
         * 번역하지 않는다: 택시·지도 앱에 그대로 넣을 수 있어야 한다.
         */
        String addressRaw,
        String sourceKind,
        /** TourAPI firstimage. 발행처는 source.publisher에서 온다. */
        String imageUrl,
        /** 발행처. 없으면 {@link Place#applyImage}가 사진을 버린다 — 출처 없이는 띄울 수 없다. */
        String imagePublisher
) {
    public PlaceClue {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public boolean hasCoordinates() {
        return lat != null && lng != null;
    }

    /** 외부 ID가 있는 소스만 새 장소 노드를 만들 자격이 있다. */
    public boolean hasExternalId() {
        return notBlank(kakaoPlaceId) || notBlank(tourApiContentId);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
