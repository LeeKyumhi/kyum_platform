package com.guidematch.guide;

import com.guidematch.guide.dto.PostCommentResponse;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PostInteractionService {

    private final PostLikeRepository likeRepo;
    private final PostCommentRepository commentRepo;
    private final GuidePostRepository postRepo;
    private final UserRepository userRepo;

    public PostInteractionService(PostLikeRepository likeRepo, PostCommentRepository commentRepo,
                                  GuidePostRepository postRepo, UserRepository userRepo) {
        this.likeRepo = likeRepo;
        this.commentRepo = commentRepo;
        this.postRepo = postRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public void like(Long userId, Long postId) {
        if (!likeRepo.existsByPostIdAndUserId(postId, userId)) {
            likeRepo.save(new PostLike(postId, userId));
        }
    }

    @Transactional
    public void unlike(Long userId, Long postId) {
        likeRepo.deleteByPostIdAndUserId(postId, userId);
    }

    public long likeCount(Long postId) {
        return likeRepo.countByPostId(postId);
    }

    public boolean isLiked(Long userId, Long postId) {
        return userId != null && likeRepo.existsByPostIdAndUserId(postId, userId);
    }

    public long commentCount(Long postId) {
        return commentRepo.countByPostId(postId);
    }

    /** 게시글 하나의 좋아요·댓글·내 좋아요 여부 묶음. */
    public record PostStats(long likeCount, long commentCount, boolean liked) {
        static final PostStats EMPTY = new PostStats(0, 0, false);
    }

    /**
     * 여러 게시글의 상호작용 통계를 3번의 쿼리로 일괄 조회 (게시글별 반복 조회 N+1 방지).
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, PostStats> statsFor(Long currentUserId, java.util.Collection<Long> postIds) {
        if (postIds.isEmpty()) return java.util.Map.of();

        java.util.Map<Long, Long> likes = new java.util.HashMap<>();
        likeRepo.likeCountsByPostIds(postIds).forEach(row -> likes.put((Long) row[0], (Long) row[1]));

        java.util.Map<Long, Long> comments = new java.util.HashMap<>();
        commentRepo.commentCountsByPostIds(postIds).forEach(row -> comments.put((Long) row[0], (Long) row[1]));

        java.util.Set<Long> liked = currentUserId == null
                ? java.util.Set.of()
                : new java.util.HashSet<>(likeRepo.likedPostIds(currentUserId, postIds));

        java.util.Map<Long, PostStats> result = new java.util.HashMap<>();
        for (Long id : postIds) {
            result.put(id, new PostStats(
                    likes.getOrDefault(id, 0L),
                    comments.getOrDefault(id, 0L),
                    liked.contains(id)));
        }
        return result;
    }

    @Transactional
    public PostCommentResponse addComment(Long userId, Long postId, String content) {
        postRepo.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        PostComment saved = commentRepo.save(new PostComment(postId, userId, content));
        String name = userRepo.findById(userId).map(User::getFullName).orElse("Unknown");
        return PostCommentResponse.from(saved, name);
    }

    @Transactional(readOnly = true)
    public List<PostCommentResponse> getComments(Long postId) {
        return commentRepo.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(c -> {
                    String name = userRepo.findById(c.getUserId()).map(User::getFullName).orElse("Unknown");
                    return PostCommentResponse.from(c, name);
                })
                .toList();
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        PostComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        if (!c.getUserId().equals(userId)) throw new SecurityException("본인 댓글만 삭제할 수 있습니다.");
        commentRepo.delete(c);
    }
}
