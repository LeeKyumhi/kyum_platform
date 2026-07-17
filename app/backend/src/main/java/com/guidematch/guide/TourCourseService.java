package com.guidematch.guide;

import com.guidematch.guide.dto.TourCourseResponse;
import com.guidematch.guide.dto.TourCourseWaypointRequest;
import com.guidematch.storage.SupabaseStorageClient;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TourCourseService {

    private final TourCourseRepository courseRepository;
    private final GuideProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageClient storageClient;
    private final String bucket;

    public TourCourseService(TourCourseRepository courseRepository,
                             GuideProfileRepository profileRepository,
                             UserRepository userRepository,
                             SupabaseStorageClient storageClient,
                             @Value("${supabase.storage.credentials-bucket}") String bucket) {
        this.courseRepository = courseRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.storageClient = storageClient;
        this.bucket = bucket;
    }

    /** 코스 등록 (가이드 프로필 필수) */
    @Transactional
    public TourCourseResponse create(Long userId, String title, String description, String city,
                                     Integer durationHours, Integer price, Integer maxPeople,
                                     String serviceCategory, MultipartFile image,
                                     List<TourCourseWaypointRequest> waypoints) {
        GuideProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다. 먼저 가이드로 등록해주세요."));

        if (title == null || title.isBlank()) throw new IllegalArgumentException("코스 이름은 필수입니다.");
        if (durationHours == null || durationHours < 1) throw new IllegalArgumentException("소요 시간은 1시간 이상이어야 합니다.");
        if (price == null || price < 0) throw new IllegalArgumentException("가격이 올바르지 않습니다.");
        if (maxPeople == null || maxPeople < 1) throw new IllegalArgumentException("최대 인원은 1명 이상이어야 합니다.");
        ServiceCategory category = requireAllowedCategory(serviceCategory, profile);

        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            String path = "courses/guide-" + profile.getId() + "/" + UUID.randomUUID() + extension(image.getOriginalFilename());
            try {
                imageUrl = storageClient.uploadPublic(bucket, path, image.getBytes(), image.getContentType());
            } catch (IOException e) {
                throw new RuntimeException("이미지 업로드 중 오류가 발생했습니다.", e);
            }
        }

        TourCourse course = new TourCourse(
                profile.getId(), title.trim(),
                description != null ? description.trim() : null,
                (city != null && !city.isBlank()) ? city : profile.getCity(),
                durationHours, price, profile.getCurrency(), maxPeople, imageUrl, category
        );
        course.replaceWaypoints(toWaypoints(waypoints));
        // 새 동선의 DB id를 응답에 채우기 위해 즉시 flush (기본은 커밋 시점에 flush돼 id가 null로 나감)
        return TourCourseResponse.from(courseRepository.saveAndFlush(course));
    }

    /** 코스 수정 (본인 것만) — 메타 + 동선 통째로 교체 */
    @Transactional
    public TourCourseResponse update(Long userId, Long courseId, String title, String description, String city,
                                     Integer durationHours, Integer price, Integer maxPeople,
                                     String serviceCategory, MultipartFile image,
                                     List<TourCourseWaypointRequest> waypoints) {
        GuideProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다."));
        TourCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));
        if (!course.getGuideProfileId().equals(profile.getId())) {
            throw new SecurityException("본인 코스만 수정할 수 있습니다.");
        }

        if (title == null || title.isBlank()) throw new IllegalArgumentException("코스 이름은 필수입니다.");
        if (durationHours == null || durationHours < 1) throw new IllegalArgumentException("소요 시간은 1시간 이상이어야 합니다.");
        if (price == null || price < 0) throw new IllegalArgumentException("가격이 올바르지 않습니다.");
        if (maxPeople == null || maxPeople < 1) throw new IllegalArgumentException("최대 인원은 1명 이상이어야 합니다.");
        ServiceCategory category = requireAllowedCategory(serviceCategory, profile);

        String imageUrl = course.getImageUrl();
        if (image != null && !image.isEmpty()) {
            String path = "courses/guide-" + profile.getId() + "/" + UUID.randomUUID() + extension(image.getOriginalFilename());
            try {
                imageUrl = storageClient.uploadPublic(bucket, path, image.getBytes(), image.getContentType());
            } catch (IOException e) {
                throw new RuntimeException("이미지 업로드 중 오류가 발생했습니다.", e);
            }
        }

        course.updateMeta(title.trim(), description != null ? description.trim() : null,
                (city != null && !city.isBlank()) ? city : profile.getCity(),
                durationHours, price, maxPeople, imageUrl, category);
        course.replaceWaypoints(toWaypoints(waypoints));

        // 새 동선의 DB id를 응답에 채우기 위해 즉시 flush
        return TourCourseResponse.from(courseRepository.saveAndFlush(course));
    }

    /**
     * 코스 카테고리 게이팅: 관광(자격 필수) 카테고리는 VERIFIED 가이드만 등록할 수 있다.
     * 관광/비관광 완전 분리의 코스 레벨 관문.
     */
    private ServiceCategory requireAllowedCategory(String serviceCategory, GuideProfile profile) {
        ServiceCategory category = ServiceCategory.fromKey(serviceCategory);
        if (category.requiresGuideLicense() && !profile.isVerified()) {
            throw new IllegalArgumentException("관광 코스는 관광통역안내사 자격 인증 후에만 등록할 수 있습니다.");
        }
        return category;
    }

    private List<TourCourseWaypoint> toWaypoints(List<TourCourseWaypointRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream()
                .filter(r -> r.placeName() != null && !r.placeName().isBlank())
                .map(r -> new TourCourseWaypoint(r.sortOrder(), r.placeId(), r.placeName(),
                        r.category(), r.address(), r.latitude(), r.longitude(),
                        r.startHour(), r.durationHours(), r.laneIndex(), r.laneSpan()))
                .toList();
    }

    /** 내 코스 목록 (비활성 포함) */
    @Transactional(readOnly = true)
    public List<TourCourseResponse> listMine(Long userId) {
        GuideProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다."));
        return courseRepository.findByGuideProfileIdOrderByCreatedAtDesc(profile.getId())
                .stream().map(TourCourseResponse::from).toList();
    }

    /** 특정 가이드의 활성 코스 목록 (공개 — 가이드 상세용). 미분류(카테고리 null) 코스는 숨긴다. */
    @Transactional(readOnly = true)
    public List<TourCourseResponse> listByGuide(Long guideProfileId) {
        return courseRepository.findByGuideProfileIdAndActiveTrueOrderByCreatedAtDesc(guideProfileId)
                .stream()
                .filter(c -> c.getServiceCategory() != null)
                .map(TourCourseResponse::from).toList();
    }

    /**
     * 전체 활성 코스 (공개 — 탐색용). 가이드 이름/아바타를 일괄 조회로 채운다
     * (원격 DB이므로 코스별 개별 조회 금지 — N+1 방지 배치 패턴).
     */
    @Transactional(readOnly = true)
    public List<TourCourseResponse> listAll(String city) {
        List<TourCourse> courses = ((city != null && !city.isBlank())
                ? courseRepository.findByActiveTrueAndCityIgnoreCaseOrderByCreatedAtDesc(city)
                : courseRepository.findByActiveTrueOrderByCreatedAtDesc())
                .stream()
                .filter(c -> c.getServiceCategory() != null)  // 미분류(레거시) 코스는 공개 탐색에서 숨김
                .toList();
        if (courses.isEmpty()) return List.of();

        List<Long> profileIds = courses.stream().map(TourCourse::getGuideProfileId).distinct().toList();
        Map<Long, GuideProfile> profiles = new HashMap<>();
        profileRepository.findAllById(profileIds).forEach(p -> profiles.put(p.getId(), p));

        List<Long> userIds = profiles.values().stream().map(GuideProfile::getUserId).toList();
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getFullName()));

        return courses.stream()
                .map(c -> {
                    GuideProfile p = profiles.get(c.getGuideProfileId());
                    String name = p != null ? names.getOrDefault(p.getUserId(), "Unknown") : "Unknown";
                    String avatar = p != null ? p.getAvatarUrl() : null;
                    return TourCourseResponse.from(c, name, avatar);
                })
                .toList();
    }

    /** 코스 삭제 (본인 것만) */
    @Transactional
    public void delete(Long userId, Long courseId) {
        GuideProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다."));
        TourCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));
        if (!course.getGuideProfileId().equals(profile.getId())) {
            throw new SecurityException("본인 코스만 삭제할 수 있습니다.");
        }
        courseRepository.delete(course);
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
