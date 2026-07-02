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
        String mbti,
        List<String> interests,
        List<LanguageResponse> languages
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
                profile.getAvatarUrl(), profile.isActive(),
                profile.getMbti(), profile.getInterestList(), langs
        );
    }
}
