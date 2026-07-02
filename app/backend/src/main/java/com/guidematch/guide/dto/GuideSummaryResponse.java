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
        String mbti,
        List<String> interests,
        List<LanguageItem> languages
) {
    public record LanguageItem(String language, LanguageLevel level) {}

    public static GuideSummaryResponse from(GuideProfile profile, String guideName,
                                            double avgRating, long reviewCount, long followerCount,
                                            long bookingCount) {
        List<LanguageItem> langs = profile.getLanguages().stream()
                .map(l -> new LanguageItem(l.getLanguage(), l.getLevel()))
                .toList();

        return new GuideSummaryResponse(
                profile.getId(), guideName, profile.getHeadline(),
                profile.getHourlyRate(), profile.getCurrency(), profile.getRegion(),
                profile.getCity(), profile.getLatitude(), profile.getLongitude(),
                profile.getAvatarUrl(), avgRating, reviewCount, followerCount, bookingCount,
                profile.getMbti(), profile.getInterestList(), langs
        );
    }
}
