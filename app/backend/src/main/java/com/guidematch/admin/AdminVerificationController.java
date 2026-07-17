package com.guidematch.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 관리자용 자격 인증 심사 API.
 * 경로가 /api/admin/** 이므로 SecurityConfig의 hasRole("ADMIN")로 보호된다
 * (JWT role=ADMIN 없이는 접근 자체가 차단됨).
 */
@RestController
@RequestMapping("/api/admin/verifications")
public class AdminVerificationController {

    private final AdminVerificationService adminVerificationService;

    public AdminVerificationController(AdminVerificationService adminVerificationService) {
        this.adminVerificationService = adminVerificationService;
    }

    /** 심사 대기 목록. */
    @GetMapping
    public List<AdminVerificationService.PendingItem> pending() {
        return adminVerificationService.listPending();
    }

    /** 승인. */
    @PostMapping("/{id}/approve")
    public void approve(@PathVariable Long id, @AuthenticationPrincipal Long adminUserId) {
        adminVerificationService.approve(id, adminUserId);
    }

    /** 반려 (body: { "reason": "..." }). */
    @PostMapping("/{id}/reject")
    public void reject(@PathVariable Long id,
                       @AuthenticationPrincipal Long adminUserId,
                       @RequestBody Map<String, String> body) {
        adminVerificationService.reject(id, adminUserId, body.get("reason"));
    }
}
