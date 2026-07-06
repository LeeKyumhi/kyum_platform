package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    List<PostComment> findByPostIdOrderByCreatedAtAsc(Long postId);
    long countByPostId(Long postId);

    // 여러 게시글의 댓글 수를 한 번에 집계 (피드 N+1 방지). 각 행: [postId, count]
    @Query("select c.postId, count(c) from PostComment c where c.postId in :postIds group by c.postId")
    List<Object[]> commentCountsByPostIds(@Param("postIds") Collection<Long> postIds);
}
