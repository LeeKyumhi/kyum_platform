package com.guidematch.guide;

import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.dto.GuideDetailResponse;
import com.guidematch.guide.dto.GuideSummaryResponse;
import com.guidematch.review.ReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/guides")
public class GuideController {

    private static final List<BookingStatus> CONFIRMED_STATUSES =
            List.of(BookingStatus.ACCEPTED, BookingStatus.COMPLETED);

    private final GuideProfileService guideProfileService;
    private final GuideCredentialRepository credentialRepository;
    private final ReviewService reviewService;
    private final FollowService followService;
    private final BookingRepository bookingRepository;

    public GuideController(GuideProfileService guideProfileService,
                           GuideCredentialRepository credentialRepository,
                           ReviewService reviewService,
                           FollowService followService,
                           BookingRepository bookingRepository) {
        this.guideProfileService = guideProfileService;
        this.credentialRepository = credentialRepository;
        this.reviewService = reviewService;
        this.followService = followService;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping
    public List<GuideSummaryResponse> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double nearLat,
            @RequestParam(required = false) Double nearLng) {
        // city 우선(신규 표준), 없으면 region(레거시). 신규 가이드는 region==city이므로 동일 컬럼으로 검색.
        String cityFilter = (city != null && !city.isBlank()) ? city : region;
        List<GuideProfile> profiles = guideProfileService.search(cityFilter);

        // "내 주변": 좌표가 오면 거리순 정렬 (좌표 없는 가이드는 뒤로).
        if (nearLat != null && nearLng != null) {
            profiles = profiles.stream()
                    .sorted(java.util.Comparator.comparingDouble(p -> distanceOrMax(p, nearLat, nearLng)))
                    .toList();
        }

        return profiles.stream()
                .map(profile -> GuideSummaryResponse.from(
                        profile,
                        guideProfileService.getGuideName(profile.getUserId()),
                        reviewService.averageRating(profile.getId()),
                        reviewService.reviewCount(profile.getId()),
                        followService.followerCount(profile.getId()),
                        bookingRepository.countByGuideProfileIdAndStatusIn(profile.getId(), CONFIRMED_STATUSES)
                ))
                .toList();
    }

    private static double distanceOrMax(GuideProfile p, double lat, double lng) {
        if (p.getLatitude() == null || p.getLongitude() == null) return Double.MAX_VALUE;
        return com.guidematch.geo.GeoUtils.distanceKm(lat, lng, p.getLatitude(), p.getLongitude());
    }

    @GetMapping("/{id}")
    public GuideDetailResponse detail(@PathVariable Long id,
                                      @AuthenticationPrincipal Long userId) {
        GuideProfile profile = guideProfileService.getById(id);
        String guideName = guideProfileService.getGuideName(profile.getUserId());
        List<GuideCredential> credentials = credentialRepository.findByGuideProfileId(profile.getId());
        double avgRating = reviewService.averageRating(profile.getId());
        long reviewCount = reviewService.reviewCount(profile.getId());
        long followerCount = followService.followerCount(profile.getId());
        boolean isFollowing = userId != null && followService.isFollowing(userId, profile.getId());

        return GuideDetailResponse.from(profile, guideName, avgRating, reviewCount,
                followerCount, isFollowing, credentials);
    }
}
