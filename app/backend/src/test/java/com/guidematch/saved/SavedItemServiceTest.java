package com.guidematch.saved;

import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.TourCourse;
import com.guidematch.guide.TourCourseRepository;
import com.guidematch.saved.dto.SavedIdsResponse;
import com.guidematch.saved.dto.SavedListResponse;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedItemServiceTest {

    @Mock SavedItemRepository savedItemRepository;
    @Mock GuideProfileRepository profileRepository;
    @Mock TourCourseRepository courseRepository;
    @Mock UserRepository userRepository;

    SavedItemService service;

    @BeforeEach
    void setUp() {
        service = new SavedItemService(savedItemRepository, profileRepository, courseRepository, userRepository);
    }

    // ── 저장: idempotent + 원본 존재 검증 ──

    @Test
    void 없는_코스_저장은_예외() {
        when(courseRepository.existsById(99L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.saveCourse(1L, 99L));
    }

    @Test
    void 코스_중복_저장은_무시() {
        when(courseRepository.existsById(20L)).thenReturn(true);
        when(savedItemRepository.existsByUserIdAndItemTypeAndRefId(1L, SavedItemType.COURSE, 20L)).thenReturn(true);

        service.saveCourse(1L, 20L);

        verify(savedItemRepository, never()).save(any());
    }

    @Test
    void 장소_저장은_ref와_이름_필수() {
        assertThrows(IllegalArgumentException.class,
                () -> service.savePlace(1L, " ", "이름", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.savePlace(1L, "kakao:123", " ", null, null, null, null, null));
    }

    @Test
    void 장소_중복_저장은_무시() {
        when(savedItemRepository.existsByUserIdAndItemTypeAndPlaceRef(1L, SavedItemType.PLACE, "gyeongbokgung"))
                .thenReturn(true);

        service.savePlace(1L, "gyeongbokgung", "경복궁", null, null, 37.5, 126.9, null);

        verify(savedItemRepository, never()).save(any());
    }

    // ── 해제: 없는 것 해제는 조용히 성공 ──

    @Test
    void 저장_해제() {
        SavedItem item = new SavedItem(1L, SavedItemType.GUIDE, 10L);
        when(savedItemRepository.findByUserIdAndItemTypeAndRefId(1L, SavedItemType.GUIDE, 10L))
                .thenReturn(Optional.of(item));

        service.unsave(1L, SavedItemType.GUIDE, 10L, null);

        verify(savedItemRepository).delete(item);
    }

    @Test
    void 없는_항목_해제는_무시() {
        when(savedItemRepository.findByUserIdAndItemTypeAndRefId(1L, SavedItemType.GUIDE, 10L))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.unsave(1L, SavedItemType.GUIDE, 10L, null));
        verify(savedItemRepository, never()).delete(any());
    }

    @Test
    void 장소_저장_해제() {
        SavedItem item = new SavedItem(1L, "gyeongbokgung", "경복궁", null, null, null, null, null);
        when(savedItemRepository.findByUserIdAndItemTypeAndPlaceRef(1L, SavedItemType.PLACE, "gyeongbokgung"))
                .thenReturn(Optional.of(item));

        service.unsave(1L, SavedItemType.PLACE, null, "gyeongbokgung");

        verify(savedItemRepository).delete(item);
    }

    // ── ids: 타입별로 올바르게 분류 ──

    @Test
    void myIds_가이드는_제외하고_코스_장소만() {
        SavedItem g = new SavedItem(1L, SavedItemType.GUIDE, 10L);   // 레거시 행 — 제외돼야 함
        SavedItem c = new SavedItem(1L, SavedItemType.COURSE, 20L);
        SavedItem p = new SavedItem(1L, "kakao:123", "장소", null, null, null, null, null);
        when(savedItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(g, c, p));

        SavedIdsResponse ids = service.myIds(1L);

        assertEquals(List.of(20L), ids.courseIds());
        assertEquals(List.of("kakao:123"), ids.placeRefs());
    }

    // ── 목록 조립: 사라진 원본은 조용히 제외 ──

    @Test
    void myList_비활성_코스는_제외하고_장소는_포함() {
        SavedItem c = new SavedItem(1L, SavedItemType.COURSE, 20L);  // 비활성
        SavedItem p = new SavedItem(1L, "gyeongbokgung", "경복궁", "명소", null, 37.5, 126.9, null);
        when(savedItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(c, p));

        TourCourse inactive = new TourCourse(5L, "코스", null, "서울", 3, 50000, "KRW", 4, null, null);
        ReflectionTestUtils.setField(inactive, "id", 20L);
        inactive.setActive(false);
        when(courseRepository.findAllById(List.of(20L))).thenReturn(List.of(inactive));

        SavedListResponse list = service.myList(1L);

        assertTrue(list.courses().isEmpty());
        assertEquals(1, list.places().size());
        assertEquals("경복궁", list.places().get(0).name());
    }

    @Test
    void myList_활성_코스는_가이드_이름과_함께_포함() {
        SavedItem c = new SavedItem(1L, SavedItemType.COURSE, 20L);
        when(savedItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(c));

        TourCourse active = new TourCourse(5L, "성수동 카페 투어", null, "서울", 3, 50000, "KRW", 4, null, null);
        ReflectionTestUtils.setField(active, "id", 20L);
        when(courseRepository.findAllById(List.of(20L))).thenReturn(List.of(active));

        GuideProfile profile = new GuideProfile(100L, "헤드라인", "소개", 20000, "KRW", "서울");
        ReflectionTestUtils.setField(profile, "id", 5L);
        when(profileRepository.findAllById(List.of(5L))).thenReturn(List.of(profile));

        User guideUser = new User("guide@test.com", "pw", "김가이드", "KR");
        ReflectionTestUtils.setField(guideUser, "id", 100L);
        when(userRepository.findAllById(List.of(100L))).thenReturn(List.of(guideUser));

        SavedListResponse list = service.myList(1L);

        assertEquals(1, list.courses().size());
        assertEquals("성수동 카페 투어", list.courses().get(0).title());
        assertEquals("김가이드", list.courses().get(0).guideName());
    }

    // ── 저장수 배치 ──

    @Test
    void counts_배치_집계() {
        // List.<Object[]>of 명시 필수 — List.of(new Object[]{...})는 varargs로 펼쳐져 List<Object>가 된다
        when(savedItemRepository.countsByTypeAndRefIds(SavedItemType.COURSE, List.of(1L, 2L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));

        Map<Long, Long> counts = service.counts(SavedItemType.COURSE, List.of(1L, 2L));

        assertEquals(3L, counts.get(1L));
        assertNull(counts.get(2L)); // 저장 0건은 미포함 — 프론트에서 ?? 0 처리
    }

    @Test
    void counts_빈_ids는_빈_맵() {
        assertTrue(service.counts(SavedItemType.GUIDE, List.of()).isEmpty());
        verifyNoInteractions(savedItemRepository);
    }
}
