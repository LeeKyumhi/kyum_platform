package com.guidematch.guide;

import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.dto.GuideDetailResponse;
import com.guidematch.guide.dto.GuideSummaryResponse;
import com.guidematch.review.ReviewRepository;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/guides")
public class GuideController {

    private static final List<BookingStatus> CONFIRMED_STATUSES =
            List.of(BookingStatus.ACCEPTED, BookingStatus.COMPLETED);

    private final GuideProfileService guideProfileService;
    private final GuideCredentialRepository credentialRepository;
    private final ReviewRepository reviewRepository;
    private final FollowService followService;
    private final FollowRepository followRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final AvailableSlotRepository slotRepository;

    public GuideController(GuideProfileService guideProfileService,
                           GuideCredentialRepository credentialRepository,
                           ReviewRepository reviewRepository,
                           FollowService followService,
                           FollowRepository followRepository,
                           BookingRepository bookingRepository,
                           UserRepository userRepository,
                           AvailableSlotRepository slotRepository) {
        this.guideProfileService = guideProfileService;
        this.credentialRepository = credentialRepository;
        this.reviewRepository = reviewRepository;
        this.followService = followService;
        this.followRepository = followRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
    }

    /**
     * 가이드 목록. DB가 원격(풀러)이라 왕복 지연이 크므로,
     * 가이드별 개별 조회 대신 사용자/평점/팔로워/예약수를 각각 한 번의 쿼리로 일괄 집계한다.
     */
    @GetMapping
    public List<GuideSummaryResponse> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double nearLat,
            @RequestParam(required = false) Double nearLng,
            @RequestParam(required = false) String availableFrom,
            @RequestParam(required = false) String availableTo,
            @RequestParam(required = false) String lang,
            @AuthenticationPrincipal Long viewerId) {
        // city 우선(신규 표준), 없으면 region(레거시). 신규 가이드는 region==city이므로 동일 컬럼으로 검색.
        String cityFilter = (city != null && !city.isBlank()) ? city : region;
        List<GuideProfile> profiles = guideProfileService.search(cityFilter);

        // 날짜 필터: 해당 기간에 가능 슬롯을 등록한 가이드만 (검색바 체크인·체크아웃 연동).
        // 날짜 형식이 잘못되면 필터를 조용히 건너뛴다 (검색 자체는 계속 동작).
        if (availableFrom != null && !availableFrom.isBlank()
                && availableTo != null && !availableTo.isBlank()) {
            try {
                java.time.LocalDateTime from = java.time.LocalDate.parse(availableFrom).atStartOfDay();
                java.time.LocalDateTime to = java.time.LocalDate.parse(availableTo).plusDays(1).atStartOfDay();
                java.util.Set<Long> withSlots =
                        new java.util.HashSet<>(slotRepository.guideProfileIdsWithSlotBetween(from, to));
                profiles = profiles.stream().filter(p -> withSlots.contains(p.getId())).toList();
            } catch (java.time.format.DateTimeParseException ignored) {
            }
        }

        // "내 주변": 좌표가 오면 거리순 정렬 (좌표 없는 가이드는 뒤로).
        if (nearLat != null && nearLng != null) {
            profiles = profiles.stream()
                    .sorted(java.util.Comparator.comparingDouble(p -> distanceOrMax(p, nearLat, nearLng)))
                    .toList();
        }

        return buildSummaries(profiles, viewerId, lang);
    }

    /**
     * 가이드 목록/유사 가이드 추천에서 공통으로 쓰는 요약 DTO 조립.
     * 사용자/평점/팔로워/예약수를 각각 한 번의 쿼리로 일괄 집계해 가이드별 추가 조회를 없앤다.
     */
    private List<GuideSummaryResponse> buildSummaries(List<GuideProfile> profiles, Long viewerId, String lang) {
        if (profiles.isEmpty()) return List.of();

        List<Long> profileIds = profiles.stream().map(GuideProfile::getId).toList();
        List<Long> userIds = profiles.stream().map(GuideProfile::getUserId).toList();

        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));

        Map<Long, double[]> ratingStats = new HashMap<>(); // id → [avg, count]
        reviewRepository.ratingStatsByGuideProfileIds(profileIds).forEach(row ->
                ratingStats.put((Long) row[0], new double[]{(Double) row[1], (Long) row[2]}));

        Map<Long, Long> followerCounts = new HashMap<>();
        followRepository.followerCountsByGuideProfileIds(profileIds).forEach(row ->
                followerCounts.put((Long) row[0], (Long) row[1]));

        Map<Long, Long> bookingCounts = new HashMap<>();
        bookingRepository.bookingCountsByGuideProfileIds(profileIds, CONFIRMED_STATUSES).forEach(row ->
                bookingCounts.put((Long) row[0], (Long) row[1]));

        // 궁합 점수: 로그인한 사용자 한 명만 추가 조회 (가이드별 추가 쿼리 없음 — 전부 메모리 계산)
        User viewer = viewerId != null ? userRepository.findById(viewerId).orElse(null) : null;

        return profiles.stream()
                .map(profile -> {
                    User user = users.get(profile.getUserId());
                    double[] rating = ratingStats.getOrDefault(profile.getId(), new double[]{0, 0});
                    return GuideSummaryResponse.from(
                            profile,
                            user != null ? user.getFullName() : "알 수 없음",
                            rating[0],
                            (long) rating[1],
                            followerCounts.getOrDefault(profile.getId(), 0L),
                            bookingCounts.getOrDefault(profile.getId(), 0L),
                            user != null ? user.getGender() : null,
                            // 본인이 가이드로도 활동 중이면 자기 카드엔 궁합을 붙이지 않는다
                            profile.getUserId().equals(viewerId)
                                    ? null : MatchScore.compute(viewer, profile, lang)
                    );
                })
                .toList();
    }

    private static double distanceOrMax(GuideProfile p, double lat, double lng) {
        if (p.getLatitude() == null || p.getLongitude() == null) return Double.MAX_VALUE;
        return com.guidematch.geo.GeoUtils.distanceKm(lat, lng, p.getLatitude(), p.getLongitude());
    }

    /**
     * 유사 가이드 추천 (최대 3명). 같은 도시를 우선하고, 로그인 여행자에게 궁합 점수 근거가 있으면
     * 궁합순, 없으면 평점·리뷰수순으로 정렬한다. 목록과 동일한 일괄 집계 경로를 재사용해 N+1을 피한다.
     */
    @GetMapping("/{id}/similar")
    public List<GuideSummaryResponse> similar(
            @PathVariable Long id,
            @RequestParam(required = false) String lang,
            @AuthenticationPrincipal Long viewerId) {
        GuideProfile target = guideProfileService.getById(id);

        List<GuideProfile> pool = guideProfileService.search(null).stream()
                .filter(p -> !p.getId().equals(target.getId()))
                .filter(p -> viewerId == null || !p.getUserId().equals(viewerId))
                .toList();

        List<GuideSummaryResponse> summaries = buildSummaries(pool, viewerId, lang);
        boolean rankByMatch = summaries.stream().anyMatch(s -> s.matchScore() != null);

        Comparator<GuideSummaryResponse> byCity = Comparator.comparingInt(
                s -> (target.getCity() != null && target.getCity().equals(s.city())) ? 0 : 1);
        Comparator<GuideSummaryResponse> byRank = rankByMatch
                ? Comparator.comparingInt((GuideSummaryResponse s) -> s.matchScore() == null ? -1 : s.matchScore()).reversed()
                : Comparator.comparingDouble(GuideSummaryResponse::avgRating).reversed()
                        .thenComparing(Comparator.comparingLong(GuideSummaryResponse::reviewCount).reversed());

        return summaries.stream()
                .sorted(byCity.thenComparing(byRank))
                .limit(3)
                .toList();
    }

    @GetMapping("/{id}")
    public GuideDetailResponse detail(@PathVariable Long id,
                                      @AuthenticationPrincipal Long userId) {
        GuideProfile profile = guideProfileService.getById(id);
        // 이름과 성별은 같은 users 행이므로 한 번만 조회한다.
        User guideUser = userRepository.findById(profile.getUserId()).orElse(null);
        String guideName = guideUser != null ? guideUser.getFullName() : "알 수 없음";
        String guideGender = guideUser != null ? guideUser.getGender() : null;
        List<GuideCredential> credentials = credentialRepository.findByGuideProfileId(profile.getId());
        // 평균과 개수를 한 번의 집계 쿼리로 조회 (원격 DB 왕복 1회 절약)
        double avgRating = 0;
        long reviewCount = 0;
        for (Object[] row : reviewRepository.ratingStatsByGuideProfileIds(List.of(profile.getId()))) {
            avgRating = (Double) row[1];
            reviewCount = (Long) row[2];
        }
        long followerCount = followService.followerCount(profile.getId());
        boolean isFollowing = userId != null && followService.isFollowing(userId, profile.getId());

        return GuideDetailResponse.from(profile, guideName, avgRating, reviewCount,
                followerCount, isFollowing, credentials, guideGender);
    }
}
