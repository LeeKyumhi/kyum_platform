package com.guidematch.guide.dto;

import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.LanguageLevel;

import java.util.List;

public record GuideProfileResponse(
        Long id,
        Long userId,
        String headline,
        String introduction,
        Integer hourlyRate,
        String currency,
        String region,
        String city,
        Double latitude,
        Double longitude,
        String avatarUrl,
        boolean active,
        boolean instantBooking,
        String mbti,
        List<String> interests,
        List<LanguageResponse> languages,
        /** 제공 서비스 카테고리 (ServiceCategory 키). become-guide/manage UI가 관광 게이팅에 사용. */
        List<String> serviceCategories,
        /** 관광통역안내사 자격 인증 상태 (NONE/PENDING/VERIFIED/REJECTED) — 관광 카테고리 선택 가능 여부 판단. */
        String verificationStatus
) {
    public record LanguageResponse(String language, LanguageLevel level) {}

    public static GuideProfileResponse from(GuideProfile profile) {
        List<LanguageResponse> langs = profile.getLanguages().stream()
                .map(l -> new LanguageResponse(l.getLanguage(), l.getLevel()))
                .toList();

        return new GuideProfileResponse(
                profile.getId(), profile.getUserId(), profile.getHeadline(),
                profile.getIntroduction(), profile.getHourlyRate(), profile.getCurrency(),
                profile.getRegion(), profile.getCity(), profile.getLatitude(), profile.getLongitude(),
                profile.getAvatarUrl(), profile.isActive(), profile.isInstantBooking(),
                profile.getMbti(), profile.getInterestList(), langs,
                profile.getServiceCategoryList(), profile.getVerificationStatus().name()
        );
    }
}
