package com.guidematch.knowledge;

import java.util.Optional;

/**
 * 사실의 종류 — 열거형이어야 하는 이유는 질의 가능성이다.
 *
 * <p>자유 텍스트로 두면 "대기시간"·"waitTime"·"wait_time"이 뒤섞여 어떤 조회도 못 하게 되고,
 * 자산이 아니라 메모장이 된다. 추출기가 새 값을 발명하면 그 사실은 버린다.
 *
 * <p>{@code ENGLISH_MENU}·{@code FOREIGNER_FRIENDLY}는 다른 여행앱에는 없는 항목인데,
 * 우리 사용자가 방한 외국인이라 결정적이다. 그래서 처음부터 넣는다.
 */
public enum FactKind {
    BEST_SEASON,
    BEST_TIME,
    TYPICAL_DURATION_MIN,
    WAIT_TIME,
    PRICE,
    PHOTO_SPOT,
    RESERVATION_REQUIRED,
    ACCESS,
    CAUTION,
    CROWD_LEVEL,
    VIBE,
    PAIRS_WITH,
    ENGLISH_MENU,
    FOREIGNER_FRIENDLY;

    /** 계약 파일은 snake_case를 쓴다. 모르는 값이면 비어 있는 결과 — 호출부가 거절 처리한다. */
    public static Optional<FactKind> fromWire(String wire) {
        if (wire == null || wire.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(wire.trim().toUpperCase().replace('-', '_')));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String toWire() {
        return name().toLowerCase();
    }
}
