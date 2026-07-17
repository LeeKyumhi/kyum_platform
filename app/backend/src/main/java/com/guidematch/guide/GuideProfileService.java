package com.guidematch.guide;

import com.guidematch.guide.dto.CreateGuideProfileRequest;
import com.guidematch.storage.SupabaseStorageClient;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 가이드 프로필 관련 비즈니스 로직.
 */
@Service
public class GuideProfileService {

    private final GuideProfileRepository guideProfileRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageClient storageClient;
    private final String bucket;

    public GuideProfileService(GuideProfileRepository guideProfileRepository,
                               UserRepository userRepository,
                               SupabaseStorageClient storageClient,
                               @Value("${supabase.storage.credentials-bucket}") String bucket) {
        this.guideProfileRepository = guideProfileRepository;
        this.userRepository = userRepository;
        this.storageClient = storageClient;
        this.bucket = bucket;
    }

    /**
     * 가이드 프로필 생성.
     * @Transactional = 이 메서드 안의 DB 작업들을 하나의 묶음으로 처리.
     *   중간에 실패하면 전부 취소(rollback)되어 "절반만 저장"되는 사고를 막는다.
     */
    @Transactional
    public GuideProfile create(Long userId, CreateGuideProfileRequest request) {
        // 한 사용자당 가이드 프로필은 하나만
        if (guideProfileRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("이미 가이드 프로필이 존재합니다.");
        }

        // 호스트 약관 동의는 필수 — 동의 없이는 프로필을 만들 수 없다(계정·자격 대여 금지 등 고지·동의 기록).
        if (!Boolean.TRUE.equals(request.hostTermsAgreed())) {
            throw new IllegalArgumentException("호스트 약관에 동의해야 가이드로 등록할 수 있습니다.");
        }

        // 통화가 비어있으면 기본 KRW
        String currency = (request.currency() == null || request.currency().isBlank())
                ? "KRW"
                : request.currency();

        GuideProfile profile = new GuideProfile(
                userId,
                request.headline(),
                request.introduction(),
                request.hourlyRate(),
                currency,
                request.region()
        );

        request.languages().forEach(l ->
                profile.addLanguage(new GuideLanguage(l.language(), l.level()))
        );

        if (request.mbti() != null && !request.mbti().isBlank()) {
            profile.setMbti(request.mbti().toUpperCase());
        }
        if (request.interests() != null) {
            profile.setInterestList(request.interests());
        }
        // 제공 서비스 카테고리 — 신규 프로필은 미인증이므로 관광 카테고리를 담으면 거부된다.
        if (request.serviceCategories() != null) {
            profile.setServiceCategoryList(validateCategories(request.serviceCategories(), profile.isVerified()));
        }
        if (request.city() != null && !request.city().isBlank()) {
            profile.updateLocation(request.city(), request.latitude(), request.longitude());
        }
        profile.agreeHostTerms();  // 동의 시각 기록

        return guideProfileRepository.save(profile);
    }

    /**
     * 제공 서비스 카테고리 갱신. 관광(자격 필수) 카테고리는 VERIFIED 가이드만 담을 수 있다
     * (관광/비관광 완전 분리의 프로필 레벨 관문).
     */
    @Transactional
    public GuideProfile updateServiceCategories(Long userId, List<String> categories) {
        GuideProfile profile = getByUserId(userId);
        profile.setServiceCategoryList(validateCategories(categories, profile.isVerified()));
        return guideProfileRepository.save(profile);
    }

    /** 카테고리 키 검증 + 정규화. 유효하지 않은 키·미인증 상태의 관광 카테고리는 거부. */
    private List<String> validateCategories(List<String> keys, boolean verified) {
        if (keys == null || keys.isEmpty()) return List.of();
        return keys.stream().map(key -> {
            ServiceCategory c = ServiceCategory.fromKey(key);
            if (c.requiresGuideLicense() && !verified) {
                throw new IllegalArgumentException("관광 서비스는 관광통역안내사 자격 인증 후에만 제공할 수 있습니다.");
            }
            return c.name();
        }).toList();
    }

    /** 가이드 위치(도시+좌표) 업데이트. */
    @Transactional
    public GuideProfile updateLocation(Long userId, String city, Double latitude, Double longitude) {
        GuideProfile profile = getByUserId(userId);
        profile.updateLocation(city, latitude, longitude);
        return guideProfileRepository.save(profile);
    }

    /**
     * 내 가이드 프로필 조회.
     */
    @Transactional(readOnly = true)
    public GuideProfile getByUserId(Long userId) {
        return guideProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다."));
    }

    /**
     * 가이드 검색 (목록). region이 주어지면 그 지역만, 아니면 전체 활동 중 가이드.
     * languages를 fetch join으로 함께 로딩해 목록 렌더링 시 가이드별 추가 조회를 없앤다.
     */
    @Transactional(readOnly = true)
    public List<GuideProfile> search(String region) {
        if (region != null && !region.isBlank()) {
            return guideProfileRepository.findActiveWithLanguagesByRegion(region);
        }
        return guideProfileRepository.findActiveWithLanguages();
    }

    /**
     * 가이드 프로필 단건 조회 (상세).
     */
    @Transactional(readOnly = true)
    public GuideProfile getById(Long id) {
        return guideProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("가이드를 찾을 수 없습니다."));
    }

    /**
     * 가이드(사용자)의 이름을 가져온다. 목록/상세에 표시하기 위함.
     */
    @Transactional(readOnly = true)
    public String getGuideName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("알 수 없음");
    }

    /**
     * 가이드(사용자)의 성별을 가져온다. 여행자가 성별 선호를 고려할 수 있도록 목록/상세에 표시.
     */
    @Transactional(readOnly = true)
    public String getGuideGender(Long userId) {
        return userRepository.findById(userId)
                .map(User::getGender)
                .orElse(null);
    }

    /**
     * 프로필 사진 업로드. 파일을 Storage에 올리고 그 URL을 프로필에 저장한다.
     */
    @Transactional
    public GuideProfile uploadAvatar(Long userId, MultipartFile file) {
        GuideProfile profile = getByUserId(userId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 사진이 없습니다.");
        }

        // 자격증과 같은 버킷을 쓰되, avatars/ 폴더로 구분
        String path = "avatars/guide-" + profile.getId() + "/" + UUID.randomUUID() + getExtension(file.getOriginalFilename());

        String url;
        try {
            url = storageClient.uploadPublic(bucket, path, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("사진을 읽는 중 오류가 발생했습니다.", e);
        }

        profile.setAvatarUrl(url);
        return guideProfileRepository.save(profile);
    }

    /**
     * MBTI · 관심사 업데이트.
     */
    @Transactional
    public GuideProfile updatePersonality(Long userId, String mbti, java.util.List<String> interests) {
        GuideProfile profile = getByUserId(userId);
        if (mbti != null) profile.setMbti(mbti.isBlank() ? null : mbti.toUpperCase());
        if (interests != null) profile.setInterestList(interests);
        return guideProfileRepository.save(profile);
    }

    /**
     * 가이드 활동 상태 변경 (활동 중 ↔ 일시 중단).
     */
    @Transactional
    public GuideProfile setActive(Long userId, boolean active) {
        GuideProfile profile = getByUserId(userId);
        profile.setActive(active);
        return guideProfileRepository.save(profile);
    }

    /**
     * 즉시 예약 설정 변경 (켜면 예약 요청이 수락 대기 없이 바로 확정된다).
     */
    @Transactional
    public GuideProfile setInstantBooking(Long userId, boolean instantBooking) {
        GuideProfile profile = getByUserId(userId);
        profile.setInstantBooking(instantBooking);
        return guideProfileRepository.save(profile);
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : "";
    }
}
