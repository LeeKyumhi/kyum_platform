package com.guidematch.geo;

import com.guidematch.knowledge.PlaceNote;
import com.guidematch.knowledge.PlaceNoteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 쓰기 엔드포인트. <b>비로그인은 여기 오지 않는다</b>(SecurityConfig가 막는다) —
 * 그래서 userId가 null이면 그건 설정 오류이고, 조용히 저장하는 것보다 예외가 낫다.
 * 검증 실패는 컨트롤러가 잡지 않고 그대로 던진다 — {@code GlobalExceptionHandler}가
 * 400으로 변환하므로, 여기서는 예외가 올라오는지와 부수효과(서비스 호출 여부)만 본다.
 */
class PlaceNoteControllerTest {

    private final PlaceNoteService service = mock(PlaceNoteService.class);
    private final com.guidematch.user.UserRepository userRepo =
            mock(com.guidematch.user.UserRepository.class);
    private final PlaceNoteController controller = new PlaceNoteController(service, userRepo);

    private PlaceNote note(long id) {
        PlaceNote n = new PlaceNote(17L, null, "덕수궁", 3L, "https://sb/f.jpg", "https://sb/t.jpg", "팁");
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Test
    void 노트를_만들면_201과_본문을_돌려준다() {
        when(service.create(eq(3L), eq(17L), isNull(), eq("덕수궁"), any(), eq("팁")))
                .thenReturn(note(5L));

        ResponseEntity<?> res = controller.create(3L, 17L, null, "덕수궁", null, "팁");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isInstanceOf(PlaceNoteController.NoteResponse.class);
        PlaceNoteController.NoteResponse body = (PlaceNoteController.NoteResponse) res.getBody();
        assertThat(body.id()).isEqualTo(5L);
        assertThat(body.photoThumbUrl()).isEqualTo("https://sb/t.jpg");
    }

    @Test
    void 검증_실패는_예외를_그대로_던진다() {
        when(service.create(anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("사진 또는 한줄팁 중 하나는 입력해야 합니다."));

        assertThatThrownBy(() -> controller.create(3L, 17L, null, "덕수궁", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("한줄팁");
    }

    @Test
    void 인증_없이_들어오면_예외를_던지고_서비스는_건드리지_않는다() {
        // SecurityConfig가 이미 막지만, 규칙이 바뀌어 새면 저장하지 않고 거부해야 한다.
        assertThatThrownBy(() -> controller.create(null, 17L, null, "덕수궁", null, "팁"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(service);
    }

    @Test
    void 삭제는_204다() {
        ResponseEntity<?> res = controller.delete(3L, 5L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(3L, 5L);
    }

    @Test
    void 사진이_multipart로_들어오면_서비스로_그대로_넘긴다() {
        MockMultipartFile photo = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[]{1});
        when(service.create(eq(3L), isNull(), eq("9982341"), eq("카페"), same(photo), isNull()))
                .thenReturn(note(6L));

        ResponseEntity<?> res = controller.create(3L, null, "9982341", "카페", photo, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
