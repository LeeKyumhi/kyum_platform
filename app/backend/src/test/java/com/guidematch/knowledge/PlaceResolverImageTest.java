package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 공식 사진은 <b>발행처와 함께</b> 저장된다.
 *
 * <p>image_url만 저장하면 표시 시점에 발행처를 되찾을 방법이 없고, 그러면
 * "출처를 못 밝히는 사진은 띄우지 않는다"는 규칙이 원리상 지켜질 수 없다.
 * sources.yml 주석이 같은 말을 한다 — "나중에 출처를 되찾을 방법이 없으면 표시할 수도 없다".
 */
class PlaceResolverImageTest {

    private Place namsan() {
        return new Place("남산케이블카", "Seoul", "중구", 37.55, 126.98,
                "123", "126508", "관광명소", "서울 중구");
    }

    @Test
    void 이미지와_발행처가_함께_저장된다() {
        Place p = namsan();
        p.applyImage("https://tong.visitkorea.or.kr/x.jpg", "한국관광공사");

        assertThat(p.getImageUrl()).isEqualTo("https://tong.visitkorea.or.kr/x.jpg");
        assertThat(p.getImagePublisher()).isEqualTo("한국관광공사");
    }

    @Test
    void 발행처가_없으면_이미지도_저장하지_않는다() {
        Place p = namsan();
        p.applyImage("https://x/y.jpg", null);

        // 띄울 수 없는 사진은 저장할 이유도 없다 — 나중에 "왜 안 보이지"의 원인이 된다.
        assertThat(p.getImageUrl()).isNull();
        assertThat(p.getImagePublisher()).isNull();
    }

    @Test
    void 이미_있는_이미지를_빈_값으로_덮지_않는다() {
        Place p = namsan();
        p.applyImage("https://x/first.jpg", "한국관광공사");
        p.applyImage(null, null);

        assertThat(p.getImageUrl()).isEqualTo("https://x/first.jpg");
    }

    /**
     * 재적재 멱등성. 같은 사진이 다시 들어와도 "바뀌었다"고 하면 안 된다 —
     * 호출부가 이 값으로 저장 여부를 정하는데(detached save = 행마다 SELECT, 시드니 왕복 250ms),
     * 여기서 true를 내면 재적재가 매번 전체 쓰기가 된다.
     */
    @Test
    void 같은_이미지_재적용은_변경이_아니다() {
        Place p = namsan();
        assertThat(p.applyImage("https://x/a.jpg", "한국관광공사")).isTrue();
        assertThat(p.applyImage("https://x/a.jpg", "한국관광공사")).isFalse();
    }

    /**
     * 사진만 새로 들어온 재적재도 저장돼야 한다.
     *
     * <p>{@code enrichMissing}은 사진을 모르므로, resolver가 그 결과만 보고 판단하면
     * <b>사진이 조용히 버려진다</b>(needsSave=false → 호출부가 save를 건너뜀).
     * 이미 있는 장소에 v5 재수집이 사진을 처음 실어 오는 경우가 정확히 이 경로다.
     */
    @Test
    void 기존_장소에_사진만_들어와도_저장이_필요하다고_알린다() {
        PlaceRepository placeRepo = mock(PlaceRepository.class);
        PlaceAliasRepository aliasRepo = mock(PlaceAliasRepository.class);
        Place existing = namsan();
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", 7L);
        when(placeRepo.findByKakaoPlaceId("123")).thenReturn(java.util.Optional.of(existing));
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(true);

        PlaceResolver resolver = new PlaceResolver(placeRepo, aliasRepo, 200, 2000);
        PlaceClue clue = new PlaceClue("남산케이블카", List.of(), "Seoul", "중구",
                37.55, 126.98, "123", "126508", "관광명소", "서울 중구", "tour_api",
                "https://tong.visitkorea.or.kr/x.jpg", "한국관광공사");

        PlaceResolver.Resolution r = resolver.resolve(clue);

        assertThat(r.isResolved()).isTrue();
        assertThat(r.needsSave()).isTrue();
        assertThat(r.place().getImageUrl()).isEqualTo("https://tong.visitkorea.or.kr/x.jpg");
        assertThat(r.place().getImagePublisher()).isEqualTo("한국관광공사");
    }

    /** 새로 만들어지는 장소도 첫 저장에 사진을 갖고 들어가야 한다(뒤에 update가 또 나가면 안 된다). */
    @Test
    void 새_장소는_처음_저장될_때_사진을_갖고_있다() {
        PlaceRepository placeRepo = mock(PlaceRepository.class);
        PlaceAliasRepository aliasRepo = mock(PlaceAliasRepository.class);
        when(placeRepo.findByKakaoPlaceId(anyString())).thenReturn(java.util.Optional.empty());
        when(placeRepo.findByNameNormalized(anyString())).thenReturn(List.of());
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> {
            Place saved = inv.getArgument(0);
            // 저장되는 순간의 상태를 검사한다 — 나중에 채우면 update 한 번이 더 나간다
            assertThat(saved.getImageUrl()).isEqualTo("https://x/new.jpg");
            assertThat(saved.getImagePublisher()).isEqualTo("한국관광공사");
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });

        PlaceResolver resolver = new PlaceResolver(placeRepo, aliasRepo, 200, 2000);
        PlaceClue clue = new PlaceClue("새 장소", List.of(), "Seoul", "중구",
                37.55, 126.98, "999", null, "관광명소", "서울 중구", "tour_api",
                "https://x/new.jpg", "한국관광공사");

        PlaceResolver.Resolution r = resolver.resolve(clue);

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place().getImageUrl()).isEqualTo("https://x/new.jpg");
    }
}
