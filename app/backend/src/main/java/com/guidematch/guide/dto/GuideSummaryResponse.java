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
        String avatarUrl,
        double avgRating,
        long reviewCount,
        long followerCount,
        String mbti,
        List<String> interests,
        List<LanguageItem> languages
) {
    public record LanguageItem(String language, LanguageLevel level) {}

    public static GuideSummaryResponse from(GuideProfile profile, String guideName,
                                            double avgRating, long reviewCount, long followerCount) {
        List<LanguageItem> langs = profile.getLanguages().stream()
                .map(l -> new LanguageItem(l.getLanguage(), l.getLevel()))
                .toList();

        return new GuideSummaryResponse(
                profile.getId(), guideName, profile.getHeadline(),
                profile.getHourlyRate(), profile.getCurrency(), profile.getRegion(),
                profile.getAvatarUrl(), avgRating, reviewCount, followerCount,
                profile.getMbti(), profile.getInterestList(), langs
        );
    }
}
