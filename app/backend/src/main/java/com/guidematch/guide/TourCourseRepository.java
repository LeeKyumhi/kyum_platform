package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourCourseRepository extends JpaRepository<TourCourse, Long> {

    List<TourCourse> findByGuideProfileIdOrderByCreatedAtDesc(Long guideProfileId);

    List<TourCourse> findByGuideProfileIdAndActiveTrueOrderByCreatedAtDesc(Long guideProfileId);

    List<TourCourse> findByActiveTrueOrderByCreatedAtDesc();

    List<TourCourse> findByActiveTrueAndCityIgnoreCaseOrderByCreatedAtDesc(String city);
}
