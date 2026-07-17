package com.guidematch.safety;

import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 신고·차단 API. 전부 로그인 필요 (SecurityConfig 기본 authenticated → principal null 걱정 없음).
 */
@RestController
public class SafetyController {

    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    public record BlockRequest(@NotNull Long targetUserId) {}

    public record ReportRequest(
            @NotNull String targetType,
            @NotNull Long targetId,
            @NotNull String reason,
            String detail) {}

    /** 사용자 차단 (멱등) */
    @PostMapping("/api/blocks")
    public Map<String, Object> block(@AuthenticationPrincipal Long userId,
                                     @RequestBody BlockRequest request) {
        safetyService.block(userId, request.targetUserId());
        return Map.of("blocked", true, "targetUserId", request.targetUserId());
    }

    /** 차단 해제 (멱등) */
    @DeleteMapping("/api/blocks/{targetUserId}")
    public Map<String, Object> unblock(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long targetUserId) {
        safetyService.unblock(userId, targetUserId);
        return Map.of("blocked", false, "targetUserId", targetUserId);
    }

    /** 내가 차단한 사용자 목록 */
    @GetMapping("/api/blocks")
    public List<SafetyService.BlockedUser> myBlocks(@AuthenticationPrincipal Long userId) {
        return safetyService.myBlocks(userId);
    }

    /** 신고 접수 */
    @PostMapping("/api/reports")
    public Map<String, Object> report(@AuthenticationPrincipal Long userId,
                                      @RequestBody ReportRequest request) {
        SafetyService.ReportResult result = safetyService.report(
                userId, request.targetType(), request.targetId(), request.reason(), request.detail());
        return Map.of("reported", true, "reportId", result.id() == null ? "" : result.id());
    }
}
