package com.guidematch.saved.dto;

import com.guidematch.guide.dto.FollowingGuideResponse;
import com.guidematch.guide.dto.TourCourseResponse;

import java.util.List;

/** /saved 페이지용 — 저장 목록 3종 일괄 (각각 최신순). */
public record SavedListResponse(
        List<FollowingGuideResponse> guides,
        List<TourCourseResponse> courses,
        List<SavedPlaceResponse> places
) {}
