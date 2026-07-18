# 관리자 포털 (Admin Portal) — 설계 문서

- 날짜: 2026-07-18
- 브랜치: feat/world-identity-ux (별도 브랜치 권장)
- 목표: 기존 자격인증/신고 두 페이지를 관리자 전용 **포털**로 통합하고, 회원 관리·모더레이션·운영 통계·예약 조회 기능을 추가한다.

## 배경 (현재 상태)

- 백엔드 `admin/` 패키지: `AdminVerificationController`(자격인증 승인/반려), `AdminReportController`(신고 review/dismiss)뿐.
- 프론트: `/admin/verifications`, `/admin/reports` 두 페이지만. **공통 레이아웃·대시보드·진입 메뉴 없음.**
- 접근 제어: `SecurityConfig` `/api/admin/** → hasRole("ADMIN")`. ADMIN 승격은 DB 직접(부트스트랩).
- **프론트가 자기 role을 모름**: 로그인은 `accessToken`만 저장, admin 페이지는 API 403이면 막는 방식.
- `User` 엔티티에 **정지 상태 필드 없음**. `getRole()`은 null→USER (null-tolerant, ddl-auto 안전).
- `GuidePost`에 **visibility/hidden 필드 없음**.
- `Report` 엔티티: `targetType`/`targetId`로 사용자/대화/게시글/리뷰를 가리킴. **그러나 검토는 status(OPEN→REVIEWED/DISMISSED)만 바꾸고 대상에 실제 조치를 못 함** → 모더레이션과 통합 필요.

## 범위 결정 (사용자 확정)

- 담을 기능: 회원 관리, 가이드/게시글 관리(모더레이션), 운영 통계 대시보드, 예약/거래 조회 + 기존 자격인증/신고.
- 깊이: 전부 깊게(검색·필터·상세·액션). 단, 구현은 아래 5단계로 나눠 각 단계 리뷰.
- 회원 액션: **정지/해제만**. ADMIN 승격·계정 완전삭제는 UI에서 제외(DB 전용 유지).
- 정지 반영: **즉시 반영** — JWT 필터에서 status 조회.

## 아키텍처

CLAUDE.md 멀티에이전트 순서 준수: **DBA(스키마) → developer(엔드포인트·role응답·가드) → designer(페이지·레이아웃·i18n)**.

### A. 진입 & 공통 셸 — **메인 앱과 분리된 독립 포털**

- **메인 진입점 없음**: `Sidebar`에 운영자 메뉴를 넣지 **않는다**. 메인 앱 어디에도 `/admin` 링크가 없다. 관리자는 URL 직접 진입(북마크).
- **로그인 응답에 `role` 추가.** `TokenResponse`에 `role` 필드 추가.
- `api.ts`에 `saveRole/getRole/isAdmin()` 헬퍼(기존 `saveUserName` 패턴). role 저장/로그아웃 시 제거.
- **`app/admin/layout.tsx`** (신규, client) = 자체 게이트:
  - 방문자가 `isAdmin()`가 아니면 → **전용 관리자 로그인 화면** 렌더(포털 콘텐츠 대신). 메인 `/login`과 별개의 독립 화면.
  - 전용 로그인은 같은 `POST /api/auth/login` 호출 → 응답 `role`이 `ADMIN`이면 token+role 저장 후 포털 진입. `ADMIN`이 아니면 "관리자 권한이 없습니다" 에러 + 진입 거부(토큰 폐기).
  - 관리자면 → 상단 탭 내비 + 포털 콘텐츠. 탭: `대시보드 / 회원 / 가이드·게시글 / 예약 / 자격인증 / 신고`.
- 기존 `/admin/verifications`, `/admin/reports`를 이 레이아웃 아래로 재편입(파일 이동 불필요, layout이 감쌈).
- 최종 방어는 백엔드 `hasRole("ADMIN")`. 전용 로그인/가드는 UX 계층일 뿐 보안 경계가 아님.

### B. 대시보드 홈 `/admin`

- 백엔드: `GET /api/admin/stats` → `AdminStatsDto` { 총 회원 수, 가이드 수, 최근 7일 신규가입, 예약 상태별 건수, 대기 자격인증 수, 미처리 신고 수 }.
- 프론트: 통계 카드 + 대기 큐(자격인증/신고) 바로가기.
- 구현 주의: 카운트는 repository `count*` 쿼리로. N+1 회피.

### C. 회원 관리 `/admin/users`

- **DBA**: `users.status` 컬럼 신규 — `UserStatus { ACTIVE, SUSPENDED }`, `@Enumerated(STRING)`.
  `getStatus()`는 **null→ACTIVE** (getRole 패턴 그대로 → 기존 행 백필 불필요). 선택: `suspended_at`, `suspended_reason`.
- 백엔드:
  - `GET /api/admin/users?query=&status=&page=&size=` — 이메일/닉네임 부분검색, 상태 필터, 페이징. 응답 DTO에 password 절대 미포함.
  - `POST /api/admin/users/{id}/suspend` (reason optional), `POST /api/admin/users/{id}/reactivate`.
  - 자기 자신·다른 ADMIN 정지는 거부(안전장치).
- **정지 즉시 반영**: `JwtAuthenticationFilter`에서 인증 시 유저 조회 후 `status==SUSPENDED`면 인증 실패(401). PK 1회 조회 추가(허용). 향후 캐시 가능.
- 프론트: 검색/필터 목록, 상태 배지, 정지/해제 버튼(확인 모달 + 사유 입력).

### D. 모더레이션 `/admin/posts` (+ 가이드) — 신고와 통합

- **DBA**: `guide_posts.hidden` boolean 신규. `isHidden()` null→false. 일반 피드 조회 쿼리는 `hidden=false`만 노출.
- **공유 모더레이션 서비스** `ModerationService`:
  - `hidePost(postId)` / `unhidePost(postId)` / `deletePost(postId)`
  - 회원 정지는 C의 서비스 재사용(가이드=유저).
- 사용처 두 곳이 **같은 서비스**를 호출 → 표면 불일치 방지:
  1. `/admin/posts` 브라우징 화면(검색·필터·숨김/삭제).
  2. **신고 검토 화면 개선**: `AdminReportService`가 `targetType/targetId`를 읽어, 검토 시 대상에 실제 조치(게시글 숨김/유저 정지) 버튼 제공. review/dismiss는 유지하되 "조치 후 review" 흐름 추가.
- 가이드 관리: 가이드 목록 + 활동정지(=유저 status 정지)로 C와 동일 메커니즘 재사용.

### E. 예약/거래 조회 `/admin/bookings`

- `GET /api/admin/bookings?status=&page=&size=` 읽기 + 상세. `hourly_rate_snapshot` 등은 표시만(절대 재계산 금지).
- `POST /api/admin/bookings/{id}/cancel` — 문제 예약 강제 취소(사유 optional). 기존 booking 상태머신 재사용.

### i18n

- 모든 신규 UI 문자열은 `src/lib/i18n.ts`에 **ko/en/zh 3개 언어 전부** 추가(admin 네임스페이스 신설 권장).

## 데이터 흐름 (정지 예시)

관리자가 `/admin/users`에서 정지 → `POST /suspend` → `users.status=SUSPENDED` 저장 → 이후 그 유저의 모든 인증 요청은 `JwtAuthenticationFilter`에서 status 확인 → 401. 로그인도 거부.

## 스키마 변경 요약 (모두 null-tolerant, ddl-auto: update 안전)

| 테이블 | 컬럼 | 타입 | 기본/널 처리 |
|--------|------|------|--------------|
| users | status | varchar (enum STRING) | null → ACTIVE |
| users | suspended_at | timestamp | nullable (optional) |
| users | suspended_reason | varchar | nullable (optional) |
| guide_posts | hidden | boolean | null → false |

DROP 없음. 컬럼 추가만.

## 에러 처리

- 모든 `/api/admin/**`는 hasRole("ADMIN")로 보호(기존). 비관리자 403.
- 프론트 admin layout이 client-side 가드로 UX 보정(비관리자 즉시 리다이렉트), 최종 방어는 백엔드.
- 정지/취소/삭제는 확인 모달 필수. 되돌릴 수 없는 삭제만 별도 경고.

## 테스트

- 백엔드: suspend 후 해당 유저 토큰으로 요청 → 401. 비ADMIN 토큰으로 `/api/admin/**` → 403. stats 카운트 정확성. hidden 게시글이 일반 피드에서 제외.
- 자기/다른 ADMIN 정지 거부.
- 프론트: 비관리자/비로그인이 `/admin` 접근 시 **전용 로그인 화면** 표시(포털 콘텐츠 미노출). 비ADMIN 계정으로 전용 로그인 시 "권한 없음" 에러. 메인 앱 어디에도 `/admin` 링크가 없음(Sidebar 미노출) 확인. 목록 검색/필터/페이징. 액션 후 목록 갱신.

## 구현 단계 (각 단계 리뷰 체크포인트)

1. **셸 + 대시보드**: role 응답/저장, isAdmin, Sidebar 메뉴, admin layout+가드, `/api/admin/stats`, `/admin` 홈.
2. **회원 관리**: users.status(DBA), users 목록/정지/해제 API, JWT 필터 즉시반영, `/admin/users`.
3. **모더레이션**: guide_posts.hidden(DBA), ModerationService, `/admin/posts`, 신고 검토 화면에 대상 조치 연결.
4. **예약 조회**: `/api/admin/bookings` + `/admin/bookings`, 강제 취소.
5. **마감**: i18n 3언어 정리, 자격인증/신고 페이지 셸 통합 확인, 회귀 테스트.

## 명시적 비범위 (YAGNI)

- ADMIN 승격 UI / 계정 완전삭제 UI (DB 전용 유지).
- 감사 로그(audit trail) — 후속 과제.
- 정지 status 캐시 — 우선 매요청 PK 조회, 성능 문제 시 후속.
