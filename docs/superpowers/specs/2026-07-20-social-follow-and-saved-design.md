# 소셜 레이어(상호 팔로우·공개 프로필·코스 공유) + 저장됨 리팩터 설계

> 작성일 2026-07-20. 브레인스토밍 승인 후 확정 스펙.
> 재개 시 읽는 순서: 이 문서 → `CLAUDE.md`(구조/패턴) → 관련 기존 코드.

## 배경 / 목표

두 가지 사용자 요청:

1. **소셜 확장** — 팔로잉을 **가이드↔여행자 상호 팔로우**로 전환. 여행자도 커뮤니티에서 활발히 활동:
   자기가 간 **여행 코스를 커뮤니티에 공유**하고, 다른 사용자를 팔로우/상호작용. 투어·동행 **두 트랙 모두** 적용.
2. **저장됨 리팩터** — 저장됨에서 **가이드 제거**(코스·장소만). 사이드바(베너)에서 `/saved` 빼고,
   **하단 프로필 클릭 → 인스타처럼 저장된 것 확인**.

### 관통 제약 (관광진흥법 §38)

앱은 관광진흥법 제38조(무자격자 유상 관광안내 금지) 대응으로 **투어(인증 가이드)** / **동행(파트너)** 두 트랙으로 분리돼 있음.
"여행자도 활발히"는 **팔로우·커뮤니티·코스공유·소셜**로만 확장한다. 여행자를 유상 '관광안내(가이드)' 또는
유상 동행 파트너 역할로 자동 승격하지 않는다(유상 동행 등록은 기존 `become-guide` 절차 그대로).

### 브레인스토밍에서 확정된 결정

- **동행 세계 여행자 역할** = 소비자 + 소셜 (동행 파트너로 자기 등록은 범위 밖).
- **코스 공유 표면** = 커뮤니티 피드에 발행 (`PEERUP::PLAN::` 스냅샷 카드 재사용).
- **공개 프로필 페이지** = `/users/[handle]` 신규 (상호작용·팔로우·공유 코스가 사는 표면).

## 단계 구성 (배포 단위)

- **Phase 0 — 저장됨 리팩터**: 독립·저위험. **먼저 배포**. 소셜 결정에 의존하지 않음.
- **Phase 1 — 상호 팔로우 (user↔user)**: 데이터 모델 전환. 이후 단계의 토대.
- **Phase 2 — 공개 프로필 `/users/[handle]`**: 팔로우/공유 코스가 사는 표면.
- **Phase 3 — 여행 코스 커뮤니티 공유**: `PEERUP::PLAN::` 재사용.
- **Phase 4 — 상호작용 마감**: 팔로우 버튼 표면 통일 + following 페이지 일반화 + 두 트랙 노출.

리포 관행대로 **Phase 0 / Phase 1–4 브랜치 분리** 제안.

---

## Phase 0 — 저장됨: 가이드 제거 + 프로필 인스타뷰

### 데이터 안전
`SavedItemType`은 `@Enumerated(EnumType.STRING)` → **상수 삭제·행 삭제 금지**(파괴적, `CLAUDE.md` 규칙).
`GUIDE`는 상수·기존 행을 **보존**하고 읽기/쓰기 경로에서만 제외한다(ordinal 손상 위험 없음).

### 백엔드 (`saved/`)
- `POST /api/saved`: `itemType=GUIDE` 요청 **거부(400)**.
- `GET /api/saved/ids`: 응답을 `{courseIds, placeRefs}`로 (guideIds 제거).
- `GET /api/saved`(리스트, `SavedListResponse`): GUIDE 항목 필터해 courses·places만.
- `GET /api/saved/counts`: `type` 파라미터 `COURSE`만 허용(가이드 저장수 뱃지 폐기).

### 프론트
- `lib/saved.ts`:
  - `SavedIds` = `{ courseIds: number[]; placeRefs: string[] }` (guideIds 제거).
  - `SaveTarget`에서 `GUIDE` 멤버 제거 (`COURSE`/`PLACE`만).
  - `fetchSaveCounts(type: "COURSE", ...)` — 유니언에서 GUIDE 제거.
- `SaveButton.tsx`: GUIDE 분기 제거.
- **가이드 카드/상세에서 저장 버튼·저장수 뱃지 제거** (GUIDE 저장 UI 제거).
- **`Sidebar.tsx`: `/saved ❤️` 메뉴 줄 제거**(데스크탑·모바일 양쪽) — 베너에서 뺌.
- **프로필 페이지(`/profile`)에 탭 섹션 추가**: `게시글` | `저장됨`.
  - `저장됨` 탭 = 저장한 **코스·장소**를 **인스타 그리드**로(정사각 썸네일 그리드). 해제 시 `SAVED_CHANGED_EVENT`로 재조회.
  - 하단 프로필(`👤 /profile`, `Sidebar` line 168/177/289 확인) 클릭 → 이 탭에서 저장물 확인.
- `/saved` 라우트: `/profile`(저장됨 탭)로 **리다이렉트** 유지(딥링크·기존 링크 안전). 기존 3탭 페이지 로직은 프로필 탭 컴포넌트로 이관.

### i18n
저장됨 탭/그리드 라벨 ko/en/zh. 기존 `t.saved.*` 재사용, 가이드 탭 관련 키만 정리.

---

## Phase 1 — 상호 팔로우 (user ↔ user)

### 문제
현재 `Follow(followerUserId, guideProfileId)` — 여행자는 `guide_profile`이 없어 팔로우 대상이 될 수 없음.
`follows.guide_profile_id`는 `nullable=false`이고 unique가 `(follower_user_id, guide_profile_id)`.
`ddl-auto: update`는 NOT NULL 완화·기존 unique 변경을 하지 않으므로 기존 컬럼을 재활용하면 마이그레이션이 깨진다.

### 해법 — 신규 테이블 (additive, ddl-safe)
- **신규 `user_follows`**: `id`, `follower_user_id`(NOT NULL), `followed_user_id`(NOT NULL), `created_at`.
  `unique(follower_user_id, followed_user_id)`. Hibernate가 올바른 제약으로 새로 생성.
- **1회 백필**(idempotent `CommandLineRunner` 또는 서비스 기동 시 1회):
  기존 `follows` 각 행 → `user_follows(follower_user_id, followed_user_id = guide_profiles.user_id)`.
  `followed_user_id IS NULL` 해석 실패 행은 skip. 이미 있으면 no-op(unique).
- **기존 `follows` 테이블·행 보존**(삭제는 사용자 확인 후). 백필 후 read 경로에서 미사용.

### 백엔드 (`guide/` → 팔로우 로직 이전/추가)
- 신규 `UserFollow` 엔티티 + `UserFollowRepository`(`existsBy...`, `deleteBy...`, `countByFollowedUserId`,
  배치 `countsByFollowedUserIds`, `findByFollowerUserId`).
- `FollowService`를 `user_follows` 기준으로 전환: 멱등(중복 팔로우=no-op 200), **자기 팔로우 400**.
- 엔드포인트:
  - `POST /api/users/{userId}/follow` (auth)
  - `DELETE /api/users/{userId}/follow` (auth)
  - `GET /api/users/{userId}/followers/count` (공개)
  - `GET /api/users/me/following` → `List<FollowingUserResponse>`
    `{ userId, handle, name, avatarUrl, isGuide, guideProfileId?, headline? }`
  - **하위호환 어댑터**: 기존 `POST/DELETE /api/guides/{guideProfileId}/follow` 유지 →
    `guideProfileId → guide_profiles.user_id` 해석 후 `user_follows`에 위임.
- **가이드 followerCount 재배선**: `GuideController.list`(배치)·`detail` 카운트를
  `user_follows(followed_user_id = guide.userId)` 기준으로 교체(기존 `guideProfileId` 기준 폐기).
  `isFollowing`도 `user_follows(follower=viewer, followed=guide.userId)`로.

### 프론트
- 신규 `components/FollowButton.tsx`(**userId 기반**): 팔로우/언팔로우 토글, 낙관적 업데이트, 비로그인 → `/login`.
- 가이드 상세: 기존 팔로우 버튼을 `guideUserId`(이미 `GuideDetailResponse`에 존재) 기반 `FollowButton`으로 교체.
- 기존 가이드 카드의 팔로우/팔로워 표기는 어댑터 덕에 그대로 동작(점진 교체).

### i18n
`follow.*`(팔로우/팔로잉/팔로워 라벨) ko/en/zh — 기존 키 재사용 확인 후 부족분 추가.

---

## Phase 2 — 공개 프로필 `/users/[handle]`

### 백엔드 (`auth/` 또는 신규 `profile/`)
- `GET /api/users/{handle}` (공개, `@AuthenticationPrincipal` optional):
  `{ userId, handle, name, avatarUrl, nationality, mbti?, interests?, isGuide, guideProfileId?,
     followerCount, followingCount, isFollowing }`.
  존재하지 않는 handle → 404. 비로그인 → `isFollowing=false`.
- `GET /api/users/{handle}/posts` (공개): 그 사용자가 쓴 커뮤니티 게시글(이미지 글 + 코스 공유 글),
  최신순. 기존 `GuidePost` where `author_user_id = userId` (+가이드면 guide_profile 글 포함 여부는 스펙 확정 시 결정 — 기본: `author_user_id` 기준 단일 소스).
  차단(§Safety) 사용자 상호 숨김 규칙 적용.

### 프론트
- 신규 `app/users/[handle]/page.tsx`:
  - 헤더: 아바타, 이름/handle, 팔로워·팔로잉 수, `FollowButton`(본인이면 숨김·"프로필 편집"),
    가이드면 "가이드 프로필 보기"(`/guides/{guideProfileId}`) 링크.
  - 본문: 게시글 **인스타 그리드**(이미지 글=썸네일, 코스 공유 글=`PlanCard` 미리보기 타일). 탭 클릭 시 상세.
  - 트랙 공용(투어/동행 어느 세계에서도 접근 가능).
- `PostCard.tsx` 작성자 링크: 여행자 작성자 → `/users/{authorHandle}`, 가이드 작성자 → `/guides/{guideProfileId}` 유지.
- `FeedPost`/`GuidePostWithGuideResponse`에 `authorHandle`·`authorUserId` 추가(여행자 링크용).

### i18n
`publicProfile.*`(팔로워/팔로잉/게시글/가이드프로필보기/프로필편집 등) ko/en/zh.

---

## Phase 3 — 여행 코스 커뮤니티 공유

### 재사용 자산
- `PEERUP::PLAN::` 스냅샷 규약 + `lib/placeCard.ts`(`parseCard`) + `components/PlanCard.tsx`(채팅 플랜 공유용, 기존).
- `lib/followCourse.ts`("이 코스 따라하기" — 코스를 새 일정으로 복사, 기존).
- `GuidePost.fromTraveler(...)` DTO 팩토리 이미 존재(여행자 글쓰기 지원). `content` NOT NULL, `imageUrl` nullable.

### 흐름
- `/trips/[id]`에 **"커뮤니티에 공유"** 버튼 → `PostComposeModal`을 **플랜 프리필**로 오픈:
  본문 앞에 `PEERUP::PLAN::{스냅샷}` 규약 인코딩 + 사용자 캡션. 이미지 없이 발행 가능(content만 필수).
- `PostCard.tsx`: `content`가 PLAN 규약이면 `parseCard`로 파싱해 **`PlanCard` 렌더**(썸네일 대신). 좋아요·댓글 기존대로.
- 플랜 카드에 **"이 코스로 내 일정 만들기"** 액션 → `followCourse` 계열 로직으로 새 `Itinerary` 생성(정차지→`ItineraryItem`).
  (재사용 범위: 플랜 스냅샷의 정차지 → itinerary item 매핑. 기존 `followCourse`는 코스 기반 — 플랜 스냅샷용 어댑터 추가 여부는 플랜 단계에서 확정.)

### 백엔드
- 새 엔티티 없음. `POST /api/posts`(기존, multipart)로 여행자 글 발행 — `content`에 규약 문자열.
- 서버는 `content` 불투명 취급(규약 파싱은 프론트). 기존 규약 저장/조회 그대로.

### i18n
`travelerHome`/`community` 그룹에 "커뮤니티에 공유"·"이 코스로 내 일정 만들기" ko/en/zh.

---

## Phase 4 — 상호작용 마감

- **팔로우 버튼 표면 통일**: 공개 프로필·가이드 상세·게시글 작성자·`/traveler/following`·`/community` 모두 `FollowButton`(userId).
- **`/traveler/following` 일반화**: 팔로우한 **사용자**(가이드+여행자) 목록. `FollowingUserResponse` 소비,
  가이드·여행자 카드 렌더 분기. 사이드바 `💙 following` 유지.
- **두 트랙 노출**: 공개 프로필·커뮤니티·팔로우는 **트랙 공용 표면** — `TrackGate`/사이드바 트랙 분리와 무충돌
  (동행 세계에서도 '가이드' 카피 노출 규칙은 기존 정책 유지: 동행 컨텍스트에서 가이드 승격 유도 금지).

---

## 범위 밖 (Out of scope)

- 여행자를 유상 동행 파트너로 자기 등록(§38 — 브레인스토밍에서 소비자+소셜로 확정).
- 팔로우 알림·피드 개인화·팔로우 기반 추천 랭킹.
- 공개 프로필의 DM 진입(기존 DM/인박스 그대로).
- 기존 `follows` 테이블/`SavedItemType.GUIDE` 상수·행 **삭제**(보존; 삭제는 별도 사용자 확인).

## 검증 방법

- **백엔드**: `gradle compileJava` + 엔드포인트별 curl E2E — 팔로우 멱등/자기팔로우400/언팔로우/카운트/following 목록,
  공개 프로필 조회(비로그인 isFollowing=false·404), 저장됨 GUIDE 거부·ids/counts 필터, 백필 idempotent.
- **프론트**: `npx tsc --noEmit` 0, `next lint` 클린, i18n ko/en/zh 3블록 대조.
- **수동 스모크**: 여행자↔여행자·여행자↔가이드 상호 팔로우, 공개 프로필 접근, 코스 커뮤니티 공유→피드 PlanCard→따라하기,
  저장됨 프로필 탭 그리드, 사이드바에 `/saved` 없음, 가이드 저장 UI 제거 확인.

## 열린 확인 항목 (플랜 단계에서 확정)

1. 공개 프로필 게시글 소스: 가이드의 `guide_profile` 글까지 합칠지 vs `author_user_id` 단일 기준.
2. 플랜 스냅샷 → itinerary "따라하기" 어댑터의 정확한 필드 매핑.
3. 프로필 저장됨 탭 vs `/saved` 라우트 최종 형태(탭 흡수 + 리다이렉트 확정안).
