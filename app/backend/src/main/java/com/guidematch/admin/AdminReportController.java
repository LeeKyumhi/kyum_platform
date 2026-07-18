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
 * 관리자용 신고 검토 API.
 * 경로가 /api/admin/** 이므로 SecurityConfig의 hasRole("ADMIN")로 보호된다.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    /** 미처리(OPEN) 신고 목록. */
    @GetMapping
    public List<AdminReportService.ReportItem> open() {
        return adminReportService.listOpen();
    }

    /** 검토 완료(조치함). */
    @PostMapping("/{id}/review")
    public void review(@PathVariable Long id) {
        adminReportService.review(id);
    }

    /** 기각(문제 없음). */
    @PostMapping("/{id}/dismiss")
    public void dismiss(@PathVariable Long id) {
        adminReportService.dismiss(id);
    }

    /** 신고 대상 조치 (body: { "action": "HIDE_POST"|"SUSPEND_USER", "reason": "..." }). */
    @PostMapping("/{id}/act")
    public void act(@PathVariable Long id,
                    @AuthenticationPrincipal Long adminId,
                    @RequestBody Map<String, String> body) {
        adminReportService.act(id, adminId, body.get("action"), body.get("reason"));
    }
}
