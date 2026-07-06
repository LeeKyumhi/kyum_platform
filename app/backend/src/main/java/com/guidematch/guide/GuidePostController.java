package com.guidematch.guide;

import com.guidematch.guide.dto.GuidePostResponse;
import com.guidematch.guide.dto.GuidePostWithGuideResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class GuidePostController {

    private final GuidePostService postService;

    public GuidePostController(GuidePostService postService) {
        this.postService = postService;
    }

    /** 전체 게시글 피드 (공개, 가이드 정보 포함, 최신순) */
    @GetMapping("/api/posts")
    public List<GuidePostWithGuideResponse> feed(@AuthenticationPrincipal Long userId) {
        return postService.listAll(userId);
    }

    /** 게시글 조회수 증가 (공개, 비로그인 포함). 피드에 노출될 때 호출. */
    @PostMapping("/api/posts/{postId}/view")
    public ResponseEntity<Void> recordView(@PathVariable Long postId) {
        postService.recordView(postId);
        return ResponseEntity.noContent().build();
    }

    /** 게시글 본문 번역 (공개 콘텐츠 — 로그인 불필요) */
    @GetMapping("/api/posts/{postId}/translate")
    public java.util.Map<String, String> translate(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        return java.util.Map.of("translated", postService.translate(postId, lang));
    }

    /** 특정 가이드의 게시글 목록 (공개) */
    @GetMapping("/api/guides/{guideProfileId}/posts")
    public List<GuidePostResponse> list(
            @PathVariable Long guideProfileId,
            @AuthenticationPrincipal Long userId
    ) {
        return postService.listByGuideProfile(guideProfileId, userId);
    }

    /** 내 게시글 전체 (가이드/여행자 공통 — 홈 프로필 그리드용) */
    @GetMapping("/api/users/me/posts")
    public List<GuidePostResponse> mine(@AuthenticationPrincipal Long userId) {
        return postService.listMine(userId);
    }

    /** 내 게시글 작성 (텍스트 필수, 이미지 선택. 가이드/여행자 모두 작성 가능) */
    @PostMapping(value = "/api/guide-profiles/me/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GuidePostResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestParam("content") String content,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        GuidePost post = postService.create(userId, content, category, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(GuidePostResponse.from(post));
    }

    /** 내 게시글 삭제 */
    @DeleteMapping("/api/guide-profiles/me/posts/{postId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId
    ) {
        postService.delete(userId, postId);
        return ResponseEntity.noContent().build();
    }
}
