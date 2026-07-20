package com.guidematch.saved.dto;

import com.guidematch.guide.dto.TourCourseResponse;

import java.util.List;

/** /saved 페이지용 — 저장 목록 2종 일괄 (각각 최신순). 가이드 저장은 폐기(Phase 0). */
public record SavedListResponse(
        List<TourCourseResponse> courses,
        List<SavedPlaceResponse> places
) {}
