package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    @Transactional
    void deleteByPostIdAndUserId(Long postId, Long userId);

    // 여러 게시글의 좋아요 수를 한 번에 집계 (피드 N+1 방지). 각 행: [postId, count]
    @Query("select l.postId, count(l) from PostLike l where l.postId in :postIds group by l.postId")
    List<Object[]> likeCountsByPostIds(@Param("postIds") Collection<Long> postIds);

    // 현재 사용자가 좋아요 누른 게시글 id들만 한 번에 조회
    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    List<Long> likedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
