package com.guidematch.guide;

import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {
    @Mock UserFollowRepository userFollows;
    @Mock GuideProfileRepository profiles;
    @Mock UserRepository users;
    @Mock FollowRepository legacyFollows;

    FollowService service;

    @BeforeEach void setUp() {
        service = new FollowService(userFollows, profiles, users, legacyFollows);
    }

    @Test void 유저_팔로우_성공() {
        when(userFollows.existsByFollowerUserIdAndFollowedUserId(1L, 2L)).thenReturn(false);
        service.followUser(1L, 2L);
        verify(userFollows).save(any(UserFollow.class));
    }

    @Test void 중복_팔로우는_무시() {
        when(userFollows.existsByFollowerUserIdAndFollowedUserId(1L, 2L)).thenReturn(true);
        service.followUser(1L, 2L);
        verify(userFollows, never()).save(any());
    }

    @Test void 자기_팔로우는_예외() {
        assertThrows(IllegalArgumentException.class, () -> service.followUser(1L, 1L));
        verify(userFollows, never()).save(any());
    }

    @Test void 언팔로우는_없어도_조용히() {
        when(userFollows.findByFollowerUserIdAndFollowedUserId(1L, 2L))
                .thenReturn(java.util.Optional.empty());
        assertDoesNotThrow(() -> service.unfollowUser(1L, 2L));
        verify(userFollows, never()).delete(any());
    }
}
