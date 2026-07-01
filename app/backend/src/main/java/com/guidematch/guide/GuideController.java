package com.guidematch.guide;

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

    private final GuideProfileService guideProfileService;
    private final GuideCredentialRepository credentialRepository;
    private final ReviewService reviewService;
    private final FollowService followService;

    public GuideController(GuideProfileService guideProfileService,
                           GuideCredentialRepository credentialRepository,
                           ReviewService reviewService,
                           FollowService followService) {
        this.guideProfileService = guideProfileService;
        this.credentialRepository = credentialRepository;
        this.reviewService = reviewService;
        this.followService = followService;
    }

    @GetMapping
    public List<GuideSummaryResponse> list(@RequestParam(required = false) String region) {
        return guideProfileService.search(region).stream()
                .map(profile -> GuideSummaryResponse.from(
                        profile,
                        guideProfileService.getGuideName(profile.getUserId()),
                        reviewService.averageRating(profile.getId()),
                        reviewService.reviewCount(profile.getId()),
                        followService.followerCount(profile.getId())
                ))
                .toList();
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
