package com.guidematch.saved.dto;

import java.util.List;

/** 카드 ♡ 초기 상태용 경량 응답 — 내가 저장한 참조 3종 일괄. */
public record SavedIdsResponse(
        List<Long> guideIds,
        List<Long> courseIds,
        List<String> placeRefs
) {}
