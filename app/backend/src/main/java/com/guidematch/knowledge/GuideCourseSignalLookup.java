package com.guidematch.knowledge;

import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.TourCourse;
import com.guidematch.guide.TourCourseRepository;
import com.guidematch.guide.TourCourseWaypoint;
import com.guidematch.guide.VerificationStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🎫 "인증 가이드가 코스에 담은 곳" — 장소별 인증 가이드 수.
 *
 * <p><b>이것이 플라이휠의 심장이다.</b> 가이드만 코스를 만들 수 있고 관광 카테고리는
 * 자격 인증이 필요하기 때문에, "인증 가이드 N명이 이 장소를 코스에 넣었다"는
 * 블로그 크롤링으로는 만들 수 없는 신호가 된다. 그 주장이 참이려면 세 가지가 지켜져야 한다:
 *
 * <ol>
 *   <li><b>사람 단위로 센다</b> — 코스나 정차지 수로 세면 한 가이드가 코스 3개를 만들었을 때 "3명"이 된다.</li>
 *   <li><b>VERIFIED만 센다</b> — {@code TourCourseService}의 자격 게이팅은
 *       {@code requiresGuideLicense()} 카테고리에만 걸린다. 미인증 가이드도 다른 카테고리 코스는
 *       만들 수 있으므로, 필터가 없으면 배지 문구가 그대로 거짓이 된다.</li>
 *   <li><b>활성 코스만 센다</b> — 판매하지 않는 코스는 근거가 아니다.</li>
 * </ol>
 *
 * <p>세 규칙을 쿼리가 아니라 여기 두는 이유: 이 저장소에는 DB를 띄우는 테스트가 없다.
 * 규칙이 JPQL 문자열 안에 있으면 어떤 테스트로도 물 수 없고, 코스 데이터가 거의 없는 지금은
 * "집계가 틀린 것"과 "데이터가 없는 것"이 화면에서 똑같이 보인다 — 조용히 영원히 안 켜진다.
 *
 * <p>{@link PlaceInsightLookup}과 같은 이유로 배치 조회만 노출한다. 단건 조회를 만들면
 * 루프 안에서 불리고, Supabase 시드니 왕복 250ms가 그대로 응답 시간에 얹힌다.
 */
@Service
public class GuideCourseSignalLookup {

    private final TourCourseRepository courseRepo;
    private final GuideProfileRepository guideRepo;

    public GuideCourseSignalLookup(TourCourseRepository courseRepo, GuideProfileRepository guideRepo) {
        this.courseRepo = courseRepo;
        this.guideRepo = guideRepo;
    }

    /**
     * Kakao place id → 그 장소를 활성 코스에 담은 <b>인증 가이드 수</b>.
     *
     * @return 근거가 있는 장소만. <b>0인 장소는 키가 아예 없다</b> — 0을 실어 보내면
     *         호출부가 "0명이 담았어요"를 만들 수 있게 된다(규칙2 위반). 없으면 없는 것이다.
     */
    public Map<String, Integer> verifiedGuideCounts(Collection<String> kakaoPlaceIds) {
        List<String> ids = kakaoPlaceIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct().toList();
        if (ids.isEmpty()) return Map.of();

        List<TourCourse> courses = courseRepo.findByWaypointsPlaceIdIn(ids);
        List<TourCourse> active = courses.stream().filter(TourCourse::isActive).toList();
        if (active.isEmpty()) return Map.of();

        Set<Long> verifiedGuideIds = verifiedAmong(
                active.stream().map(TourCourse::getGuideProfileId).filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet()));
        if (verifiedGuideIds.isEmpty()) return Map.of();

        Set<String> wanted = new HashSet<>(ids);
        Map<String, Set<Long>> guidesByPlace = new HashMap<>();
        for (TourCourse c : active) {
            if (!verifiedGuideIds.contains(c.getGuideProfileId())) continue;
            for (TourCourseWaypoint w : c.getWaypoints()) {
                String placeId = w.getPlaceId();
                // 수기 입력 장소는 placeId가 null이고, 과거 프론트가 합성하던 "rec-3-경복궁" 같은
                // 값도 남아 있다. 둘 다 wanted에 없으므로 여기서 조용히 걸러진다.
                if (placeId == null || !wanted.contains(placeId)) continue;
                guidesByPlace.computeIfAbsent(placeId, k -> new HashSet<>()).add(c.getGuideProfileId());
            }
        }

        Map<String, Integer> counts = new HashMap<>();
        guidesByPlace.forEach((placeId, guides) -> counts.put(placeId, guides.size()));
        return counts;
    }

    private Set<Long> verifiedAmong(Set<Long> guideProfileIds) {
        if (guideProfileIds.isEmpty()) return Set.of();
        Set<Long> verified = new HashSet<>();
        for (GuideProfile g : guideRepo.findAllById(guideProfileIds)) {
            if (g.getVerificationStatus() == VerificationStatus.VERIFIED) verified.add(g.getId());
        }
        return verified;
    }
}
