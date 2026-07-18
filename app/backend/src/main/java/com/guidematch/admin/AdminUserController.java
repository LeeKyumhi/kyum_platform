package com.guidematch.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 관리자 회원 관리 API. /api/admin/** → hasRole("ADMIN"). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminUserService.PageResult<AdminUserService.UserRow> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.list(query, status, page, size);
    }

    @PostMapping("/{id}/suspend")
    public void suspend(@PathVariable Long id,
                        @AuthenticationPrincipal Long adminId,
                        @RequestBody(required = false) Map<String, String> body) {
        adminUserService.suspend(id, adminId, body != null ? body.get("reason") : null);
    }

    @PostMapping("/{id}/reactivate")
    public void reactivate(@PathVariable Long id) {
        adminUserService.reactivate(id);
    }
}
