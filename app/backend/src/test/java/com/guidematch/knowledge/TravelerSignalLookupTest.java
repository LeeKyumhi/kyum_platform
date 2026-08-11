package com.guidematch.knowledge;

import com.guidematch.itinerary.ItineraryRepository;
import com.guidematch.saved.SavedItemRepository;
import com.guidematch.saved.SavedItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 🧳 "여행자 N명이 담은 곳" 집계.
 *
 * <p>출처가 둘이다 — 찜(saved_items)과 일정(itinerary_items). 두 수를 <b>더하면 안 된다</b>:
 * 찜해두고 일정에도 넣은 한 사람이 2명이 되어, 배지가 실제보다 부풀려진다.
 * 사람 단위 합집합으로 세야 "N명"이 참이 된다.
 *
 * <p>찜은 {@code place_ref}에 {@code kakao:} 접두어를 붙여 저장한다(explore 화면 규약).
 * 일정은 접두어 없이 원시 Kakao id다. 두 표기를 여기서 흡수하지 않으면 조인이 통째로 빗나가고,
 * 그 모습은 "아직 아무도 안 담은 장소"와 똑같아서 버그로 보이지 않는다.
 */
class TravelerSignalLookupTest {

    private final SavedItemRepository savedRepo = mock(SavedItemRepository.class);
    private final ItineraryRepository itineraryRepo = mock(ItineraryRepository.class);
    private final TravelerSignalLookup lookup = new TravelerSignalLookup(savedRepo, itineraryRepo);

    /** 저장소가 돌려주는 [userId, placeRef] 행. */
    private Object[] savedRow(long userId, String kakaoId) {
        return new Object[]{ userId, "kakao:" + kakaoId };
    }

    /** 저장소가 돌려주는 [ownerId, placeId] 행. */
    private Object[] tripRow(long ownerId, String kakaoId) {
        return new Object[]{ ownerId, kakaoId };
    }

    private void given(List<Object[]> saved, List<Object[]> trips) {
        when(savedRepo.userPlaceRefPairs(eq(SavedItemType.PLACE), anyCollection())).thenReturn(saved);
        when(itineraryRepo.ownerPlaceIdPairs(anyCollection())).thenReturn(trips);
    }

    @Test
    void 찜한_사람과_일정에_담은_사람을_합쳐_센다() {
        given(List.<Object[]>of(savedRow(1L, "A")), List.<Object[]>of(tripRow(2L, "A")));

        assertThat(lookup.travelerCounts(List.of("A"))).containsEntry("A", 2);
    }

    /** 같은 사람이 찜도 하고 일정에도 넣었다 — 그래도 한 명이다. */
    @Test
    void 같은_사람이_두_경로로_담아도_한_명이다() {
        given(List.<Object[]>of(savedRow(1L, "A")), List.<Object[]>of(tripRow(1L, "A")));

        assertThat(lookup.travelerCounts(List.of("A"))).containsEntry("A", 1);
    }

    /** 한 사람이 여러 일정에 같은 장소를 넣어도 한 명이다. */
    @Test
    void 한_사람이_여러_일정에_담아도_한_명이다() {
        given(List.of(), List.<Object[]>of(tripRow(1L, "A"), tripRow(1L, "A"), tripRow(1L, "A")));

        assertThat(lookup.travelerCounts(List.of("A"))).containsEntry("A", 1);
    }

    /** 찜은 "kakao:" 접두어로 저장된다 — 벗겨내지 않으면 조인이 전부 빗나간다. */
    @Test
    void 찜의_kakao_접두어를_벗겨_원시_id로_돌려준다() {
        given(List.<Object[]>of(savedRow(1L, "1234567"), savedRow(2L, "1234567")), List.of());

        Map<String, Integer> counts = lookup.travelerCounts(List.of("1234567"));

        assertThat(counts).containsOnlyKeys("1234567");
        assertThat(counts.get("1234567")).isEqualTo(2);
    }

    /** 조회할 때도 접두어를 붙여 물어야 한다 — 원시 id로 물으면 찜은 영원히 0이다. */
    @Test
    void 찜_조회는_접두어를_붙여_묻는다() {
        given(List.of(), List.of());

        lookup.travelerCounts(List.of("A", "B"));

        verify(savedRepo).userPlaceRefPairs(SavedItemType.PLACE, List.of("kakao:A", "kakao:B"));
        verify(itineraryRepo).ownerPlaceIdPairs(List.of("A", "B"));
    }

    /** 규칙2 — 0은 키가 아예 없어야 호출부가 "0명이 담았어요"를 만들 수 없다. */
    @Test
    void 아무도_안_담은_장소는_키가_없다() {
        given(List.of(), List.of());

        assertThat(lookup.travelerCounts(List.of("A", "B"))).isEmpty();
    }

    @Test
    void 요청하지_않은_장소는_결과에_없다() {
        given(List.<Object[]>of(savedRow(1L, "A")), List.<Object[]>of(tripRow(2L, "Z")));

        assertThat(lookup.travelerCounts(List.of("A"))).containsOnlyKeys("A");
    }

    @Test
    void 빈_입력이면_쿼리를_날리지_않는다() {
        assertThat(lookup.travelerCounts(List.of())).isEmpty();
        verify(savedRepo, never()).userPlaceRefPairs(any(), anyCollection());
        verify(itineraryRepo, never()).ownerPlaceIdPairs(anyCollection());
    }

    /** 익명 행(userId null)은 사람으로 셀 수 없다 — 세면 "N명"이 거짓이 된다. */
    @Test
    void 사용자를_알_수_없는_행은_세지_않는다() {
        given(List.<Object[]>of(new Object[]{ null, "kakao:A" }), List.of());

        assertThat(lookup.travelerCounts(List.of("A"))).isEmpty();
    }

    /** 정차지가 몇 곳이든 조회는 두 번. */
    @Test
    void 정차지가_많아도_배치로_두_번만_조회한다() {
        given(List.of(), List.of());

        lookup.travelerCounts(List.of("a", "b", "c", "d", "e"));

        verify(savedRepo, times(1)).userPlaceRefPairs(any(), anyCollection());
        verify(itineraryRepo, times(1)).ownerPlaceIdPairs(anyCollection());
    }
}
