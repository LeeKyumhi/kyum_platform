package com.guidematch.guide;

import java.util.Arrays;

/**
 * 가이드/호스트가 제공하는 서비스 카테고리.
 *
 * 관광진흥법 제38조: 외국인 대상 유상 "관광안내"는 관광통역안내사 자격 보유자만 가능.
 * → requiresGuideLicense=true 카테고리(관광)는 인증(VERIFIED) 가이드만 제공/예약될 수 있고,
 *   false 카테고리(비관광 동행)는 누구나 제공할 수 있다.
 *
 * 이 플래그가 프로필·코스·예약 3중 게이팅의 단일 기준이다.
 */
public enum ServiceCategory {

    /** 관광 가이드 — 명소 안내·해설 등 관광안내(자격 필수). */
    TOUR_GUIDE(true),

    /** 병원·관공서 통역 동행. */
    MEDICAL_INTERPRETER(false),
    /** 식사 동행. */
    DINING_COMPANION(false),
    /** 카페 동행. */
    CAFE_COMPANION(false),
    /** 쇼핑 통역. */
    SHOPPING_INTERPRETER(false),
    /** 언어 교환. */
    LANGUAGE_EXCHANGE(false);

    private final boolean requiresGuideLicense;

    ServiceCategory(boolean requiresGuideLicense) {
        this.requiresGuideLicense = requiresGuideLicense;
    }

    /** 이 카테고리가 관광통역안내사 자격을 요구하는가(=관광). */
    public boolean requiresGuideLicense() {
        return requiresGuideLicense;
    }

    /**
     * 키 문자열 → enum. 알 수 없는 값이면 IllegalArgumentException(전역 핸들러가 400으로 변환).
     */
    public static ServiceCategory fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("서비스 카테고리를 선택하세요.");
        }
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(key.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("올바르지 않은 서비스 카테고리입니다: " + key));
    }
}
