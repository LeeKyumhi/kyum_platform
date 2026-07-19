package com.guidematch.saved;

import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.TourCourse;
import com.guidematch.guide.TourCourseRepository;
import com.guidematch.saved.dto.SavedIdsResponse;
import com.guidematch.saved.dto.SavedListResponse;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void 가이드_저장_성공() {
        when(profileRepository.existsById(10L)).thenReturn(true);
        when(savedItemRepository.existsByUserIdAndItemTypeAndRefId(1L, SavedItemType.GUIDE, 10L)).thenReturn(false);

        service.saveGuide(1L, 10L);

        verify(savedItemRepository).save(any(SavedItem.class));
    }

    @Test
    void 가이드_중복_저장은_무시() {
        when(profileRepository.existsById(10L)).thenReturn(true);
        when(savedItemRepository.existsByUserIdAndItemTypeAndRefId(1L, SavedItemType.GUIDE, 10L)).thenReturn(true);

        service.saveGuide(1L, 10L);

        verify(savedItemRepository, never()).save(any());
    }

    @Test
    void 없는_가이드_저장은_예외() {
        when(profileRepository.existsById(99L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.saveGuide(1L, 99L));
    }

    @Test
    void 없는_코스_저장은_예외() {
        when(courseRepository.existsById(99L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.saveCourse(1L, 99L));
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

    // ── ids: 타입별로 올바르게 분류 ──

    @Test
    void myIds_타입별_분류() {
        SavedItem g = new SavedItem(1L, SavedItemType.GUIDE, 10L);
        SavedItem c = new SavedItem(1L, SavedItemType.COURSE, 20L);
        SavedItem p = new SavedItem(1L, "kakao:123", "장소", null, null, null, null, null);
        when(savedItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(g, c, p));

        SavedIdsResponse ids = service.myIds(1L);

        assertEquals(List.of(10L), ids.guideIds());
        assertEquals(List.of(20L), ids.courseIds());
        assertEquals(List.of("kakao:123"), ids.placeRefs());
    }

    // ── 목록 조립: 사라진 원본은 조용히 제외 ──

    @Test
    void myList_사라진_가이드와_비활성_코스는_제외() {
        SavedItem g = new SavedItem(1L, SavedItemType.GUIDE, 10L);   // 원본 없음
        SavedItem c = new SavedItem(1L, SavedItemType.COURSE, 20L);  // 비활성
        SavedItem p = new SavedItem(1L, "gyeongbokgung", "경복궁", "명소", null, 37.5, 126.9, null);
        when(savedItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(g, c, p));
        when(profileRepository.findAllById(List.of(10L))).thenReturn(List.of()); // 가이드 사라짐

        TourCourse inactive = new TourCourse(5L, "코스", null, "서울", 3, 50000, "KRW", 4, null, null);
        inactive.setActive(false);
        when(courseRepository.findAllById(List.of(20L))).thenReturn(List.of(inactive));

        SavedListResponse list = service.myList(1L);

        assertTrue(list.guides().isEmpty());
        assertTrue(list.courses().isEmpty());
        assertEquals(1, list.places().size());
        assertEquals("경복궁", list.places().get(0).name());
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
