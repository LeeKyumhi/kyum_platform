package com.guidematch.saved;

import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.TourCourse;
import com.guidematch.guide.TourCourseRepository;
import com.guidematch.guide.dto.FollowingGuideResponse;
import com.guidematch.guide.dto.TourCourseResponse;
import com.guidematch.saved.dto.SavedIdsResponse;
import com.guidematch.saved.dto.SavedListResponse;
import com.guidematch.saved.dto.SavedPlaceResponse;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SavedItemService {

    private final SavedItemRepository savedItemRepository;
    private final GuideProfileRepository profileRepository;
    private final TourCourseRepository courseRepository;
    private final UserRepository userRepository;

    public SavedItemService(SavedItemRepository savedItemRepository,
                            GuideProfileRepository profileRepository,
                            TourCourseRepository courseRepository,
                            UserRepository userRepository) {
        this.savedItemRepository = savedItemRepository;
        this.profileRepository = profileRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void saveGuide(Long userId, Long guideProfileId) {
        if (!profileRepository.existsById(guideProfileId)) {
            throw new IllegalArgumentException("가이드를 찾을 수 없습니다.");
        }
        if (savedItemRepository.existsByUserIdAndItemTypeAndRefId(userId, SavedItemType.GUIDE, guideProfileId)) {
            return; // 이미 저장됨 — 무시 (idempotent)
        }
        savedItemRepository.save(new SavedItem(userId, SavedItemType.GUIDE, guideProfileId));
    }

    @Transactional
    public void saveCourse(Long userId, Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new IllegalArgumentException("코스를 찾을 수 없습니다.");
        }
        if (savedItemRepository.existsByUserIdAndItemTypeAndRefId(userId, SavedItemType.COURSE, courseId)) {
            return;
        }
        savedItemRepository.save(new SavedItem(userId, SavedItemType.COURSE, courseId));
    }

    @Transactional
    public void savePlace(Long userId, String ref, String name, String category,
                          String address, Double lat, Double lng, String image) {
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException("장소 참조가 없습니다.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("장소 이름이 없습니다.");
        if (savedItemRepository.existsByUserIdAndItemTypeAndPlaceRef(userId, SavedItemType.PLACE, ref.trim())) {
            return;
        }
        savedItemRepository.save(new SavedItem(userId, ref.trim(), name.trim(), category, address, lat, lng, image));
    }

    /** 해제 — 없는 항목 해제는 조용히 성공 (idempotent, Follow.unfollow 패턴). */
    @Transactional
    public void unsave(Long userId, SavedItemType type, Long refId, String placeRef) {
        if (type == SavedItemType.PLACE) {
            savedItemRepository.findByUserIdAndItemTypeAndPlaceRef(userId, type, placeRef)
                    .ifPresent(savedItemRepository::delete);
        } else {
            savedItemRepository.findByUserIdAndItemTypeAndRefId(userId, type, refId)
                    .ifPresent(savedItemRepository::delete);
        }
    }

    /** 카드 ♡ 초기 상태용 — 저장한 참조 3종 일괄. */
    @Transactional(readOnly = true)
    public SavedIdsResponse myIds(Long userId) {
        List<SavedItem> items = savedItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return new SavedIdsResponse(
                items.stream().filter(s -> s.getItemType() == SavedItemType.GUIDE).map(SavedItem::getRefId).toList(),
                items.stream().filter(s -> s.getItemType() == SavedItemType.COURSE).map(SavedItem::getRefId).toList(),
                items.stream().filter(s -> s.getItemType() == SavedItemType.PLACE).map(SavedItem::getPlaceRef).toList()
        );
    }

    /**
     * /saved 페이지용 목록 3종. 가이드/코스는 원본을 배치 재조회(원격 DB — 개별 조회 금지, N+1 방지)
     * 하고, 사라진 가이드·비활성 코스는 조용히 제외한다.
     */
    @Transactional(readOnly = true)
    public SavedListResponse myList(Long userId) {
        List<SavedItem> items = savedItemRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 가이드: 배치 조회 → 저장 순서 유지 조립
        List<Long> guideIds = items.stream()
                .filter(s -> s.getItemType() == SavedItemType.GUIDE).map(SavedItem::getRefId).toList();
        Map<Long, GuideProfile> profiles = new HashMap<>();
        if (!guideIds.isEmpty()) {
            profileRepository.findAllById(guideIds).forEach(p -> profiles.put(p.getId(), p));
        }
        Map<Long, String> names = new HashMap<>();
        if (!profiles.isEmpty()) {
            List<Long> userIds = profiles.values().stream().map(GuideProfile::getUserId).toList();
            userRepository.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getFullName()));
        }
        List<FollowingGuideResponse> guides = guideIds.stream()
                .map(profiles::get)
                .filter(p -> p != null)
                .map(p -> FollowingGuideResponse.from(p, names.getOrDefault(p.getUserId(), "Unknown")))
                .toList();

        // 코스: 배치 조회 → active만
        List<Long> courseIds = items.stream()
                .filter(s -> s.getItemType() == SavedItemType.COURSE).map(SavedItem::getRefId).toList();
        Map<Long, TourCourse> courseMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseRepository.findAllById(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }
        List<TourCourse> activeCourses = courseIds.stream()
                .map(courseMap::get)
                .filter(c -> c != null && c.isActive())
                .toList();

        // 코스가 속한 가이드도 배치 조회 (가이드 분기와 동일한 패턴 — 코스마다 findById 호출 시 N+1 발생)
        List<Long> courseProfileIds = activeCourses.stream()
                .map(TourCourse::getGuideProfileId).distinct().toList();
        Map<Long, GuideProfile> courseProfiles = new HashMap<>();
        if (!courseProfileIds.isEmpty()) {
            profileRepository.findAllById(courseProfileIds).forEach(p -> courseProfiles.put(p.getId(), p));
        }
        Map<Long, String> courseGuideNames = new HashMap<>();
        if (!courseProfiles.isEmpty()) {
            List<Long> courseUserIds = courseProfiles.values().stream().map(GuideProfile::getUserId).toList();
            userRepository.findAllById(courseUserIds).forEach(u -> courseGuideNames.put(u.getId(), u.getFullName()));
        }
        List<TourCourseResponse> courses = activeCourses.stream()
                .map(c -> {
                    GuideProfile p = courseProfiles.get(c.getGuideProfileId());
                    String name = p != null ? courseGuideNames.getOrDefault(p.getUserId(), "Unknown") : null;
                    return TourCourseResponse.from(c, name, p != null ? p.getAvatarUrl() : null);
                })
                .toList();

        // 장소: 스냅샷 그대로
        List<SavedPlaceResponse> places = items.stream()
                .filter(s -> s.getItemType() == SavedItemType.PLACE)
                .map(SavedPlaceResponse::from)
                .toList();

        return new SavedListResponse(guides, courses, places);
    }

    /** 저장수 배치 집계 — 저장 0건 대상은 맵에 없음(프론트에서 ?? 0). */
    @Transactional(readOnly = true)
    public Map<Long, Long> counts(SavedItemType type, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : savedItemRepository.countsByTypeAndRefIds(type, ids)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }
}
