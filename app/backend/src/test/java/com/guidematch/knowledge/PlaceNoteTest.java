package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 노트는 <b>두 개의 장소 식별자를 둘 다</b> 들고 있다.
 *
 * <p>해소해서 하나로 접으면(= {@code SignalRecorder}가 하는 방식) 레지스트리에 없는 장소에
 * 올린 사진이 place_id=null로 남아 <b>어느 장소 사진인지 영영 알 수 없게 된다.</b>
 * 레지스트리는 53건이고 Kakao 검색 결과는 수천 건이라, 대부분의 사진이 그렇게 사라진다.
 */
class PlaceNoteTest {

    @Test
    void 레지스트리_유래는_place_id를_갖는다() {
        PlaceNote n = new PlaceNote(17L, null, "덕수궁", 3L, "u/full.jpg", "u/thumb.jpg", null);

        assertThat(n.getPlaceId()).isEqualTo(17L);
        assertThat(n.getKakaoPlaceId()).isNull();
        assertThat(n.getStatus()).isEqualTo("VISIBLE");
        assertThat(n.getCreatedAt()).isNotNull();
    }

    @Test
    void Kakao_유래는_kakao_place_id를_버리지_않는다() {
        PlaceNote n = new PlaceNote(null, "9982341", "동네카페", 3L, null, null, "2층 창가 자리가 좋아요");

        assertThat(n.getPlaceId()).isNull();
        assertThat(n.getKakaoPlaceId()).isEqualTo("9982341");
        assertThat(n.getTip()).isEqualTo("2층 창가 자리가 좋아요");
    }

    @Test
    void 식별자가_하나도_없으면_만들_수_없다() {
        // 나중에 어떤 장소인지 알 수 없는 사진은 자산이 아니라 쓰레기다.
        // SignalRecorder:96이 빈 행을 안 만드는 것과 같은 판단이다.
        assertThatThrownBy(() -> new PlaceNote(null, null, "무명", 3L, "u/full.jpg", "u/thumb.jpg", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PlaceNote(null, "   ", "무명", 3L, "u/full.jpg", "u/thumb.jpg", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 사진도_팁도_없으면_만들_수_없다() {
        assertThatThrownBy(() -> new PlaceNote(17L, null, "덕수궁", 3L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PlaceNote(17L, null, "덕수궁", 3L, null, null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 숨기면_status가_바뀐다() {
        PlaceNote n = new PlaceNote(17L, null, "덕수궁", 3L, "u/full.jpg", "u/thumb.jpg", null);
        n.hide();
        assertThat(n.getStatus()).isEqualTo("HIDDEN");
    }

    @Test
    void 나중에_레지스트리에_수집되면_place_id가_채워진다() {
        PlaceNote n = new PlaceNote(null, "9982341", "동네카페", 3L, null, null, "좋아요");
        n.linkPlaceId(88L);

        assertThat(n.getPlaceId()).isEqualTo(88L);
        // kakao id는 지우지 않는다 — 두 키 합집합 조회가 계속 이걸 쓴다.
        assertThat(n.getKakaoPlaceId()).isEqualTo("9982341");
    }
}
