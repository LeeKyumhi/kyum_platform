package com.guidematch.geo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소 목록의 "추천순" 정렬.
 *
 * <p>계단식 티어다 — 점수를 합산하지 않는다. 근거 데이터가 거의 없는 지금 가중치 합산을 하면
 * 대부분 0점이 되어 사실상 Kakao 순서인데 가중치만 튜닝하는 상태가 된다.
 * 티어는 "왜 위에 있는가"가 화면의 이유 배지와 정확히 일치한다는 장점도 있다.
 *
 * <p>티어 안에서는 <b>Kakao가 준 순서를 그대로 유지</b>한다. Kakao 순서는 거리·정확도 기반이라
 * 우리가 근거를 못 대는 구간에서는 그게 최선의 기본값이다.
 */
class PlaceRankingTest {

    private List<String> sorted(List<String> ids, Map<String, Integer> guides,
                                Map<String, Integer> travelers, java.util.Set<String> withOfficial) {
        return PlaceRanking.sort(ids, id -> new PlaceRanking.Signals(
                guides.getOrDefault(id, 0),
                travelers.getOrDefault(id, 0),
                withOfficial.contains(id)));
    }

    @Test
    void 인증_가이드가_담은_장소가_가장_앞에_온다() {
        List<String> out = sorted(List.of("a", "b", "c"),
                Map.of("c", 1), Map.of("b", 5), java.util.Set.of("a"));

        assertThat(out).containsExactly("c", "b", "a");
    }

    @Test
    void 여행자가_담은_장소가_공공기관_자료만_있는_장소보다_앞이다() {
        List<String> out = sorted(List.of("a", "b"), Map.of(), Map.of("b", 3), java.util.Set.of("a"));

        assertThat(out).containsExactly("b", "a");
    }

    @Test
    void 근거가_없는_장소는_맨_뒤로_간다() {
        List<String> out = sorted(List.of("plain", "official"),
                Map.of(), Map.of(), java.util.Set.of("official"));

        assertThat(out).containsExactly("official", "plain");
    }

    /** 같은 티어 안에서는 수가 많은 쪽이 먼저. */
    @Test
    void 같은_티어에서는_담은_사람이_많은_쪽이_먼저다() {
        List<String> out = sorted(List.of("few", "many"), Map.of(), Map.of("few", 2, "many", 9), java.util.Set.of());

        assertThat(out).containsExactly("many", "few");
    }

    /**
     * 티어도 수도 같으면 Kakao 순서를 지킨다.
     * 여기서 순서가 흔들리면 같은 검색을 두 번 해도 목록이 달라져 사용자가 방금 본 것을 못 찾는다.
     */
    @Test
    void 동점이면_원래_순서를_그대로_유지한다() {
        List<String> out = sorted(List.of("first", "second", "third"), Map.of(), Map.of(), java.util.Set.of());

        assertThat(out).containsExactly("first", "second", "third");
    }

    /** 🧳는 2명 미만이면 근거가 아니다 — 정렬에서도 근거로 치지 않아야 화면과 순서가 어긋나지 않는다. */
    @Test
    void 여행자_한_명은_티어를_올리지_못한다() {
        List<String> out = sorted(List.of("plain", "one"), Map.of(), Map.of("one", 1), java.util.Set.of());

        assertThat(out).containsExactly("plain", "one");
    }

    @Test
    void 빈_목록도_그대로_돌려준다() {
        assertThat(PlaceRanking.sort(List.of(), id -> new PlaceRanking.Signals(0, 0, false))).isEmpty();
    }
}
