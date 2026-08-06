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
        String sourceKind
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
