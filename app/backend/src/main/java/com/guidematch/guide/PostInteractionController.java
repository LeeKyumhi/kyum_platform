package com.guidematch.guide;

import com.guidematch.guide.dto.PostCommentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts/{postId}")
public class PostInteractionController {

    private final PostInteractionService service;

    public PostInteractionController(PostInteractionService service) {
        this.service = service;
    }

    @PostMapping("/like")
    public ResponseEntity<Map<String, Long>> like(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId
    ) {
        service.like(userId, postId);
        return ResponseEntity.ok(Map.of("likeCount", service.likeCount(postId)));
    }

    @DeleteMapping("/like")
    public ResponseEntity<Map<String, Long>> unlike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId
    ) {
        service.unlike(userId, postId);
        return ResponseEntity.ok(Map.of("likeCount", service.likeCount(postId)));
    }

    @GetMapping("/comments")
    public List<PostCommentResponse> getComments(@PathVariable Long postId) {
        return service.getComments(postId);
    }

    @PostMapping("/comments")
    public ResponseEntity<PostCommentResponse> addComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId,
            @RequestBody CommentRequest req
    ) {
        PostCommentResponse resp = service.addComment(userId, postId, req.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        service.deleteComment(userId, commentId);
        return ResponseEntity.noContent().build();
    }

    record CommentRequest(String content) {}
}
