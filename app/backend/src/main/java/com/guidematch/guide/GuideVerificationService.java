package com.guidematch.guide;

import com.guidematch.storage.SupabaseStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * 관광통역안내사 자격 인증 신청/심사 로직.
 * 파일 업로드는 GuideCredentialService / GuideProfileService.uploadAvatar와 동일하게
 * SupabaseStorageClient + credentials 버킷을 재사용한다.
 */
@Service
public class GuideVerificationService {

    private final GuideVerificationRepository verificationRepository;
    private final GuideProfileService guideProfileService;
    private final GuideProfileRepository guideProfileRepository;
    private final SupabaseStorageClient storageClient;
    private final String bucket;

    public GuideVerificationService(GuideVerificationRepository verificationRepository,
                                    GuideProfileService guideProfileService,
                                    GuideProfileRepository guideProfileRepository,
                                    SupabaseStorageClient storageClient,
                                    @Value("${supabase.storage.verification-bucket}") String bucket) {
        this.verificationRepository = verificationRepository;
        this.guideProfileService = guideProfileService;
        this.guideProfileRepository = guideProfileRepository;
        this.storageClient = storageClient;
        this.bucket = bucket;
    }

    /** 본인 인증 신청 상태 조회 (없으면 빈 값). */
    @Transactional(readOnly = true)
    public Optional<GuideVerification> getMine(Long userId) {
        GuideProfile profile = guideProfileService.getByUserId(userId);
        return verificationRepository.findByGuideProfileId(profile.getId());
    }

    /**
     * 인증 신청 (최초) 또는 재신청 (기존 행 갱신 → PENDING).
     * 이미 VERIFIED인 상태면 재신청을 막는다(불필요한 재심사·상태 되돌림 방지).
     */
    @Transactional
    public GuideVerification submit(Long userId, String legalName, String licenseNumber,
                                    String issueDate, String innerNumber,
                                    MultipartFile licenseFile, MultipartFile idDocFile) {
        GuideProfile profile = guideProfileService.getByUserId(userId);

        if (legalName == null || legalName.isBlank()) {
            throw new IllegalArgumentException("자격증상의 실명을 입력하세요.");
        }
        if (licenseNumber == null || licenseNumber.isBlank()) {
            throw new IllegalArgumentException("자격증 번호를 입력하세요.");
        }
        if (licenseFile == null || licenseFile.isEmpty()) {
            throw new IllegalArgumentException("자격증 사본을 첨부하세요.");
        }
        if (idDocFile == null || idDocFile.isEmpty()) {
            throw new IllegalArgumentException("신분증 사본을 첨부하세요.");
        }

        Optional<GuideVerification> existing = verificationRepository.findByGuideProfileId(profile.getId());
        if (existing.isPresent() && existing.get().getStatus() == VerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("이미 인증이 완료된 상태입니다.");
        }

        LocalDate parsedIssueDate = parseDate(issueDate);
        // 비공개 버킷에 저장하고 "경로"만 보관한다. 열람은 어드민이 서명 URL로만.
        String licensePath = upload(profile.getId(), "license", licenseFile);
        String idDocPath = upload(profile.getId(), "id", idDocFile);

        GuideVerification verification;
        if (existing.isPresent()) {
            verification = existing.get();
            verification.resubmit(legalName.trim(), licenseNumber.trim(), parsedIssueDate,
                    blankToNull(innerNumber), licensePath, idDocPath);
        } else {
            verification = new GuideVerification(profile.getId(), legalName.trim(), licenseNumber.trim(),
                    parsedIssueDate, blankToNull(innerNumber), licensePath, idDocPath);
        }
        GuideVerification saved = verificationRepository.save(verification);

        // 비정규화 상태를 PENDING으로 (배지/게이팅 조회가 조인 없이 이 값만 보게).
        profile.setVerificationStatus(VerificationStatus.PENDING);
        guideProfileRepository.save(profile);

        return saved;
    }

    private String upload(Long profileId, String kind, MultipartFile file) {
        String path = "guide-" + profileId + "/" + kind + "-"
                + UUID.randomUUID() + getExtension(file.getOriginalFilename());
        try {
            return storageClient.uploadPrivate(bucket, path, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("발급일자 형식이 올바르지 않습니다 (예: 2024-03-15).");
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : "";
    }
}
