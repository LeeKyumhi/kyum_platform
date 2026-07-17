package com.guidematch.guide;

import com.guidematch.guide.dto.VerificationResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관광통역안내사 자격 인증 신청 API (내 가이드 프로필 기준).
 * 파일(자격증·신분증)을 함께 받으므로 multipart/form-data.
 */
@RestController
@RequestMapping("/api/guide-profiles/me/verification")
public class GuideVerificationController {

    private final GuideVerificationService verificationService;

    public GuideVerificationController(GuideVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /** 내 인증 상태 조회. 신청 이력이 없으면 status=NONE. */
    @GetMapping
    public VerificationResponse mine(@AuthenticationPrincipal Long userId) {
        return verificationService.getMine(userId)
                .map(VerificationResponse::from)
                .orElseGet(VerificationResponse::none);
    }

    /** 인증 신청/재신청. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VerificationResponse submit(
            @AuthenticationPrincipal Long userId,
            @RequestParam String legalName,
            @RequestParam String licenseNumber,
            @RequestParam(required = false) String issueDate,
            @RequestParam(required = false) String innerNumber,
            @RequestParam("licenseFile") MultipartFile licenseFile,
            @RequestParam("idDocFile") MultipartFile idDocFile
    ) {
        GuideVerification saved = verificationService.submit(
                userId, legalName, licenseNumber, issueDate, innerNumber, licenseFile, idDocFile);
        return VerificationResponse.from(saved);
    }
}
