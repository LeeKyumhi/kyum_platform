# 소셜 레이어 (상호 팔로우·공개 프로필·코스 공유) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 가이드↔여행자 상호 팔로우(user↔user), 공개 프로필 `/users/[handle]`, 여행 코스의 커뮤니티 공유를 추가해 여행자가 커뮤니티에서 활발히 활동하게 한다. 투어·동행 두 트랙 공용.

**Architecture:** 팔로우를 guide-profile 대상에서 **user↔user**로 전환하되, `ddl-auto: update`의 NOT NULL/unique 제약 한계를 피하려 기존 `follows`를 수정하지 않고 **신규 `user_follows` 테이블**을 만들고 1회 백필한다. 기존 `/api/guides/{id}/follow`는 어댑터로 유지. 코스 공유는 새 엔티티 없이 기존 `GuidePost`(여행자 글 지원 `authorUserId`) + `PEERUP::PLAN::` 스냅샷 규약 + `PlanCard`/`lib/placeCard.ts`를 재사용한다.

**Tech Stack:** Spring Boot 3.3.5 / Java 21 / JUnit5+Mockito, Next.js 15 / React 19 / TypeScript / Tailwind. 프론트 유닛 하네스 없음 → 검증은 `tsc --noEmit` + `next lint` + curl E2E(백엔드) + 수동 스모크.

## Global Constraints

- **선행 조건**: Phase 0(`2026-07-20-saved-refactor.md`) 머지 후 진행 권장(독립이나 프로필 페이지를 함께 손댐).
- **관광진흥법 §38**: 여행자를 유상 관광안내(가이드)·유상 동행 파트너로 자동 승격 금지. 소셜/커뮤니티만 확장.
- **Never drop tables/columns** without confirmation — 기존 `follows` 테이블·행 보존.
- **Auth 패턴**: 공개 엔드포인트는 `@AuthenticationPrincipal Long userId`가 null 반환(401 던지지 말 것). `SecurityConfig`에 public 라우트 등록 필수.
- **i18n 모든 키 ko/en/zh 3개 언어 필수**.
- **No new npm packages.**
- **Backend Java 21**: `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`.

---

## Phase 1 — 상호 팔로우 (user ↔ user)

### Task 1: `UserFollow` 엔티티 + 리포지토리 + 1회 백필

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/guide/UserFollow.java`
- Create: `app/backend/src/main/java/com/guidematch/guide/UserFollowRepository.java`
- Create: `app/backend/src/main/java/com/guidematch/guide/UserFollowBackfill.java` (`ApplicationRunner`)

**Interfaces:**
- Produces: `UserFollow(Long followerUserId, Long followedUserId)`; `getId/getFollowerUserId/getFollowedUserId/getCreatedAt`.
- Produces: `UserFollowRepository` — `existsByFollowerUserIdAndFollowedUserId`, `findByFollowerUserIdAndFollowedUserId`, `countByFollowedUserId`, `findByFollowerUserId(Long)`, `@Query countsByFollowedUserIds(Collection<Long>) → List<Object[]>{followedUserId,count}`, `existsByFollowerUserIdAndFollowedUserIdIn`.

- [ ] **Step 1: 엔티티 작성**

```java
package com.guidematch.guide;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_follows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_user_id", "followed_user_id"}))
public class UserFollow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_user_id", nullable = false)
    private Long followerUserId;

    @Column(name = "followed_user_id", nullable = false)
    private Long followedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void onCreate() { this.createdAt = Instant.now(); }

    protected UserFollow() {}
    public UserFollow(Long followerUserId, Long followedUserId) {
        this.followerUserId = followerUserId;
        this.followedUserId = followedUserId;
    }
    public Long getId() { return id; }
    public Long getFollowerUserId() { return followerUserId; }
    public Long getFollowedUserId() { return followedUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 2: 리포지토리 작성**

```java
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
```

- [ ] **Step 3: 백필 러너 작성** (idempotent — 존재하면 skip)

```java
package com.guidematch.guide;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class UserFollowBackfill implements ApplicationRunner {
    private final FollowRepository follows;                 // 기존 guide-profile 팔로우
    private final GuideProfileRepository profiles;
    private final UserFollowRepository userFollows;

    public UserFollowBackfill(FollowRepository follows, GuideProfileRepository profiles,
                              UserFollowRepository userFollows) {
        this.follows = follows; this.profiles = profiles; this.userFollows = userFollows;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Follow f : follows.findAll()) {
            Long followedUserId = profiles.findById(f.getGuideProfileId())
                    .map(GuideProfile::getUserId).orElse(null);
            if (followedUserId == null) continue;
            if (userFollows.existsByFollowerUserIdAndFollowedUserId(f.getFollowerUserId(), followedUserId)) continue;
            userFollows.save(new UserFollow(f.getFollowerUserId(), followedUserId));
        }
    }
}
```
*실행자 확인: `GuideProfile.getUserId()` 접근자 존재 여부(없으면 게터 추가). `Follow.getGuideProfileId()`·`getFollowerUserId()`는 확인됨.*

- [ ] **Step 4: 컴파일 + 기동으로 테이블 생성·백필 확인**

```bash
cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL. (기동 검증은 Task 2 curl 단계에서 `user_follows` 존재로 확인)

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/guide/UserFollow.java app/backend/src/main/java/com/guidematch/guide/UserFollowRepository.java app/backend/src/main/java/com/guidematch/guide/UserFollowBackfill.java
git commit -m "feat(follow): user_follows table + repository + one-time backfill"
```

---

### Task 2: `FollowService` user↔user 전환 + `UserFollowController` + 가이드 어댑터 + followerCount 재배선

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/guide/FollowService.java`
- Create: `app/backend/src/main/java/com/guidematch/guide/UserFollowController.java`
- Create: `app/backend/src/main/java/com/guidematch/guide/dto/FollowingUserResponse.java`
- Modify: `app/backend/src/main/java/com/guidematch/guide/FollowController.java` (어댑터 위임)
- Modify: `app/backend/src/main/java/com/guidematch/guide/GuideController.java` (followerCount/isFollowing 재배선)
- Modify: `app/backend/src/main/java/com/guidematch/config/SecurityConfig.java` (`GET /api/users/*/followers/count` public)
- Create: `app/backend/src/test/java/com/guidematch/guide/FollowServiceTest.java`

**Interfaces:**
- Produces: `FollowService.followUser(Long follower, Long followed)` (멱등, 자기팔로우 `IllegalArgumentException`), `unfollowUser(follower, followed)`, `followerCountOfUser(Long userId)`, `isFollowingUser(Long viewer, Long target)`, `followerCountsByUserIds(Collection<Long>)→Map<Long,Long>`, `myFollowingUsers(Long) → List<FollowingUserResponse>`.
- Produces: `FollowingUserResponse(Long userId, String handle, String name, String avatarUrl, boolean isGuide, Long guideProfileId, String headline)`.
- Produces: `POST/DELETE /api/users/{userId}/follow`, `GET /api/users/{userId}/followers/count`, `GET /api/users/me/following`.
- Consumes: `UserFollowRepository`(Task 1), `GuideProfileRepository`, `UserRepository`.

- [ ] **Step 1: 실패하는 서비스 테스트 작성** (`FollowServiceTest.java`)

```java
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
```
*실행자 확인: `FollowService`의 실제 생성자 인자에 맞춰 목 구성. 기존 생성자에 없던 `UserFollowRepository`/`UserRepository`/`FollowRepository`를 추가하는 것이 이 태스크의 일부.*

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests '*FollowServiceTest' 2>&1 | tail -15
```
Expected: 컴파일 실패(`followUser`/`unfollowUser` 미정의) 또는 FAIL.

- [ ] **Step 3: `FollowService`에 user↔user 메서드 추가** — 기존 guide-profile 메서드는 어댑터에서 재사용하도록 유지하되 내부적으로 user_follows로 위임한다.

```java
    // user↔user
    public void followUser(Long follower, Long followed) {
        if (follower.equals(followed)) throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        if (userFollows.existsByFollowerUserIdAndFollowedUserId(follower, followed)) return; // idempotent
        userFollows.save(new UserFollow(follower, followed));
    }
    public void unfollowUser(Long follower, Long followed) {
        userFollows.findByFollowerUserIdAndFollowedUserId(follower, followed)
                .ifPresent(userFollows::delete);
    }
    public long followerCountOfUser(Long userId) { return userFollows.countByFollowedUserId(userId); }
    public boolean isFollowingUser(Long viewer, Long target) {
        return viewer != null && userFollows.existsByFollowerUserIdAndFollowedUserId(viewer, target);
    }
    public java.util.Map<Long, Long> followerCountsByUserIds(java.util.Collection<Long> ids) {
        java.util.Map<Long, Long> out = new java.util.HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        userFollows.countsByFollowedUserIds(ids).forEach(r -> out.put((Long) r[0], (Long) r[1]));
        return out;
    }
```

`myFollowingUsers(Long follower)`: `userFollows.findByFollowerUserId(follower)`로 followedUserId 수집 → `users.findAllById(...)`·`profiles`에서 가이드 여부/handle/이름/아바타 조립 → `List<FollowingUserResponse>`. (가이드면 `isGuide=true`, `guideProfileId`·`headline` 채움; 여행자면 null.)

- [ ] **Step 4: `FollowingUserResponse` 작성**

```java
package com.guidematch.guide.dto;

public record FollowingUserResponse(
        Long userId, String handle, String name, String avatarUrl,
        boolean isGuide, Long guideProfileId, String headline
) {}
```

- [ ] **Step 5: `UserFollowController` 작성**

```java
package com.guidematch.guide;

import com.guidematch.guide.dto.FollowingUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
public class UserFollowController {
    private final FollowService followService;
    public UserFollowController(FollowService followService) { this.followService = followService; }

    @PostMapping("/api/users/{userId}/follow")
    public ResponseEntity<Void> follow(@AuthenticationPrincipal Long me, @PathVariable Long userId) {
        followService.followUser(me, userId);
        return ResponseEntity.ok().build();
    }
    @DeleteMapping("/api/users/{userId}/follow")
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal Long me, @PathVariable Long userId) {
        followService.unfollowUser(me, userId);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/users/{userId}/followers/count")
    public Map<String, Long> followerCount(@PathVariable Long userId) {
        return Map.of("count", followService.followerCountOfUser(userId));
    }
    @GetMapping("/api/users/me/following")
    public List<FollowingUserResponse> myFollowing(@AuthenticationPrincipal Long me) {
        return followService.myFollowingUsers(me);
    }
}
```

- [ ] **Step 6: 가이드 어댑터 위임** — `FollowController`의 `follow`/`unfollow`/`followerCount`가 `guideProfileId → userId` 해석 후 user_follows로 위임하도록 수정. `GET /api/guides/{id}/followers/count` = `followService.followerCountOfUser(guide.userId)`. `POST/DELETE`도 동일 해석 후 `followUser/unfollowUser`. `myFollowing`(가이드 목록)은 하위호환 위해 유지하거나 Task 7에서 정리.

- [ ] **Step 7: `GuideController` followerCount/isFollowing 재배선** — `list`의 배치 카운트를 `followService.followerCountsByUserIds(가이드 userId들)`로, `detail`의 `followerCount`·`isFollowing`을 `followerCountOfUser(guide.userId)`·`isFollowingUser(viewer, guide.userId)`로 교체. 기존 `followRepository.followerCountsByGuideProfileIds` 호출 제거.

- [ ] **Step 8: SecurityConfig public 라우트** — `GET /api/users/*/followers/count`를 permitAll에 추가(팔로우 POST/DELETE·`/me/following`은 authenticated 유지).

- [ ] **Step 9: 테스트 통과 + curl E2E**

```bash
cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests '*FollowServiceTest' compileJava 2>&1 | tail -15
```
Expected: PASS + BUILD SUCCESSFUL. 이후 서버 기동 후 curl: 로그인 토큰으로 `POST /api/users/{other}/follow`(200) → 중복(200 no-op) → `GET /api/users/{other}/followers/count`(count 1) → 자기팔로우(400) → `DELETE`(204) → `GET /api/users/me/following`.

- [ ] **Step 10: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/guide app/backend/src/main/java/com/guidematch/config/SecurityConfig.java app/backend/src/test/java/com/guidematch/guide/FollowServiceTest.java
git commit -m "feat(follow): user-to-user follow endpoints + guide adapter + follower count rewire"
```

---

### Task 3: 프론트 — `FollowButton`(userId) + 가이드 상세 전환

**Files:**
- Create: `app/frontend/src/components/FollowButton.tsx`
- Modify: `app/frontend/src/app/guides/[id]/page.tsx` (팔로우 버튼을 `guideUserId` 기반 `FollowButton`으로)
- Modify: `app/frontend/src/lib/i18n.ts` (`t.follow.follow/following/followers` ko/en/zh — 기존 있으면 재사용)

**Interfaces:**
- Consumes: `POST/DELETE /api/users/{userId}/follow`, `GET /api/users/{userId}/followers/count`.
- Produces: `<FollowButton userId={number} initialFollowing?={boolean} onChange?={(following:boolean)=>void} />`.

- [ ] **Step 1: `FollowButton.tsx` 작성** — userId 기반 토글, 낙관적 업데이트, 실패 시 롤백, 비로그인 시 `/login`. 본인 프로필이면 렌더 안 함(부모가 제어).

```tsx
"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

export default function FollowButton({ userId, initialFollowing = false, onChange }:
  { userId: number; initialFollowing?: boolean; onChange?: (f: boolean) => void }) {
  const router = useRouter();
  const { t } = useLanguage();
  const [following, setFollowing] = useState(initialFollowing);
  const [busy, setBusy] = useState(false);

  async function toggle() {
    if (!getToken()) { router.push("/login"); return; }
    const next = !following;
    setFollowing(next); setBusy(true); onChange?.(next);
    try {
      await api(`/api/users/${userId}/follow`, { method: next ? "POST" : "DELETE", auth: true });
    } catch { setFollowing(!next); onChange?.(!next); }
    finally { setBusy(false); }
  }
  return (
    <button onClick={toggle} disabled={busy}
      className={following ? "btn-ghost text-sm" : "btn-primary text-sm"}>
      {following ? t.follow.following : t.follow.follow}
    </button>
  );
}
```

- [ ] **Step 2: 가이드 상세 전환** — `guides/[id]/page.tsx`에서 기존 팔로우 버튼을 `<FollowButton userId={detail.guideUserId} initialFollowing={detail.isFollowing} />`로 교체(`GuideDetailResponse.guideUserId`·`isFollowing` 소비).

- [ ] **Step 3: i18n `follow` 그룹 확인/추가** — `follow: { follow, following, followers }` 3언어. 이미 있으면 재사용.

- [ ] **Step 4: 검증**

```bash
cd app/frontend && npx tsc --noEmit && npx next lint 2>&1 | tail -10
```
Expected: 타입 에러 0.

- [ ] **Step 5: 커밋**

```bash
git add app/frontend/src/components/FollowButton.tsx app/frontend/src/app/guides/[id]/page.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(follow): FollowButton (userId) + guide detail switch to user-follow"
```

---

## Phase 2 — 공개 프로필 `/users/[handle]`

### Task 4: 백엔드 — 공개 프로필 + 사용자 게시글 + 포스트 DTO 작성자 필드

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/user/PublicProfileController.java`
- Create: `app/backend/src/main/java/com/guidematch/user/dto/PublicProfileResponse.java`
- Modify: `app/backend/src/main/java/com/guidematch/guide/dto/GuidePostWithGuideResponse.java` (`authorHandle`, `authorUserId` 추가)
- Modify: 포스트 목록/작성 조립부(위 DTO를 만드는 서비스) — `authorHandle` 채우기
- Modify: `app/backend/src/main/java/com/guidematch/config/SecurityConfig.java` (`GET /api/users/*`, `GET /api/users/*/posts` public)

**Interfaces:**
- Produces: `GET /api/users/{handle}` → `PublicProfileResponse(userId, handle, name, avatarUrl, nationality, mbti, interests, isGuide, guideProfileId, followerCount, followingCount, isFollowing)` (없으면 404).
- Produces: `GET /api/users/{handle}/posts` → `List<GuidePostWithGuideResponse>` (author_user_id 기준, 최신순).
- Consumes: `UserRepository`(handle 조회), `GuideProfileRepository`, `FollowService`(Task 2), 포스트 리포지토리.

- [ ] **Step 1: `PublicProfileResponse` 작성**

```java
package com.guidematch.user.dto;

import java.util.List;

public record PublicProfileResponse(
        Long userId, String handle, String name, String avatarUrl,
        String nationality, String mbti, List<String> interests,
        boolean isGuide, Long guideProfileId,
        long followerCount, long followingCount, boolean isFollowing
) {}
```

- [ ] **Step 2: `PublicProfileController` 작성** — handle로 User 조회(없으면 404). 가이드면 GuideProfile에서 avatar/guideProfileId. `followerCount=followService.followerCountOfUser(userId)`, `isFollowing=followService.isFollowingUser(viewer, userId)`(viewer nullable), `followingCount=userFollows.countByFollowerUserId(userId)`.

```java
package com.guidematch.user;

import com.guidematch.user.dto.PublicProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class PublicProfileController {
    private final PublicProfileService service;
    public PublicProfileController(PublicProfileService service) { this.service = service; }

    @GetMapping("/api/users/{handle}")
    public PublicProfileResponse profile(@AuthenticationPrincipal Long viewer, @PathVariable String handle) {
        return service.byHandle(handle, viewer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
```
*실행자: `PublicProfileService`(신규, byHandle + posts)를 같은 태스크에서 작성. `User`에 `handle`·`nickname`·`nationality`·`mbti`·`interests` 접근자 존재 확인(`/api/users/me` DTO가 이미 노출하므로 존재). 여행자 avatar 컬럼 부재 시 avatarUrl=가이드면 GuideProfile.avatarUrl, 아니면 null.*

- [ ] **Step 3: 사용자 게시글 엔드포인트** — `GET /api/users/{handle}/posts`: handle→userId 해석 후 `GuidePost where authorUserId = userId` 최신순 → `GuidePostWithGuideResponse.fromTraveler(...)` (가이드 글이면 기존 팩토리). 차단(Safety) 상호 숨김 규칙 반영.

- [ ] **Step 4: 포스트 DTO에 작성자 링크 필드 추가** — `GuidePostWithGuideResponse`에 `authorHandle`(String), `authorUserId`(Long) 추가하고 `fromTraveler`/피드 조립부에서 채운다. 프론트 여행자 작성자 링크(`/users/{handle}`)에 필요.

- [ ] **Step 5: SecurityConfig** — `GET /api/users/*`, `GET /api/users/*/posts` permitAll 추가. (주의: `GET /api/users/me`·`/me/following`이 더 구체적 경로로 먼저 매칭되게 순서 확인 — `/me`는 authenticated 유지.)

- [ ] **Step 6: 컴파일 + curl E2E**

```bash
cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL. curl: `GET /api/users/{handle}`(비로그인 isFollowing=false), 없는 handle 404, `GET /api/users/{handle}/posts` 배열.

- [ ] **Step 7: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/user app/backend/src/main/java/com/guidematch/guide/dto/GuidePostWithGuideResponse.java app/backend/src/main/java/com/guidematch/config/SecurityConfig.java
git commit -m "feat(profile): public user profile + user posts endpoints"
```

---

### Task 5: 프론트 — `/users/[handle]` 페이지 + PostCard 작성자 링크

**Files:**
- Create: `app/frontend/src/app/users/[handle]/page.tsx`
- Modify: `app/frontend/src/components/PostCard.tsx` (작성자 링크 분기)
- Modify: `app/frontend/src/lib/i18n.ts` (`t.publicProfile.*` ko/en/zh)

**Interfaces:**
- Consumes: `GET /api/users/{handle}`, `GET /api/users/{handle}/posts`, `<FollowButton>`(Task 3).
- Consumes: `FeedPost`에 추가된 `authorHandle`·`authorUserId`.

- [ ] **Step 1: `/users/[handle]/page.tsx` 작성** — 헤더(아바타/이름/handle, 팔로워·팔로잉 수, `FollowButton` [본인이면 "프로필 편집" 링크로 대체], 가이드면 "가이드 프로필 보기" → `/guides/{guideProfileId}`) + 게시글 인스타 그리드(이미지 글=썸네일; PLAN 규약 글=`PlanCard` 미리보기 타일; 타일 클릭 시 상세/피드). 트랙 공용.

- [ ] **Step 2: PostCard 작성자 링크 분기** — 작성자가 여행자면 `/users/{post.authorHandle}`, 가이드면 기존 `/guides/{post.guideProfileId}`. `FeedPost` 타입에 `authorHandle?: string`·`authorUserId?: number` 추가.

- [ ] **Step 3: i18n `publicProfile` 그룹** — `{ followers, following, posts, viewGuideProfile, editProfile, noPosts }` 3언어.

- [ ] **Step 4: 검증**

```bash
cd app/frontend && npx tsc --noEmit && npx next lint 2>&1 | tail -10
```
Expected: 타입 에러 0.

- [ ] **Step 5: 수동 스모크 + 커밋** — 여행자 프로필 접근, 팔로우 토글, 게시글 그리드 렌더 확인 후:

```bash
git add app/frontend/src/app/users app/frontend/src/components/PostCard.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(profile): public /users/[handle] page + post author links"
```

---

## Phase 3 — 여행 코스 커뮤니티 공유

### Task 6: 프론트 — 코스 공유 발행 + 피드 PlanCard 렌더 + 따라하기

**Files:**
- Modify: `app/frontend/src/app/trips/[id]/page.tsx` ("커뮤니티에 공유" 버튼)
- Modify: `app/frontend/src/components/PostComposeModal.tsx` (플랜 프리필 지원)
- Modify: `app/frontend/src/components/PostCard.tsx` (content가 PLAN 규약이면 `PlanCard` 렌더)
- Create/Modify: `app/frontend/src/lib/followCourse.ts` (플랜 스냅샷 → itinerary 어댑터, 필요 시 함수 추가)
- Modify: `app/frontend/src/lib/i18n.ts` (`shareToCommunity`, `cloneToMyTrip` 3언어)

**Interfaces:**
- Consumes: `PEERUP::PLAN::` 규약(`lib/placeCard.ts`의 `parseCard`), `PlanCard.tsx`, `POST /api/posts`(multipart, content 필수·image 선택).
- Produces: `PostComposeModal`에 `initialContent?: string` prop; `PostCard`가 PLAN 본문을 `PlanCard`로 렌더 + "이 코스로 내 일정 만들기" 액션.

- [ ] **Step 1: `PostComposeModal`에 프리필 지원** — `initialContent?: string` prop 추가, 열릴 때 `content` 초기값으로 사용. 이미지 없이 발행 가능(이미 content만 필수).

- [ ] **Step 2: `/trips/[id]`에 "커뮤니티에 공유"** — 현재 일정을 `PEERUP::PLAN::{스냅샷}` 문자열로 인코딩(채팅 공유와 동일 규약 재사용; `lib/placeCard.ts`의 인코딩 헬퍼 사용)해 `PostComposeModal`을 `initialContent`로 오픈.

- [ ] **Step 3: `PostCard`에서 PLAN 렌더** — `post.content`가 `parseCard`로 PLAN 규약이면 이미지 대신 `<PlanCard>` 렌더. 좋아요·댓글은 기존 그대로. 카드에 "이 코스로 내 일정 만들기" 버튼.

- [ ] **Step 4: 따라하기 어댑터** — `lib/followCourse.ts`에 플랜 스냅샷 정차지 → 새 `Itinerary`(정차지별 `ItineraryItem`) 생성 함수 추가(기존 코스 따라하기 로직 재사용/확장). 생성 후 `/trips/{newId}`로 이동.

- [ ] **Step 5: i18n** — `travelerHome`/`community` 그룹에 `shareToCommunity`("커뮤니티에 공유"/"Share to community"/"分享到社区"), `cloneToMyTrip`("이 코스로 내 일정 만들기"/…/…) 3언어.

- [ ] **Step 6: 검증 + 수동 스모크**

```bash
cd app/frontend && npx tsc --noEmit && npx next lint 2>&1 | tail -10
```
Expected: 타입 에러 0. 스모크: `/trips/[id]` → 공유 → 커뮤니티 피드에 PlanCard 글 → "내 일정 만들기" → 새 일정 생성.

- [ ] **Step 7: 커밋**

```bash
git add app/frontend/src/app/trips/[id]/page.tsx app/frontend/src/components/PostComposeModal.tsx app/frontend/src/components/PostCard.tsx app/frontend/src/lib/followCourse.ts app/frontend/src/lib/i18n.ts
git commit -m "feat(community): share travel course to feed as PlanCard + clone-to-trip"
```

---

## Phase 4 — 상호작용 마감

### Task 7: 프론트 — following 페이지 일반화 + 팔로우 버튼 표면 통일 + 두 트랙 노출

**Files:**
- Modify: `app/frontend/src/app/traveler/following/page.tsx` (`FollowingUserResponse` 소비, 가이드+여행자 카드)
- Modify: `app/frontend/src/components/PostCard.tsx` (작성자 옆 `FollowButton`)
- Modify: `app/frontend/src/app/community/page.tsx` (필요 시 작성자 팔로우 노출)
- Modify: `app/frontend/src/lib/i18n.ts` (following 페이지 카피 일반화 3언어)

**Interfaces:**
- Consumes: `GET /api/users/me/following` → `FollowingUserResponse[]`(Task 2), `<FollowButton>`(Task 3).

- [ ] **Step 1: following 페이지 일반화** — `/traveler/following`가 `GET /api/users/me/following`(신규, FollowingUserResponse)를 소비. 가이드 항목은 `/guides/{guideProfileId}`, 여행자 항목은 `/users/{handle}` 링크. 언팔로우는 `DELETE /api/users/{userId}/follow`.

- [ ] **Step 2: 팔로우 버튼 표면 통일** — PostCard 작성자 옆(본인·이미 표시된 곳 제외)과 community 피드에 `FollowButton` 노출. 가이드 상세는 Task 3에서 완료.

- [ ] **Step 3: 두 트랙 노출 점검** — 공개 프로필·커뮤니티·팔로우가 투어/동행 두 세계 모두에서 접근되는지 확인. 동행 컨텍스트에서 '가이드' 승격 유도 카피 미노출(기존 정책 유지).

- [ ] **Step 4: i18n** — following 페이지 제목/빈상태를 "팔로잉(가이드+여행자)" 톤으로 일반화, 3언어.

- [ ] **Step 5: 검증 + 수동 스모크**

```bash
cd app/frontend && npx tsc --noEmit && npx next lint 2>&1 | tail -10
```
Expected: 타입 에러 0. 스모크: following 목록에 가이드·여행자 혼재, 피드/프로필 팔로우 토글, 두 트랙에서 공개 프로필 접근.

- [ ] **Step 6: 커밋**

```bash
git add app/frontend/src/app/traveler/following/page.tsx app/frontend/src/components/PostCard.tsx app/frontend/src/app/community/page.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(social): generalize following list + unify follow buttons across surfaces"
```

---

## Self-Review

- **Spec coverage** — Phase 1(상호 팔로우 user↔user, 신규 테이블+백필+어댑터+followerCount 재배선): T1·T2·T3. Phase 2(공개 프로필 `/users/[handle]`+사용자 게시글+작성자 링크): T4·T5. Phase 3(코스 커뮤니티 공유, PEERUP::PLAN 재사용+따라하기): T6. Phase 4(following 일반화+버튼 통일+두 트랙): T7. §38 제약은 Global Constraints + T7-S3. ✅
- **Placeholder scan** — 신규 파일(엔티티/DTO/컨트롤러/FollowButton)은 완전한 코드. 기존 파일 편집은 확인된 시그니처(`GuideDetailResponse.guideUserId`·`isFollowing`, `GuidePost.authorUserId`, `FollowRepository.getGuideProfileId`)에 기반한 정밀 지시 + "실행자 확인" 노트로 명시. 프론트 검증은 tsc+lint+수동 스모크(하네스 부재 반영). ✅
- **Type consistency** — `followUser/unfollowUser/followerCountOfUser/isFollowingUser/followerCountsByUserIds/myFollowingUsers`가 T2 정의↔T3/T4/T7 소비 일치. `FollowingUserResponse`·`PublicProfileResponse`·`FollowButton({userId})`·`authorHandle/authorUserId` 시그니처가 태스크 간 일치. ✅
- **열린 확인 항목**(스펙 §열린 확인): ① 공개 프로필 게시글 소스 = `authorUserId` 단일 기준(T4-S3 확정). ② 플랜→일정 따라하기 어댑터(T6-S4). ③ 저장됨 탭/리다이렉트는 Phase 0 플랜에서 확정. ✅
