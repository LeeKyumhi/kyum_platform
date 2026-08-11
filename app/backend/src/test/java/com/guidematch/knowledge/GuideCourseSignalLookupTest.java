package com.guidematch.knowledge;

import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.TourCourse;
import com.guidematch.guide.TourCourseRepository;
import com.guidematch.guide.TourCourseWaypoint;
import com.guidematch.guide.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * 🎫 "인증 가이드가 코스에 담은 곳" 집계.
 *
 * <p><b>이 배지는 셋 중 하나만 틀려도 거짓말이 된다.</b>
 * ① 코스 수로 세면 한 가이드가 코스 3개를 만들었을 때 "3명"이 된다.
 * ② 인증 필터가 없으면 미인증 가이드가 "인증 가이드"로 둔갑한다 —
 *    {@code TourCourseService}의 자격 게이팅은 관광 카테고리에만 걸리므로
 *    미인증 가이드도 다른 카테고리 코스는 만들 수 있다.
 * ③ 비활성 코스를 세면 판매하지도 않는 코스가 근거가 된다.
 *
 * <p>그리고 이 셋은 <b>화면에서 검증할 수 없다</b>. 코스 데이터가 거의 없어서
 * "집계가 틀린 것"과 "데이터가 없는 것"이 똑같이 빈 화면으로 보이기 때문이다.
 * 그래서 규칙을 SQL이 아니라 Java에 두고 여기서 고정한다.
 */
class GuideCourseSignalLookupTest {

    private final TourCourseRepository courseRepo = mock(TourCourseRepository.class);
    private final GuideProfileRepository guideRepo = mock(GuideProfileRepository.class);
    private final GuideCourseSignalLookup lookup = new GuideCourseSignalLookup(courseRepo, guideRepo);

    private TourCourse course(long id, long guideProfileId, boolean active, String... waypointPlaceIds) {
        TourCourse c = new TourCourse(guideProfileId, "코스", null, "Seoul",
                4, 50000, "KRW", 4, null, null);
        ReflectionTestUtils.setField(c, "id", id);
        c.setActive(active);
        c.replaceWaypoints(List.of(waypointPlaceIds).stream()
                .map(pid -> new TourCourseWaypoint(0, pid, "장소", null, null, null, null,
                        null, null, null, null))
                .toList());
        return c;
    }

    private GuideProfile guide(long id, VerificationStatus status) {
        GuideProfile g = new GuideProfile(id * 100, "헤드라인", null, 30000, "KRW", "Seoul");
        ReflectionTestUtils.setField(g, "id", id);
        g.setVerificationStatus(status);
        return g;
    }

    private void given(List<TourCourse> courses, GuideProfile... guides) {
        when(courseRepo.findByWaypointsPlaceIdIn(anyCollection())).thenReturn(courses);
        when(guideRepo.findAllById(anyCollection())).thenReturn(List.of(guides));
    }

    /** 서로 다른 인증 가이드 2명이 같은 장소를 담았다 — 이게 배지의 정상 동작. */
    @Test
    void 인증_가이드_두_명이_같은_장소를_담으면_둘로_센다() {
        given(List.of(course(1L, 10L, true, "kakao-A"),
                      course(2L, 11L, true, "kakao-A")),
              guide(10L, VerificationStatus.VERIFIED), guide(11L, VerificationStatus.VERIFIED));

        assertThat(lookup.verifiedGuideCounts(List.of("kakao-A"))).containsEntry("kakao-A", 2);
    }

    /** 한 명이 코스를 여러 개 만들어도 사람은 한 명이다. */
    @Test
    void 한_가이드의_코스_세_개는_한_명으로_센다() {
        given(List.of(course(1L, 10L, true, "kakao-A"),
                      course(2L, 10L, true, "kakao-A"),
                      course(3L, 10L, true, "kakao-A")),
              guide(10L, VerificationStatus.VERIFIED));

        assertThat(lookup.verifiedGuideCounts(List.of("kakao-A"))).containsEntry("kakao-A", 1);
    }

    /** 미인증 가이드는 배지의 근거가 될 수 없다 — 배지 문구가 "인증 가이드"이기 때문이다. */
    @Test
    void 미인증_가이드의_코스는_세지_않는다() {
        given(List.of(course(1L, 10L, true, "kakao-A"),
                      course(2L, 11L, true, "kakao-A"),
                      course(3L, 12L, true, "kakao-A")),
              guide(10L, VerificationStatus.VERIFIED),
              guide(11L, VerificationStatus.PENDING),
              guide(12L, VerificationStatus.NONE));

        assertThat(lookup.verifiedGuideCounts(List.of("kakao-A"))).containsEntry("kakao-A", 1);
    }

    @Test
    void 비활성_코스는_세지_않는다() {
        given(List.of(course(1L, 10L, false, "kakao-A")),
              guide(10L, VerificationStatus.VERIFIED));

        assertThat(lookup.verifiedGuideCounts(List.of("kakao-A"))).doesNotContainKey("kakao-A");
    }

    /**
     * 규칙2: 0은 표시하지 않는다. 0을 담은 항목을 돌려주면 호출부가 "0명이 담았어요"를
     * 만들 수 있게 된다 — 아예 키가 없어야 그 실수가 구조적으로 불가능해진다.
     */
    @Test
    void 근거가_없는_장소는_0이_아니라_키가_아예_없다() {
        given(List.of(course(1L, 10L, true, "kakao-A")),
              guide(10L, VerificationStatus.NONE));

        Map<String, Integer> counts = lookup.verifiedGuideCounts(List.of("kakao-A", "kakao-B"));

        assertThat(counts).isEmpty();
    }

    /** 코스에는 있지만 이번 추천에 안 나온 장소까지 세서 돌려주면 안 된다. */
    @Test
    void 요청하지_않은_장소는_결과에_없다() {
        given(List.of(course(1L, 10L, true, "kakao-A", "kakao-Z")),
              guide(10L, VerificationStatus.VERIFIED));

        assertThat(lookup.verifiedGuideCounts(List.of("kakao-A")))
                .containsOnlyKeys("kakao-A");
    }

    /**
     * 정차지가 몇 곳이든 조회는 배치로 한 번씩. Supabase가 시드니라 왕복 250ms —
     * 루프 조회로 퇴화하면 응답 시간이 정차지 수에 비례해 늘어난다.
     */
    @Test
    void 정차지가_많아도_배치로_한_번씩만_조회한다() {
        given(List.of(course(1L, 10L, true, "a"), course(2L, 11L, true, "b")),
              guide(10L, VerificationStatus.VERIFIED), guide(11L, VerificationStatus.VERIFIED));

        lookup.verifiedGuideCounts(List.of("a", "b", "c", "d", "e"));

        verify(courseRepo, times(1)).findByWaypointsPlaceIdIn(anyCollection());
        verify(guideRepo, times(1)).findAllById(anyCollection());
    }

    @Test
    void 빈_입력이면_쿼리를_날리지_않는다() {
        assertThat(lookup.verifiedGuideCounts(List.of())).isEmpty();
        verify(courseRepo, never()).findByWaypointsPlaceIdIn(anyCollection());
        verify(guideRepo, never()).findAllById(anyCollection());
    }

    /**
     * 프론트가 합성하던 {@code rec-3-경복궁} 같은 값이 과거에 저장돼 있다.
     * 조인에 안 걸리는 게 정상이고, 여기서 죽지 않는 것도 정상이어야 한다.
     */
    @Test
    void 오염된_합성_id는_조용히_무시된다() {
        given(List.of(course(1L, 10L, true, "rec-3-경복궁")),
              guide(10L, VerificationStatus.VERIFIED));

        assertThat(lookup.verifiedGuideCounts(List.of("kakao-A"))).isEmpty();
    }

    /** null placeId를 가진 waypoint(수기 입력 장소)가 섞여도 죽지 않는다. */
    @Test
    void placeId가_null인_정차지가_섞여도_죽지_않는다() {
        TourCourse c = course(1L, 10L, true, "kakao-A");
        c.replaceWaypoints(List.of(
                new TourCourseWaypoint(0, null, "수기 장소", null, null, null, null, null, null, null, null),
                new TourCourseWaypoint(1, "kakao-A", "장소", null, null, null, null, null, null, null, null)));
        given(List.of(c), guide(10L, VerificationStatus.VERIFIED));

        assertThat(lookup.verifiedGuideCounts(List.of("kakao-A"))).containsEntry("kakao-A", 1);
    }

    @Test
    void null이_섞인_입력은_걸러낸다() {
        assertThat(lookup.verifiedGuideCounts(java.util.Arrays.asList(null, "", "  "))).isEmpty();
        verify(courseRepo, never()).findByWaypointsPlaceIdIn(any());
    }
}
