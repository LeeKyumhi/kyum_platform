package com.guidematch.admin;

import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.GuideVerification;
import com.guidematch.guide.GuideVerificationRepository;
import com.guidematch.guide.VerificationStatus;
import com.guidematch.storage.SupabaseStorageClient;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자용 자격 인증 심사 로직. /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN으로 보호된다.
 */
@Service
public class AdminVerificationService {

    /** 어드민 서류 열람용 서명 URL 유효 시간 (10분). */
    private static final int SIGNED_URL_TTL_SECONDS = 600;

    private final GuideVerificationRepository verificationRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageClient storageClient;
    private final String verificationBucket;

    public AdminVerificationService(GuideVerificationRepository verificationRepository,
                                    GuideProfileRepository guideProfileRepository,
                                    UserRepository userRepository,
                                    SupabaseStorageClient storageClient,
                                    @Value("${supabase.storage.verification-bucket}") String verificationBucket) {
        this.verificationRepository = verificationRepository;
        this.guideProfileRepository = guideProfileRepository;
        this.userRepository = userRepository;
        this.storageClient = storageClient;
        this.verificationBucket = verificationBucket;
    }

    /** 관리자 심사 화면에 노출할 신청 1건 (제출 필드 + 파일 URL + 가이드 이름). */
    public record PendingItem(
            Long id,
            Long guideProfileId,
            String guideName,
            String legalName,
            String licenseNumber,
            String licenseIssueDate,
            String licenseInnerNumber,
            String licenseFileUrl,
            String idDocFileUrl,
            VerificationStatus status
    ) {}

    /**
     * 심사 대기(PENDING) 목록. 가이드 이름은 프로필→유저를 배치 조회로 붙인다(N+1 방지).
     */
    @Transactional(readOnly = true)
    public List<PendingItem> listPending() {
        List<GuideVerification> pending =
                verificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING);
        if (pending.isEmpty()) return List.of();

        List<Long> profileIds = pending.stream().map(GuideVerification::getGuideProfileId).toList();
        Map<Long, GuideProfile> profiles = new HashMap<>();
        guideProfileRepository.findAllById(profileIds).forEach(p -> profiles.put(p.getId(), p));

        List<Long> userIds = profiles.values().stream().map(GuideProfile::getUserId).toList();
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));

        return pending.stream().map(v -> {
            GuideProfile profile = profiles.get(v.getGuideProfileId());
            User user = profile != null ? users.get(profile.getUserId()) : null;
            return new PendingItem(
                    v.getId(),
                    v.getGuideProfileId(),
                    user != null ? user.getFullName() : "알 수 없음",
                    v.getLegalName(),
                    v.getLicenseNumber(),
                    v.getLicenseIssueDate() != null ? v.getLicenseIssueDate().toString() : null,
                    v.getLicenseInnerNumber(),
                    // 저장된 값은 비공개 버킷의 경로 — 어드민 열람용 만료형 서명 URL로 변환해 내려준다.
                    storageClient.createSignedUrl(verificationBucket, v.getLicenseFileUrl(), SIGNED_URL_TTL_SECONDS),
                    storageClient.createSignedUrl(verificationBucket, v.getIdDocFileUrl(), SIGNED_URL_TTL_SECONDS),
                    v.getStatus()
            );
        }).toList();
    }

    /** 승인 — 인증 상태를 VERIFIED로, 프로필 비정규화 컬럼도 동기화. */
    @Transactional
    public void approve(Long verificationId, Long adminUserId) {
        GuideVerification v = getForReview(verificationId);
        v.approve(adminUserId);
        syncProfileStatus(v.getGuideProfileId(), VerificationStatus.VERIFIED);
    }

    /** 반려 — 사유 필수. 프로필 비정규화 컬럼도 REJECTED로 동기화. */
    @Transactional
    public void reject(Long verificationId, Long adminUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("반려 사유를 입력하세요.");
        }
        GuideVerification v = getForReview(verificationId);
        v.reject(adminUserId, reason.trim());
        syncProfileStatus(v.getGuideProfileId(), VerificationStatus.REJECTED);
    }

    private GuideVerification getForReview(Long verificationId) {
        GuideVerification v = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 인증 신청입니다."));
        if (v.getStatus() != VerificationStatus.PENDING) {
            throw new IllegalArgumentException("이미 심사된 신청입니다.");
        }
        return v;
    }

    private void syncProfileStatus(Long guideProfileId, VerificationStatus status) {
        guideProfileRepository.findById(guideProfileId).ifPresent(profile -> {
            profile.setVerificationStatus(status);
            guideProfileRepository.save(profile);
        });
    }
}
