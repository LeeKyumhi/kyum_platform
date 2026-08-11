package com.guidematch.knowledge;

import com.guidematch.storage.SupabaseStorageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 노트 작성 검증. <b>여기서 막지 못한 것은 되돌릴 수 없다</b> — 식별자 없는 사진은
 * 어느 장소인지 알 수 없고, 도배는 화면을 무너뜨린다.
 */
class PlaceNoteServiceTest {

    private final PlaceNoteRepository repo = mock(PlaceNoteRepository.class);
    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    private final PlaceImageProcessor processor = new PlaceImageProcessor();
    private final PlaceNoteService service =
            new PlaceNoteService(repo, placeRepo, storage, processor, "posts");

    private byte[] jpegBytes() throws IOException {
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private MockMultipartFile photo() throws IOException {
        return new MockMultipartFile("photo", "p.jpg", "image/jpeg", jpegBytes());
    }

    private void stubSaveReturnsArg() {
        when(repo.save(any(PlaceNote.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 사진을_올리면_두_크기가_저장되고_노트가_생긴다() throws IOException {
        stubSaveReturnsArg();
        when(storage.uploadPublic(any(), any(), any(), any())).thenReturn("https://sb/x.jpg");

        PlaceNote n = service.create(3L, null, "9982341", "동네카페", photo(), null);

        // full·thumb 두 번 올라간다
        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).uploadPublic(eq("posts"), paths.capture(), any(), eq("image/jpeg"));
        assertThat(paths.getAllValues()).anyMatch(p -> p.contains("_full.jpg"));
        assertThat(paths.getAllValues()).anyMatch(p -> p.contains("_thumb.jpg"));
        assertThat(paths.getAllValues()).allMatch(p -> p.startsWith("place-notes/3/"));

        assertThat(n.getPhotoUrl()).isNotNull();
        assertThat(n.getPhotoThumbUrl()).isNotNull();
        assertThat(n.getKakaoPlaceId()).isEqualTo("9982341");
    }

    @Test
    void 팁만_올리면_업로드는_일어나지_않는다() {
        stubSaveReturnsArg();

        PlaceNote n = service.create(3L, 17L, null, "덕수궁", null, "돌담길이 예뻐요");

        verifyNoInteractions(storage);
        assertThat(n.getTip()).isEqualTo("돌담길이 예뻐요");
        assertThat(n.getPhotoUrl()).isNull();
    }

    @Test
    void 장소_이름이_없으면_업로드_전에_거부한다() throws IOException {
        // 이름 검증이 업로드보다 뒤에 있으면, 이름 없는 요청이 스토리지에 고아 객체를
        // 두 개 남기고서야 거부된다 — 업로드 왕복 전에 막아야 한다.
        assertThatThrownBy(() -> service.create(3L, 17L, null, "  ", photo(), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
        verify(repo, never()).save(any());
    }

    @Test
    void 식별자가_없으면_거부한다() throws IOException {
        assertThatThrownBy(() -> service.create(3L, null, null, "무명", photo(), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
        verify(repo, never()).save(any());
    }

    @Test
    void 사진도_팁도_없으면_거부한다() {
        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void 팁이_140자를_넘으면_거부한다() {
        String tooLong = "가".repeat(141);
        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", null, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 허용하지_않는_형식은_거부한다() {
        MockMultipartFile gif = new MockMultipartFile("photo", "a.gif", "image/gif", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", gif, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 위장_파일은_디코딩_단계에서_거부된다() {
        // 확장자·content-type은 jpeg인데 내용이 이미지가 아니다.
        MockMultipartFile fake = new MockMultipartFile("photo", "a.jpg", "image/jpeg",
                "MZ not an image".getBytes());
        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", fake, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 한_사용자가_한_장소에_3개까지만_올릴_수_있다() {
        when(repo.idsByUserAndPlaceId(3L, 17L)).thenReturn(List.of(1L, 2L, 3L));

        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", null, "네번째"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void 같은_노트가_양쪽_쿼리에_걸려도_한_번만_센다() {
        // 두 키가 다 채워진 노트는 양쪽에 걸린다. count를 더하면 3개가 6개로 세어져
        // 두 번째 업로드부터 막힌다.
        stubSaveReturnsArg();
        when(repo.idsByUserAndPlaceId(3L, 17L)).thenReturn(List.of(1L, 2L));
        when(repo.idsByUserAndKakaoPlaceId(3L, "8113954")).thenReturn(List.of(1L, 2L));

        PlaceNote n = service.create(3L, 17L, "8113954", "덕수궁", null, "세번째");

        assertThat(n).isNotNull();   // 합집합은 2이므로 통과해야 한다
    }

    @Test
    void 키_하나만_와도_레지스트리로_풀어서_센다() {
        // place_id로 3개 올린 뒤 Kakao 카드에서 열면 클라이언트는 kakao id만 보낸다.
        // 그때 kakao id로만 세면 0이 나와 실질 상한이 6이 된다.
        Place p = new Place("덕수궁", "Seoul", "중구", 37.5, 126.9, "8113954", null, "관광명소", "서울");
        ReflectionTestUtils.setField(p, "id", 17L);
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of(p));
        when(repo.idsByUserAndPlaceId(3L, 17L)).thenReturn(List.of(1L, 2L, 3L));
        when(repo.idsByUserAndKakaoPlaceId(3L, "8113954")).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(3L, null, "8113954", "덕수궁", null, "우회 시도"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void 남의_노트는_지울_수_없다() {
        PlaceNote mine = new PlaceNote(17L, null, "덕수궁", 3L, null, null, "내 팁");
        ReflectionTestUtils.setField(mine, "id", 5L);
        when(repo.findById(5L)).thenReturn(java.util.Optional.of(mine));

        assertThatThrownBy(() -> service.delete(99L, 5L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void 본인_노트는_지울_수_있다() {
        PlaceNote mine = new PlaceNote(17L, null, "덕수궁", 3L, null, null, "내 팁");
        ReflectionTestUtils.setField(mine, "id", 5L);
        when(repo.findById(5L)).thenReturn(java.util.Optional.of(mine));

        service.delete(3L, 5L);

        verify(repo).delete(mine);
    }
}
