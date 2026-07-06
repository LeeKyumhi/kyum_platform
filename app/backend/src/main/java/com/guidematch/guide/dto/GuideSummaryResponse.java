package com.guidematch.guide.dto;

import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.LanguageLevel;

import java.util.List;

public record GuideSummaryResponse(
        Long id,
        String guideName,
        String headline,
        Integer hourlyRate,
        String currency,
        String region,
        String city,
        Double latitude,
        Double longitude,
        String avatarUrl,
        double avgRating,
        long reviewCount,
        long followerCount,
        long bookingCount,
        String gender,
        boolean instantBooking,
        String mbti,
        List<String> interests,
        List<LanguageItem> languages,
        /** 로그인한 여행자와의 궁합 점수(0~99). 비로그인이거나 계산 근거가 없으면 null. */
        Integer matchScore
) {
    public record LanguageItem(String language, LanguageLevel level) {}

    public static GuideSummaryResponse from(GuideProfile profile, String guideName,
                                            double avgRating, long reviewCount, long followerCount,
                                            long bookingCount, String guideGender, Integer matchScore) {
        List<LanguageItem> langs = profile.getLanguages().stream()
                .map(l -> new LanguageItem(l.getLanguage(), l.getLevel()))
                .toList();

        return new GuideSummaryResponse(
                profile.getId(), guideName, profile.getHeadline(),
                profile.getHourlyRate(), profile.getCurrency(), profile.getRegion(),
                profile.getCity(), profile.getLatitude(), profile.getLongitude(),
                profile.getAvatarUrl(), avgRating, reviewCount, followerCount, bookingCount,
                guideGender, profile.isInstantBooking(), profile.getMbti(), profile.getInterestList(), langs, matchScore
        );
    }
}
