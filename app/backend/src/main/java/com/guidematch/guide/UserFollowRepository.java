package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    boolean existsByFollowerUserIdAndFollowedUserId(Long follower, Long followed);
    Optional<UserFollow> findByFollowerUserIdAndFollowedUserId(Long follower, Long followed);
    long countByFollowedUserId(Long followed);
    long countByFollowerUserId(Long follower);
    List<UserFollow> findByFollowerUserId(Long follower);

    @Query("select f.followedUserId, count(f) from UserFollow f " +
           "where f.followedUserId in :ids group by f.followedUserId")
    List<Object[]> countsByFollowedUserIds(@Param("ids") Collection<Long> ids);
}
