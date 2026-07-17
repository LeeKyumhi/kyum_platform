package com.guidematch.guide.dto;

import com.guidematch.guide.GuideCredential;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.LanguageLevel;

import java.util.List;

public record GuideDetailResponse(
        Long id,
        Long guideUserId,
        String guideName,
        String headline,
        String introduction,
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
        boolean isFollowing,
        String gender,
        boolean instantBooking,
        String mbti,
        List<String> interests,
        List<LanguageItem> languages,
        List<CredentialResponse> credentials,
        /** 관광통역안내사 자격 인증 상태 (NONE/PENDING/VERIFIED/REJECTED). 배지는 VERIFIED일 때만. */
        String verificationStatus,
        /** 제공 서비스 카테고리 (ServiceCategory 키). 예약 위젯의 서비스 선택·표시에 사용. */
        List<String> serviceCategories
) {
    public record LanguageItem(String language, LanguageLevel level) {}

    public static GuideDetailResponse from(GuideProfile profile, String guideName,
                                           double avgRating, long reviewCount,
                                           long followerCount, boolean isFollowing,
                                           List<GuideCredential> credentials, String guideGender) {
        List<LanguageItem> langs = profile.getLanguages().stream()
                .map(l -> new LanguageItem(l.getLanguage(), l.getLevel()))
                .toList();

        List<CredentialResponse> creds = credentials.stream()
                .map(CredentialResponse::from)
                .toList();

        return new GuideDetailResponse(
                profile.getId(), profile.getUserId(), guideName, profile.getHeadline(), profile.getIntroduction(),
                profile.getHourlyRate(), profile.getCurrency(), profile.getRegion(),
                profile.getCity(), profile.getLatitude(), profile.getLongitude(),
                profile.getAvatarUrl(), avgRating, reviewCount, followerCount, isFollowing,
                guideGender, profile.isInstantBooking(), profile.getMbti(), profile.getInterestList(), langs, creds,
                profile.getVerificationStatus().name(), profile.getServiceCategoryList()
        );
    }
}
