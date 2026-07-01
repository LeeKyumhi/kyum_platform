package com.guidematch.guide;

import com.guidematch.storage.SupabaseStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class GuideCredentialService {

    private final GuideCredentialRepository credentialRepository;
    private final GuideProfileService guideProfileService;
    private final SupabaseStorageClient storageClient;
    private final String bucket;

    public GuideCredentialService(
            GuideCredentialRepository credentialRepository,
            GuideProfileService guideProfileService,
            SupabaseStorageClient storageClient,
            @Value("${supabase.storage.credentials-bucket}") String bucket
    ) {
        this.credentialRepository = credentialRepository;
        this.guideProfileService = guideProfileService;
        this.storageClient = storageClient;
        this.bucket = bucket;
    }

    /**
     * 자격증 파일을 업로드하고 정보를 저장한다.
     */
    @Transactional
    public GuideCredential upload(Long userId, CredentialType type, String title, MultipartFile file) {
        // 가이드 프로필이 있어야 자격증을 올릴 수 있다.
        GuideProfile profile = guideProfileService.getByUserId(userId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        // 저장 경로: guide-{프로필id}/{랜덤UUID}{확장자}  (이름 충돌 방지)
        String path = "guide-" + profile.getId() + "/" + UUID.randomUUID() + getExtension(file.getOriginalFilename());

        String fileUrl;
        try {
            fileUrl = storageClient.uploadPublic(bucket, path, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("파일을 읽는 중 오류가 발생했습니다.", e);
        }

        GuideCredential credential = new GuideCredential(profile.getId(), type, title, fileUrl);
        return credentialRepository.save(credential);
    }

    @Transactional(readOnly = true)
    public List<GuideCredential> list(Long userId) {
        GuideProfile profile = guideProfileService.getByUserId(userId);
        return credentialRepository.findByGuideProfileId(profile.getId());
    }

    /** 파일 이름에서 확장자(.pdf 등)를 추출. 없으면 빈 문자열. */
    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : "";
    }
}
