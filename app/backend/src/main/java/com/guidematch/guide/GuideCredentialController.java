package com.guidematch.guide;

import com.guidematch.guide.dto.CredentialResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 가이드 자격증 API. (내 가이드 프로필 기준)
 * 파일 업로드라서 일반 JSON이 아닌 multipart/form-data 형식으로 받는다.
 */
@RestController
@RequestMapping("/api/guide-profiles/me/credentials")
public class GuideCredentialController {

    private final GuideCredentialService credentialService;

    public GuideCredentialController(GuideCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * 자격증 업로드.
     * consumes = MULTIPART_FORM_DATA : 파일+텍스트를 함께 받는 형식.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CredentialResponse> upload(
            @AuthenticationPrincipal Long userId,
            @RequestParam CredentialType type,
            @RequestParam String title,
            @RequestParam("file") MultipartFile file
    ) {
        GuideCredential credential = credentialService.upload(userId, type, title, file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CredentialResponse.from(credential));
    }

    /** 내 자격증 목록 조회 */
    @GetMapping
    public List<CredentialResponse> list(@AuthenticationPrincipal Long userId) {
        return credentialService.list(userId).stream()
                .map(CredentialResponse::from)
                .toList();
    }
}
