package com.guidematch.guide;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TourCourseRepository extends JpaRepository<TourCourse, Long> {

    /**
     * 이 정차지들을 코스에 담은 코스들 — 🎫 근거 집계용 (배치 1회).
     *
     * <p>인증·활성 필터를 여기 넣지 않는 것은 의도다. 세 규칙(가이드 DISTINCT · VERIFIED만 ·
     * 활성만)이 전부 쿼리 문자열에 들어가면 이 저장소에서는 <b>검증할 방법이 없다</b> —
     * DB를 띄우는 테스트가 하나도 없기 때문이다. 규칙은 {@code GuideCourseSignalLookup}의
     * Java 코드에 두고 테스트로 고정한다.
     *
     * <p>{@code waypoints}는 LAZY라 여기서 fetch해 두지 않으면 호출부에서
     * LazyInitializationException이 난다.
     */
    @EntityGraph(attributePaths = "waypoints")
    List<TourCourse> findByWaypointsPlaceIdIn(Collection<String> placeIds);

    List<TourCourse> findByGuideProfileIdOrderByCreatedAtDesc(Long guideProfileId);

    List<TourCourse> findByGuideProfileIdAndActiveTrueOrderByCreatedAtDesc(Long guideProfileId);

    List<TourCourse> findByActiveTrueOrderByCreatedAtDesc();

    List<TourCourse> findByActiveTrueAndCityIgnoreCaseOrderByCreatedAtDesc(String city);
}
