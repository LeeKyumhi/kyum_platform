package com.guidematch.knowledge;

import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 노트 읽기. <b>배치만 노출한다</b> — 소비자가 리포지토리를 직접 쓰면 루프 안에서 단건 조회를
 * 하기 쉽고, Supabase가 시드니라 왕복이 250ms다. 목록 15곳이면 그것만으로 3.75초다.
 */
class PlaceMediaLookupTest {

    private final PlaceNoteRepository repo = mock(PlaceNoteRepository.class);
    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final PlaceMediaLookup lookup = new PlaceMediaLookup(repo, placeRepo, userRepo);

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    private PlaceNote note(long id, Long placeId, String kakaoId, String thumb, String tip) {
        PlaceNote n = new PlaceNote(placeId, kakaoId, "장소", 3L,
                thumb == null ? null : thumb.replace("_thumb", "_full"), thumb, tip);
        ReflectionTestUtils.setField(n, "id", id);
        // createdAt은 실제 시계(Instant.now())로 초기화되는데, 이 클래스 내에서 노트를
        // 만드는 순서(호출 순서)와 id 순서가 항상 일치하진 않는다. id가 클수록 최신이라는
        // 테스트 의도를 시계 타이밍이 아니라 id로 결정적으로 고정한다.
        ReflectionTestUtils.setField(n, "createdAt", BASE.plusSeconds(id));
        return n;
    }

    @Test
    void 두_출신의_노트가_한_장소로_합쳐진다() {
        // 이 테스트가 설계 §1 전체의 유일한 판별기다.
        // 깨지면 사진이 갈라져 쌓이는데 화면에는 "사진이 좀 적네"로만 보인다.
        Place registryPlace = new Place("덕수궁", "Seoul", "중구", 37.5, 126.9,
                "8113954", null, "관광명소", "서울 중구");
        ReflectionTestUtils.setField(registryPlace, "id", 17L);

        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of(registryPlace));
        when(repo.findVisibleByKakaoIdIn(anyCollection())).thenReturn(List.of(
                note(2L, null, "8113954", "https://sb/b_thumb.jpg", null)   // Kakao 경로로 올림
        ));
        when(repo.findVisibleByPlaceIdIn(anyCollection())).thenReturn(List.of(
                note(1L, 17L, null, "https://sb/a_thumb.jpg", null)         // 레지스트리 경로로 올림
        ));

        Map<String, PlaceMediaLookup.Cover> covers = lookup.coversByKakaoIds(List.of("8113954"));

        assertThat(covers).containsKey("8113954");
        assertThat(covers.get("8113954").photoCount())
                .as("두 출신의 사진이 합쳐져야 한다").isEqualTo(2);
    }

    @Test
    void 같은_노트가_양쪽_쿼리에_걸려도_한_번만_센다() {
        // 두 키가 다 채워진 노트(백필이 place_id를 채운 뒤)는 두 쿼리에 모두 걸린다.
        // 중복 제거가 없으면 사진 장수가 부풀려진다.
        Place registryPlace = new Place("덕수궁", "Seoul", "중구", 37.5, 126.9,
                "8113954", null, "관광명소", "서울 중구");
        ReflectionTestUtils.setField(registryPlace, "id", 17L);
        PlaceNote both = note(1L, 17L, "8113954", "https://sb/a_thumb.jpg", null);

        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of(registryPlace));
        when(repo.findVisibleByKakaoIdIn(anyCollection())).thenReturn(List.of(both));
        when(repo.findVisibleByPlaceIdIn(anyCollection())).thenReturn(List.of(both));

        Map<String, PlaceMediaLookup.Cover> covers = lookup.coversByKakaoIds(List.of("8113954"));

        assertThat(covers.get("8113954").photoCount()).isEqualTo(1);
    }

    @Test
    void 사진이_없으면_키가_아예_없다() {
        // "0장"을 담은 항목을 만들면 프론트가 "사진 0장"을 렌더할 여지가 생긴다.
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of());
        when(repo.findVisibleByKakaoIdIn(anyCollection())).thenReturn(List.of(
                note(1L, null, "8113954", null, "팁만 있음")
        ));

        Map<String, PlaceMediaLookup.Cover> covers = lookup.coversByKakaoIds(List.of("8113954"));

        assertThat(covers).doesNotContainKey("8113954");
    }

    @Test
    void 조회는_장소_수와_무관하게_고정_횟수다() {
        // 레지스트리에 걸리는 장소가 없으면 place_id 쿼리는 아예 안 나간다.
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of());
        when(repo.findVisibleByKakaoIdIn(anyCollection())).thenReturn(List.of());

        lookup.coversByKakaoIds(List.of("1", "2", "3", "4", "5", "6", "7", "8"));

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repo, times(1)).findVisibleByKakaoIdIn(captor.capture());
        assertThat(captor.getValue()).hasSize(8);
        verify(repo, never()).findVisibleByPlaceIdIn(anyCollection());
        verify(placeRepo, times(1)).findAllByKakaoPlaceIdIn(anyCollection());
    }

    @Test
    void 빈_입력은_쿼리를_내지_않는다() {
        assertThat(lookup.coversByKakaoIds(List.of())).isEmpty();
        verifyNoInteractions(repo);
        verifyNoInteractions(placeRepo);
    }

    @Test
    void 상세는_사진과_팁을_모두_최신순으로_준다() {
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of());
        when(repo.findVisibleByKakaoIdIn(anyCollection())).thenReturn(List.of(
                note(2L, null, "8113954", "https://sb/b_thumb.jpg", null),
                note(1L, null, "8113954", null, "돌담길이 예뻐요")
        ));
        User u = mock(User.class);
        when(u.getId()).thenReturn(3L);
        when(u.getHandle()).thenReturn("seoul_lover");
        when(userRepo.findAllById(anyCollection())).thenReturn(List.of(u));

        List<PlaceMediaLookup.NoteView> views = lookup.notesFor(null, "8113954");

        assertThat(views).hasSize(2);
        assertThat(views.get(0).id()).isEqualTo(2L);
        assertThat(views).allMatch(v -> "seoul_lover".equals(v.authorHandle()));
    }

    @Test
    void kakao_id가_없는_레지스트리_장소도_상세를_준다() {
        // placeId=44 "개화"처럼 kakao_place_id가 비어 있는 레지스트리 장소가 실제로 있다.
        when(repo.findVisibleByPlaceIdIn(anyCollection())).thenReturn(List.of(
                note(1L, 44L, null, "https://sb/a_thumb.jpg", null)
        ));
        when(userRepo.findAllById(anyCollection())).thenReturn(List.of());

        List<PlaceMediaLookup.NoteView> views = lookup.notesFor(44L, null);

        assertThat(views).hasSize(1);
        verify(repo, never()).findVisibleByKakaoIdIn(anyCollection());
    }

    @Test
    void 작성자_조회도_배치_1회다() {
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of());
        when(repo.findVisibleByKakaoIdIn(anyCollection())).thenReturn(List.of(
                note(1L, null, "8113954", "https://sb/a_thumb.jpg", null),
                note(2L, null, "8113954", "https://sb/b_thumb.jpg", null)
        ));
        when(userRepo.findAllById(anyCollection())).thenReturn(List.of());

        lookup.notesFor(null, "8113954");

        verify(userRepo, times(1)).findAllById(anyCollection());
    }
}
