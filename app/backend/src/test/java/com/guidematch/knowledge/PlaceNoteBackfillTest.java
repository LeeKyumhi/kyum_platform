package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * "나중에 흡수된다"는 약속의 판별기.
 *
 * <p>Kakao 유래 노트는 place_id가 비어 있다. 그 장소가 뒤늦게 레지스트리에 수집되면
 * 연결해줘야 코스 추천·인사이트와 같은 축에 서게 된다. 이게 없으면 노트는 영구히
 * kakao id로만 떠 있고, 레지스트리 쪽 기능과 절대 만나지 않는다.
 */
class PlaceNoteBackfillTest {

    private final PlaceNoteRepository noteRepo = mock(PlaceNoteRepository.class);
    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    private final PlaceNoteBackfill backfill = new PlaceNoteBackfill(noteRepo, placeRepo, tx);

    private Place place(long id, String kakaoId) {
        Place p = new Place("덕수궁", "Seoul", "중구", 37.5, 126.9, kakaoId, null, "관광명소", "서울");
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void 수집된_장소의_노트에_place_id가_채워진다() {
        PlaceNote orphan = new PlaceNote(null, "8113954", "덕수궁", 3L, null, null, "좋아요");
        when(noteRepo.findUnlinkedKakaoIds()).thenReturn(List.of("8113954"));
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of(place(17L, "8113954")));
        when(noteRepo.findByKakaoPlaceIdAndPlaceIdIsNull("8113954")).thenReturn(List.of(orphan));

        int linked = backfill.run();

        assertThat(linked).isEqualTo(1);
        assertThat(orphan.getPlaceId()).isEqualTo(17L);
        assertThat(orphan.getKakaoPlaceId()).as("kakao id는 지우지 않는다").isEqualTo("8113954");
    }

    @Test
    void 아직_수집되지_않은_장소는_건드리지_않는다() {
        when(noteRepo.findUnlinkedKakaoIds()).thenReturn(List.of("9982341"));
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of());

        int linked = backfill.run();

        assertThat(linked).isZero();
        verify(noteRepo, never()).saveAll(anyCollection());
    }

    @Test
    void 연결할_것이_없으면_쿼리를_더_내지_않는다() {
        when(noteRepo.findUnlinkedKakaoIds()).thenReturn(List.of());

        assertThat(backfill.run()).isZero();
        verifyNoInteractions(placeRepo);
    }

    @Test
    void 실패해도_기동을_막지_않는다() {
        // 실제로 Supabase 소켓이 끊겨 앱이 못 뜬 적이 있다(레지스트리 사이클). 회귀로 고정한다.
        when(noteRepo.findUnlinkedKakaoIds()).thenThrow(new RuntimeException("소켓 끊김"));

        assertThatCode(() -> backfill.run()).doesNotThrowAnyException();
    }

    /**
     * 아무도 부르지 않는 백필은 없는 것과 같다.
     *
     * <p>{@code PlaceKindBackfill}과 같은 방식(기동 시 {@link ApplicationRunner})으로 돌아야 한다.
     * 이 어서션이 없으면 "구현했고 테스트도 통과하는데 실제로는 한 번도 안 도는" 상태가
     * 조용히 성립한다 — 노트는 영원히 kakao id로만 떠 있게 된다.
     */
    @Test
    void 기동_시_자동으로_돈다() throws Exception {
        when(noteRepo.findUnlinkedKakaoIds()).thenReturn(List.of());

        assertThat(backfill).isInstanceOf(ApplicationRunner.class);
        ((ApplicationRunner) backfill).run(null);

        verify(noteRepo).findUnlinkedKakaoIds();
    }
}
