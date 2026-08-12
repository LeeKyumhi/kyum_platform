package com.guidematch.geo;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 장소 목록을 "추천순"으로 세운다.
 *
 * <p><b>왜 점수 합산이 아니라 계단식 티어인가</b>: 근거 데이터가 거의 없다. 가중치 합산을 하면
 * 대부분의 장소가 0점이라 사실상 Kakao 순서인데 가중치만 만지는 상태가 되고, 왜 이 장소가
 * 위에 있는지 설명할 수도 없다. 티어는 "위에 있는 이유"가 화면의 근거 배지와 정확히 일치한다 —
 * 순서와 설명이 같은 규칙에서 나오므로 서로 어긋날 수 없다.
 *
 * <p>티어 안에서는 <b>입력 순서(Kakao가 준 거리·정확도 순)를 그대로 유지</b>한다.
 * 우리가 근거를 못 대는 구간에서는 그게 최선의 기본값이고, 순서가 흔들리면 같은 검색을 두 번 했을 때
 * 목록이 달라져 사용자가 방금 본 장소를 못 찾는다.
 */
public final class PlaceRanking {

    private PlaceRanking() {}

    /**
     * 한 장소의 근거 신호.
     *
     * @param verifiedGuideCount 이 장소를 활성 코스에 담은 인증 가이드 수
     * @param travelerCount      찜·일정에 담은 여행자 수 (사람 단위 합집합)
     * @param hasOfficialSource  발행처를 밝힐 수 있는 공공기관 자료가 있는가
     */
    public record Signals(int verifiedGuideCount, int travelerCount, boolean hasOfficialSource) {

        /** 티어: 3=🎫, 2=🧳, 1=🏛, 0=근거 없음. {@code CourseReasons}의 서열과 같아야 한다. */
        int tier() {
            if (verifiedGuideCount > 0) return 3;
            if (travelerCount >= CourseReasons.MIN_TRAVELERS) return 2;
            if (hasOfficialSource) return 1;
            return 0;
        }

        /** 같은 티어 안에서의 크기 — 티어를 만든 수가 클수록 앞. */
        int magnitude() {
            if (verifiedGuideCount > 0) return verifiedGuideCount;
            if (travelerCount >= CourseReasons.MIN_TRAVELERS) return travelerCount;
            return 0;
        }
    }

    /**
     * @param ids     Kakao가 준 순서 그대로의 장소 id
     * @param signals id → 신호
     * @return 추천순으로 재배열된 id. <b>안정 정렬</b>이라 동점은 원래 순서를 지킨다.
     */
    public static <T> List<T> sort(List<T> ids, Function<T, Signals> signals) {
        return ids.stream()
                .sorted(Comparator
                        .comparingInt((T id) -> signals.apply(id).tier()).reversed()
                        .thenComparing(Comparator.comparingInt((T id) -> signals.apply(id).magnitude()).reversed()))
                .toList();
    }
}
