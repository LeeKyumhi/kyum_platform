package com.guidematch.admin;

import org.springframework.web.bind.annotation.*;

/** 관리자 게시글 모더레이션 API. /api/admin/** → hasRole("ADMIN"). */
@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final AdminPostService adminPostService;
    private final ModerationService moderationService;

    public AdminPostController(AdminPostService adminPostService, ModerationService moderationService) {
        this.adminPostService = adminPostService;
        this.moderationService = moderationService;
    }

    @GetMapping
    public AdminUserService.PageResult<AdminPostService.PostRow> list(
            @RequestParam(defaultValue = "false") boolean hidden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminPostService.list(hidden, page, size);
    }

    @PostMapping("/{id}/hide")
    public void hide(@PathVariable Long id) { moderationService.hidePost(id); }

    @PostMapping("/{id}/unhide")
    public void unhide(@PathVariable Long id) { moderationService.unhidePost(id); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { moderationService.deletePost(id); }
}
