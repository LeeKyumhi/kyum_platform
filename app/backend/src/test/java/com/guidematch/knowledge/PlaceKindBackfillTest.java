package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 기존 행 백필.
 *
 * <p>이게 안 돌면 레지스트리 후보가 0건이 되고, 그래도 <b>엔드포인트는 정상 응답한다</b>
 * (Kakao 폴백이 전부 채우므로). 결과가 예전과 똑같아서 "됐다"로 보이는 조용한 실패라
 * 테스트로 못 박아 둔다.
 */
class PlaceKindBackfillTest {

    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final org.springframework.transaction.support.TransactionTemplate tx =
            mock(org.springframework.transaction.support.TransactionTemplate.class);
    private final PlaceKindBackfill backfill = new PlaceKindBackfill(placeRepo, tx);

    /**
     * 트랜잭션 콜백을 그 자리에서 실행한다.
     *
     * <p>이 스텁이 없으면 백필 본문이 아예 안 돌아 <b>모든 테스트가 "아무 일도 안 함"으로 통과</b>한다.
     * 트랜잭션 안에서 도는 것 자체가 이 클래스의 핵심 요구사항이라(detached 엔티티면 저장마다
     * SELECT가 하나씩 붙는다) 경계를 실제로 거치게 둔다.
     */
    @org.junit.jupiter.api.BeforeEach
    @SuppressWarnings("unchecked")
    void runInsideTransaction() {
        doAnswer(inv -> {
            ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>)
                    inv.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    /** 컬럼이 갓 추가돼 종류가 비어 있는 상태를 재현한다. */
    private static Place place(long id, String name, String category) {
        Place p = new Place(name, "Seoul", "중구", 37.5, 127.0, "k" + id, null, category, null);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "placeKind", null);
        return p;
    }

    @Test
    void 종류가_비어있는_행을_적재와_같은_규칙으로_채운다() {
        when(placeRepo.findByPlaceKindIsNull()).thenReturn(List.of(
                place(1, "덕수궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁"),
                place(2, "남대문시장", "가정,생활 > 시장"),
                place(3, "게임문화축제", "A02>A0207>A02070200")));

        backfill.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Place>> saved = ArgumentCaptor.forClass(List.class);
        verify(placeRepo).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Place::getPlaceKind)
                .containsExactly(PlaceKind.ATTRACTION, PlaceKind.MARKET, PlaceKind.EVENT);
    }

    /** 이미 값이 있는 행은 조회 자체에 안 걸린다 — 사람이 손으로 고친 값을 되돌리지 않는다. */
    @Test
    void 이미_종류가_있는_행은_건드리지_않는다() {
        Place already = place(4, "덕수궁", "여행 > 관광,명소");
        ReflectionTestUtils.setField(already, "placeKind", PlaceKind.CULTURE);
        when(placeRepo.findByPlaceKindIsNull()).thenReturn(List.of());

        backfill.run(null);

        verify(placeRepo, never()).saveAll(any());
        assertThat(already.getPlaceKind()).isEqualTo(PlaceKind.CULTURE);
    }

    /** 두 번째 기동에서 할 일이 없으면 쓰기도 없어야 한다 (매 기동 도는 러너다). */
    @Test
    void 채울_행이_없으면_저장하지_않는다() {
        when(placeRepo.findByPlaceKindIsNull()).thenReturn(List.of());

        backfill.run(null);

        verify(placeRepo, never()).saveAll(any());
    }

    /** 카테고리가 없는 행도 OTHER로 확정한다 — NULL로 남으면 매 기동 다시 조회된다. */
    @Test
    void 카테고리가_없으면_OTHER로_확정한다() {
        when(placeRepo.findByPlaceKindIsNull()).thenReturn(List.of(place(5, "이름만 있는 곳", null)));

        backfill.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Place>> saved = ArgumentCaptor.forClass(List.class);
        verify(placeRepo).saveAll(saved.capture());
        assertThat(saved.getValue().get(0).getPlaceKind()).isEqualTo(PlaceKind.OTHER);
    }

    /**
     * 회귀 테스트 — 실제로 밟은 사고다.
     *
     * <p>백필 도중 Supabase 소켓이 끊기자({@code Can't assign requested address})
     * {@code ApplicationRunner}에서 예외가 새어나가 <b>앱이 통째로 기동에 실패했다.</b>
     * 백필은 다음 기동에 다시 시도하면 그만이고(멱등), 그것 때문에 서버가 못 뜨면 안 된다.
     */
    @Test
    void 백필이_실패해도_기동을_막지_않는다() {
        when(placeRepo.findByPlaceKindIsNull())
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException(
                        "An I/O error occurred while sending to the backend."));

        backfill.run(null);   // 예외가 새어나가면 이 테스트가 실패한다

        verify(placeRepo, never()).saveAll(any());
    }
}
