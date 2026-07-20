package com.guidematch.saved.dto;

import java.util.List;

/** 카드 ♡ 초기 상태용 경량 응답 — 내가 저장한 참조 2종 일괄. 가이드 저장은 폐기(Phase 0). */
public record SavedIdsResponse(
        List<Long> courseIds,
        List<String> placeRefs
) {}
