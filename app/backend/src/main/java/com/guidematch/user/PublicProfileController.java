package com.guidematch.user;

import com.guidematch.guide.dto.GuidePostWithGuideResponse;
import com.guidematch.user.dto.PublicProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 공개 프로필 + 사용자 게시글 조회 (비로그인 방문자도 접근 가능). */
@RestController
public class PublicProfileController {

    private final PublicProfileService service;

    public PublicProfileController(PublicProfileService service) {
        this.service = service;
    }

    @GetMapping("/api/users/{handle}")
    public PublicProfileResponse profile(@AuthenticationPrincipal Long viewer, @PathVariable String handle) {
        return service.byHandle(handle, viewer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/api/users/{handle}/posts")
    public List<GuidePostWithGuideResponse> posts(@AuthenticationPrincipal Long viewer, @PathVariable String handle) {
        return service.postsByHandle(handle, viewer);
    }
}
