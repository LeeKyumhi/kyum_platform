package com.guidematch.guide;

import com.guidematch.user.User;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * "PeerUp 궁합" — 여행자와 가이드가 얼마나 잘 맞는지 0~99 점수.
 *
 * 재미 요소가 섞인 휴리스틱이지 정밀한 지표가 아니다. 구성:
 *   기본 50 + 공통 관심사(개당 8, 최대 24) + 언어 일치 12 + 같은 도시 7 + MBTI 케미(글자당 3, 최대 12)
 *
 * 여행자가 관심사도 MBTI도 입력하지 않았다면 개인화할 근거가 없으므로 null을 반환한다
 * (전원 50%로 도배된 배지는 신뢰를 깎기만 한다).
 */
public final class MatchScore {

    private MatchScore() {
    }

    public static Integer compute(User viewer, GuideProfile guide, String viewerLang) {
        if (viewer == null) return null;
        boolean hasInterests = !viewer.getInterestList().isEmpty();
        boolean hasMbti = viewer.getMbti() != null && viewer.getMbti().length() == 4;
        if (!hasInterests && !hasMbti) return null;

        int score = 50;

        if (hasInterests) {
            Set<String> mine = new HashSet<>(viewer.getInterestList());
            long common = guide.getInterestList().stream().filter(mine::contains).count();
            score += (int) Math.min(common, 3) * 8;
        }

        if (hasMbti && guide.getMbti() != null && guide.getMbti().length() == 4) {
            String a = viewer.getMbti().toUpperCase(Locale.ROOT);
            String b = guide.getMbti().toUpperCase(Locale.ROOT);
            for (int i = 0; i < 4; i++) {
                if (a.charAt(i) == b.charAt(i)) score += 3;
            }
        }

        if (speaksViewerLang(guide, viewerLang)) score += 12;

        if (viewer.getCity() != null && viewer.getCity().equals(guide.getCity())) score += 7;

        return Math.min(score, 99);
    }

    /** 가이드 언어는 자유 입력 텍스트라 UI 언어(ko/en/zh)별 흔한 표기를 느슨하게 매칭한다. */
    private static boolean speaksViewerLang(GuideProfile guide, String viewerLang) {
        if (viewerLang == null) return false;
        return guide.getLanguages().stream().anyMatch(l -> {
            String name = l.getLanguage().toLowerCase(Locale.ROOT);
            return switch (viewerLang) {
                case "ko" -> name.contains("한국") || name.contains("korean");
                case "en" -> name.contains("영어") || name.contains("english");
                case "zh" -> name.contains("중국") || name.contains("chinese")
                        || name.contains("中文") || name.contains("汉语") || name.contains("普通话");
                default -> false;
            };
        });
    }
}
