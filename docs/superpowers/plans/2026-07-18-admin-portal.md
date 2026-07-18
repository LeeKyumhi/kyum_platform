# 관리자 포털 (Admin Portal) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 자격인증/신고 두 페이지를 관리자 전용 독립 포털로 통합하고 회원 관리·모더레이션·운영 통계·예약 조회를 추가한다.

**Architecture:** 백엔드는 `com.guidematch.admin` 패키지에 모듈별 Controller/Service를 추가하고 `/api/admin/**`(기존 `hasRole("ADMIN")` 보호)로 노출한다. 프론트는 메인 앱과 완전 분리된 `/admin` 라우트 그룹 — `app/admin/layout.tsx`가 자체 로그인 게이트를 두어 비관리자를 차단한다. 스키마 변경은 컬럼 추가만(null-tolerant getter로 백필 불필요, `ddl-auto: update` 안전).

**Tech Stack:** Spring Boot 3.3.5 / Java 21 / Spring Security / JPA(PostgreSQL·Supabase) / Next.js 15 · React 19 · TypeScript · Tailwind 3.

## Global Constraints

- **No new npm packages / no new Java dependencies** without explicit approval.
- **자동화 테스트 프레임워크가 없다** (backend `src/test` 0개, frontend 테스트 러너 없음). 검증은 **컴파일(`gradle build`) + 실행 중 서버에 curl 스모크 + 수동 UI 확인**으로 한다. 새 테스트 하네스를 도입하지 않는다.
- **백엔드 실행/빌드는 Java 21**: `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` 후 `app/backend`에서 `gradle build` / `gradle bootRun`. 프론트는 `app/frontend`에서 `npm run dev`.
- **DROP 금지** — 컬럼 추가만. 새 컬럼 getter는 반드시 null-tolerant(`getStatus()` null→ACTIVE, `isHidden()` null→false) — 기존 `User.getRole()`(User.java:209) 패턴을 그대로 따른다.
- **모든 신규 UI 문자열은 `src/lib/i18n.ts`에 ko/en/zh 3개 언어 전부** 추가. 동적 데이터(이름·이메일 등)는 i18n에 넣지 않는다.
- **`@AuthenticationPrincipal Long userId`** 규약 유지 — public 엔드포인트에서 null 허용, 절대 401을 직접 던지지 않는다.
- **가격은 예약 시점 스냅샷** — `hourlyRateSnapshot`/`totalPrice`는 표시만, 재계산 금지.
- **커밋은 사용자 확인 후에만.** 이 repo는 "요청 없이 커밋 금지" 방침. 각 Task의 커밋 스텝은 사용자가 커밋을 승인했을 때만 실행한다(아니면 변경만 남기고 다음 Task로).
- **모더레이션 표면 단일화**: 게시글 숨김/유저 정지 로직은 각각 한 서비스 메서드(`ModerationService.hidePost`, `AdminUserService.suspend`)에만 두고, 신고 검토 화면과 브라우징 화면 둘 다 그 메서드를 호출한다. 로직을 복제하지 않는다.

---

## File Structure

**Backend (create):**
- `admin/AdminStatsController.java`, `admin/AdminStatsService.java` — 대시보드 통계
- `user/UserStatus.java` — ACTIVE/SUSPENDED enum
- `admin/AdminUserController.java`, `admin/AdminUserService.java` — 회원 목록/정지/해제
- `admin/ModerationService.java` — 게시글 숨김/삭제 공유 로직
- `admin/AdminPostController.java`, `admin/AdminPostService.java` — 게시글 모더레이션 목록/액션
- `admin/AdminBookingController.java`, `admin/AdminBookingService.java` — 예약 조회/강제취소

**Backend (modify):**
- `auth/dto/TokenResponse.java` — `role` 필드 추가
- `auth/AuthService.java` — 로그인 반환에 role 포함 + 정지 계정 로그인 차단
- `auth/AuthController.java` — role을 응답에 전달
- `config/JwtAuthenticationFilter.java` — 정지 계정 즉시 차단(status 조회)
- `user/User.java` — `status` 필드 + getStatus/suspend/reactivate
- `user/UserRepository.java` — 검색/카운트 쿼리
- `guide/GuidePost.java` — `hidden` 필드 + isHidden/hide/unhide
- `guide/GuidePostRepository.java` — hidden 제외 피드 쿼리
- `booking/BookingRepository.java` — 페이지·상태 카운트
- `admin/AdminReportService.java` — 검토 시 대상 실제 조치 연결

**Frontend (create):**
- `app/admin/layout.tsx` — 게이트 + 전용 로그인 + 탭 내비
- `app/admin/page.tsx` — 대시보드
- `app/admin/users/page.tsx` — 회원 관리
- `app/admin/posts/page.tsx` — 게시글/가이드 모더레이션
- `app/admin/bookings/page.tsx` — 예약 조회

**Frontend (modify):**
- `lib/api.ts` — saveRole/getRole/isAdmin
- `app/login/page.tsx` — 로그인 시 role 저장
- `lib/i18n.ts` — `admin` 네임스페이스(ko/en/zh)
- `app/admin/reports/page.tsx` — 대상 조치 버튼 연결(Phase 3)

---

# PHASE 1 — 셸 + 대시보드 + role

### Task 1: 로그인 응답에 role 포함 (backend)

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/auth/dto/TokenResponse.java`
- Modify: `app/backend/src/main/java/com/guidematch/auth/AuthService.java` (login ~line 150-160)
- Modify: `app/backend/src/main/java/com/guidematch/auth/AuthController.java` (login ~line 52-55)

**Interfaces:**
- Produces: `TokenResponse(String accessToken, String tokenType, String role)`; `TokenResponse.bearer(String token, String role)`; `AuthService.LoginResult(String token, String role)`; `AuthService.login(LoginRequest)` now returns `LoginResult`.

- [ ] **Step 1: TokenResponse에 role 추가**

```java
package com.guidematch.auth.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,   // 보통 "Bearer"
        String role         // "USER" | "ADMIN" — 프론트가 관리자 포털 진입 판단에 사용
) {
    public static TokenResponse bearer(String accessToken, String role) {
        return new TokenResponse(accessToken, "Bearer", role);
    }
}
```

- [ ] **Step 2: AuthService.login이 role도 반환**

`login` 메서드를 아래로 교체(반환 타입 변경). 클래스 안에 `LoginResult` record도 추가.

```java
    /** 로그인 결과 — 토큰과 권한(role)을 함께 돌려준다. */
    public record LoginResult(String token, String role) {}

    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtProvider.createToken(user.getId(), user.getEmail(), user.getRole().name());
        return new LoginResult(token, user.getRole().name());
    }
```

- [ ] **Step 3: AuthController.login이 role 전달**

```java
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok(TokenResponse.bearer(result.token(), result.role()));
    }
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 스모크 (서버 실행 중일 때)**

Run: `curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"<기존계정>","password":"<pw>"}'`
Expected: JSON에 `"role":"USER"`(또는 ADMIN) 포함, `accessToken` 존재.

- [ ] **Step 6: Commit** (사용자 승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/auth/
git commit -m "feat(auth): include role in login response"
```

---

### Task 2: 프론트 role 저장 헬퍼 (frontend)

**Files:**
- Modify: `app/frontend/src/lib/api.ts`
- Modify: `app/frontend/src/app/login/page.tsx`

**Interfaces:**
- Consumes: 로그인 응답 `{ accessToken, tokenType, role }`.
- Produces: `saveRole(role: string)`, `getRole(): string | null`, `isAdmin(): boolean`. `clearToken()`이 role도 제거.

- [ ] **Step 1: api.ts에 role 헬퍼 추가**

`USER_NAME_KEY` 선언 아래에 `const ROLE_KEY = "userRole";` 추가. `clearToken` 안에 `localStorage.removeItem(ROLE_KEY);` 한 줄 추가. 그리고 `getUserName` 아래에 다음을 추가:

```ts
export function saveRole(role: string) {
  if (typeof window !== "undefined") localStorage.setItem(ROLE_KEY, role);
}

export function getRole(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ROLE_KEY);
}

export function isAdmin(): boolean {
  return getRole() === "ADMIN";
}
```

- [ ] **Step 2: 로그인 페이지에서 role 저장**

`app/login/page.tsx`의 `TokenResponse` 타입과 onSubmit을 수정.

```ts
type TokenResponse = { accessToken: string; tokenType: string; role: string };
```

import에 `saveRole` 추가(`import { api, saveToken, saveUserName, saveRole } from "@/lib/api";`). onSubmit의 `saveToken(res.accessToken);` 바로 아래에 `saveRole(res.role);` 추가.

- [ ] **Step 3: 타입/린트 확인**

Run: `cd app/frontend && npx tsc --noEmit`
Expected: 에러 없음.

- [ ] **Step 4: 수동 확인**

`npm run dev` 상태에서 로그인 → 브라우저 콘솔 `localStorage.getItem("userRole")`가 `"USER"`/`"ADMIN"`.

- [ ] **Step 5: Commit** (승인 시)

```bash
git add app/frontend/src/lib/api.ts app/frontend/src/app/login/page.tsx
git commit -m "feat(auth): store user role client-side"
```

---

### Task 3: 대시보드 통계 API (backend)

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/admin/AdminStatsService.java`
- Create: `app/backend/src/main/java/com/guidematch/admin/AdminStatsController.java`
- Modify: `app/backend/src/main/java/com/guidematch/booking/BookingRepository.java`
- Modify: `app/backend/src/main/java/com/guidematch/user/UserRepository.java`
- Modify: `app/backend/src/main/java/com/guidematch/guide/GuideVerificationRepository.java`
- Modify: `app/backend/src/main/java/com/guidematch/safety/ReportRepository.java`

**Interfaces:**
- Produces: `GET /api/admin/stats` → `AdminStatsService.StatsDto(long totalUsers, long totalGuides, long newUsers7d, long bookingsRequested, long bookingsAccepted, long bookingsCompleted, long pendingVerifications, long openReports)`.
- Consumes (later tasks): 없음.

- [ ] **Step 1: 리포지토리 카운트 메서드 추가**

BookingRepository에 추가:
```java
    long countByStatus(BookingStatus status);
```
UserRepository에 추가(import `java.time.Instant`):
```java
    long countByCreatedAtAfter(java.time.Instant since);
```
GuideVerificationRepository에 추가:
```java
    long countByStatus(VerificationStatus status);
```
ReportRepository에 추가:
```java
    long countByStatus(String status);
```

- [ ] **Step 2: AdminStatsService 작성**

`GuideProfileRepository`는 `count()`(JpaRepository 기본) 사용 → 활성 가이드 총원. `newUsers7d`는 최근 7일.

```java
package com.guidematch.admin;

import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.GuideVerificationRepository;
import com.guidematch.guide.VerificationStatus;
import com.guidematch.safety.ReportRepository;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 관리자 대시보드 통계. 모든 값은 count 쿼리로만 계산(N+1 없음). */
@Service
public class AdminStatsService {

    private final UserRepository userRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final BookingRepository bookingRepository;
    private final GuideVerificationRepository verificationRepository;
    private final ReportRepository reportRepository;

    public AdminStatsService(UserRepository userRepository,
                             GuideProfileRepository guideProfileRepository,
                             BookingRepository bookingRepository,
                             GuideVerificationRepository verificationRepository,
                             ReportRepository reportRepository) {
        this.userRepository = userRepository;
        this.guideProfileRepository = guideProfileRepository;
        this.bookingRepository = bookingRepository;
        this.verificationRepository = verificationRepository;
        this.reportRepository = reportRepository;
    }

    public record StatsDto(
            long totalUsers,
            long totalGuides,
            long newUsers7d,
            long bookingsRequested,
            long bookingsAccepted,
            long bookingsCompleted,
            long pendingVerifications,
            long openReports
    ) {}

    @Transactional(readOnly = true)
    public StatsDto load() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        return new StatsDto(
                userRepository.count(),
                guideProfileRepository.count(),
                userRepository.countByCreatedAtAfter(weekAgo),
                bookingRepository.countByStatus(BookingStatus.REQUESTED),
                bookingRepository.countByStatus(BookingStatus.ACCEPTED),
                bookingRepository.countByStatus(BookingStatus.COMPLETED),
                verificationRepository.countByStatus(VerificationStatus.PENDING),
                reportRepository.countByStatus("OPEN")
        );
    }
}
```

- [ ] **Step 3: AdminStatsController 작성**

```java
package com.guidematch.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 대시보드 통계 API. /api/admin/** → hasRole("ADMIN")로 보호됨. */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping
    public AdminStatsService.StatsDto stats() {
        return adminStatsService.load();
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 스모크 (ADMIN 토큰으로)**

Run: `curl -s localhost:8080/api/admin/stats -H "Authorization: Bearer <ADMIN_JWT>"`
Expected: 8개 필드 JSON. 비ADMIN 토큰이면 403.

- [ ] **Step 6: Commit** (승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/admin/AdminStats*.java app/backend/src/main/java/com/guidematch/booking/BookingRepository.java app/backend/src/main/java/com/guidematch/user/UserRepository.java app/backend/src/main/java/com/guidematch/guide/GuideVerificationRepository.java app/backend/src/main/java/com/guidematch/safety/ReportRepository.java
git commit -m "feat(admin): dashboard stats endpoint"
```

---

### Task 4: 관리자 포털 셸 — 레이아웃 + 전용 로그인 게이트 (frontend)

**Files:**
- Create: `app/frontend/src/app/admin/layout.tsx`
- Modify: `app/frontend/src/lib/i18n.ts` (admin 네임스페이스 — 최소 키; Task별로 확장)

**Interfaces:**
- Consumes: `isAdmin()`, `getToken()`, `saveToken`, `saveRole`, `clearToken` from `@/lib/api`; `POST /api/auth/login`.
- Produces: `/admin/**` 전체를 감싸는 게이트. 관리자 아니면 전용 로그인 폼, 맞으면 탭 내비 + children.

- [ ] **Step 1: i18n에 admin 네임스페이스 추가 (ko/en/zh 3곳 모두)**

`src/lib/i18n.ts`의 `ko` 객체에 새 키 블록을 추가(적당한 위치, 예: `adminReports` 근처):

```ts
    admin: {
      loginTitle: "관리자 로그인",
      loginSub: "운영자 계정으로만 접근할 수 있습니다.",
      email: "이메일",
      password: "비밀번호",
      loginBtn: "로그인",
      loggingIn: "로그인 중…",
      notAdmin: "관리자 권한이 없는 계정입니다.",
      logout: "로그아웃",
      portalTitle: "관리자 포털",
      tabDashboard: "대시보드",
      tabUsers: "회원",
      tabModeration: "가이드·게시글",
      tabBookings: "예약",
      tabVerifications: "자격인증",
      tabReports: "신고",
    },
```

`en` 객체에 동일 키(영문):
```ts
    admin: {
      loginTitle: "Admin login",
      loginSub: "Operators only.",
      email: "Email",
      password: "Password",
      loginBtn: "Sign in",
      loggingIn: "Signing in…",
      notAdmin: "This account is not an administrator.",
      logout: "Log out",
      portalTitle: "Admin Portal",
      tabDashboard: "Dashboard",
      tabUsers: "Members",
      tabModeration: "Guides & Posts",
      tabBookings: "Bookings",
      tabVerifications: "Verifications",
      tabReports: "Reports",
    },
```

`zh` 객체에 동일 키(중문):
```ts
    admin: {
      loginTitle: "管理员登录",
      loginSub: "仅限运营人员访问。",
      email: "邮箱",
      password: "密码",
      loginBtn: "登录",
      loggingIn: "登录中…",
      notAdmin: "该账号不是管理员。",
      logout: "退出登录",
      portalTitle: "管理后台",
      tabDashboard: "仪表盘",
      tabUsers: "会员",
      tabModeration: "向导与帖子",
      tabBookings: "预约",
      tabVerifications: "资格认证",
      tabReports: "举报",
    },
```

- [ ] **Step 2: admin/layout.tsx 작성**

메인 앱과 분리 — 이 레이아웃 자체가 게이트. 비관리자는 children을 렌더하지 않고 전용 로그인 폼만 보여준다. `layout.tsx`(루트)의 `md:pl-64` 오프셋 영향을 피하려면 자체 풀스크린 컨테이너를 쓴다.

```tsx
"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { api, getToken, saveToken, saveRole, clearToken, isAdmin } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type TokenResponse = { accessToken: string; tokenType: string; role: string };

const TABS = [
  { href: "/admin",              key: "tabDashboard" as const },
  { href: "/admin/users",        key: "tabUsers" as const },
  { href: "/admin/posts",        key: "tabModeration" as const },
  { href: "/admin/bookings",     key: "tabBookings" as const },
  { href: "/admin/verifications",key: "tabVerifications" as const },
  { href: "/admin/reports",      key: "tabReports" as const },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { t } = useLanguage();
  const a = t.admin;
  const pathname = usePathname();

  const [ready, setReady]   = useState(false);
  const [authed, setAuthed] = useState(false);
  const [form, setForm]     = useState({ email: "", password: "" });
  const [error, setError]   = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setAuthed(!!getToken() && isAdmin());
    setReady(true);
  }, []);

  async function onLogin(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setLoading(true);
    try {
      const res = await api<TokenResponse>("/api/auth/login", { method: "POST", body: form });
      if (res.role !== "ADMIN") { setError(a.notAdmin); return; }  // 토큰 저장 안 함
      saveToken(res.accessToken);
      saveRole(res.role);
      setAuthed(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally {
      setLoading(false);
    }
  }

  function onLogout() {
    clearToken();
    setAuthed(false);
  }

  if (!ready) return null;

  // ── 게이트: 관리자 아님 → 전용 로그인 화면 ──
  if (!authed) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-stone-50 px-4">
        <div className="w-full max-w-sm">
          <div className="mb-6 text-center">
            <h1 className="text-2xl font-extrabold text-stone-900">{a.loginTitle}</h1>
            <p className="mt-1.5 text-sm text-stone-500">{a.loginSub}</p>
          </div>
          <form onSubmit={onLogin} className="card flex flex-col gap-4 p-8 shadow-lg">
            <div>
              <label className="input-label">{a.email}</label>
              <input type="email" required className="input" value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div>
              <label className="input-label">{a.password}</label>
              <input type="password" required className="input" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </div>
            {error && <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>}
            <button type="submit" disabled={loading} className="btn-primary mt-2 w-full py-3">
              {loading ? a.loggingIn : a.loginBtn}
            </button>
          </form>
        </div>
      </div>
    );
  }

  // ── 포털 셸: 상단 탭 내비 + children ──
  return (
    <div className="fixed inset-0 z-40 flex flex-col bg-stone-50">
      <header className="flex items-center justify-between border-b border-stone-200 bg-white px-6 py-3">
        <div className="flex items-center gap-6">
          <span className="font-extrabold text-stone-900">{a.portalTitle}</span>
          <nav className="flex gap-1">
            {TABS.map((tab) => {
              const active = tab.href === "/admin" ? pathname === "/admin" : pathname.startsWith(tab.href);
              return (
                <Link key={tab.href} href={tab.href}
                  className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                    active ? "bg-sky-100 text-sky-700" : "text-stone-500 hover:bg-stone-100"
                  }`}>
                  {a[tab.key]}
                </Link>
              );
            })}
          </nav>
        </div>
        <button onClick={onLogout} className="text-sm font-medium text-stone-500 hover:text-stone-800">
          {a.logout}
        </button>
      </header>
      <main className="flex-1 overflow-y-auto p-6">{children}</main>
    </div>
  );
}
```

- [ ] **Step 3: 타입 확인**

Run: `cd app/frontend && npx tsc --noEmit`
Expected: 에러 없음.

- [ ] **Step 4: 수동 확인**

`npm run dev` → `http://localhost:3000/admin` 접속: (a) 비로그인/USER면 전용 로그인 폼, (b) USER 계정으로 로그인 시 "관리자 권한이 없는 계정입니다", (c) ADMIN 계정이면 탭 내비 표시. 메인 앱 Sidebar에는 admin 링크가 없음(변경 안 했으므로 확인만).

- [ ] **Step 5: Commit** (승인 시)

```bash
git add app/frontend/src/app/admin/layout.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(admin): standalone portal shell with dedicated login gate"
```

---

### Task 5: 대시보드 페이지 (frontend)

**Files:**
- Create: `app/frontend/src/app/admin/page.tsx`
- Modify: `app/frontend/src/lib/i18n.ts` (admin에 대시보드 라벨 키 추가 — 3언어)

**Interfaces:**
- Consumes: `GET /api/admin/stats` → StatsDto(Task 3), `api(..., { auth: true })`.

- [ ] **Step 1: i18n admin에 대시보드 라벨 추가 (ko/en/zh)**

ko: `statTotalUsers: "총 회원", statGuides: "가이드", statNew7d: "신규(7일)", statRequested: "예약 요청", statAccepted: "예약 확정", statCompleted: "완료", statPendingVerif: "인증 대기", statOpenReports: "미처리 신고", queueGo: "처리하러 가기",`
en: `statTotalUsers: "Total users", statGuides: "Guides", statNew7d: "New (7d)", statRequested: "Requested", statAccepted: "Accepted", statCompleted: "Completed", statPendingVerif: "Pending verifications", statOpenReports: "Open reports", queueGo: "Go handle",`
zh: `statTotalUsers: "总会员", statGuides: "向导", statNew7d: "新增(7天)", statRequested: "预约请求", statAccepted: "已确认", statCompleted: "已完成", statPendingVerif: "待认证", statOpenReports: "未处理举报", queueGo: "去处理",`

- [ ] **Step 2: 대시보드 페이지 작성**

```tsx
"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type Stats = {
  totalUsers: number; totalGuides: number; newUsers7d: number;
  bookingsRequested: number; bookingsAccepted: number; bookingsCompleted: number;
  pendingVerifications: number; openReports: number;
};

export default function AdminDashboardPage() {
  const { t } = useLanguage();
  const a = t.admin;
  const [stats, setStats] = useState<Stats | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Stats>("/api/admin/stats", { auth: true })
      .then(setStats)
      .catch((e) => setError(e instanceof Error ? e.message : t.common.error));
  }, [t.common.error]);

  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!stats) return <p className="text-sm text-stone-500">…</p>;

  const cards = [
    { label: a.statTotalUsers, value: stats.totalUsers },
    { label: a.statGuides, value: stats.totalGuides },
    { label: a.statNew7d, value: stats.newUsers7d },
    { label: a.statRequested, value: stats.bookingsRequested },
    { label: a.statAccepted, value: stats.bookingsAccepted },
    { label: a.statCompleted, value: stats.bookingsCompleted },
  ];

  return (
    <div className="mx-auto max-w-5xl">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {cards.map((c) => (
          <div key={c.label} className="card p-5">
            <div className="text-3xl font-extrabold text-stone-900">{c.value}</div>
            <div className="mt-1 text-sm text-stone-500">{c.label}</div>
          </div>
        ))}
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <Link href="/admin/verifications" className="card flex items-center justify-between p-5 hover:shadow-md">
          <div><div className="text-2xl font-bold">{stats.pendingVerifications}</div>
            <div className="text-sm text-stone-500">{a.statPendingVerif}</div></div>
          <span className="text-sm font-medium text-sky-600">{a.queueGo} →</span>
        </Link>
        <Link href="/admin/reports" className="card flex items-center justify-between p-5 hover:shadow-md">
          <div><div className="text-2xl font-bold">{stats.openReports}</div>
            <div className="text-sm text-stone-500">{a.statOpenReports}</div></div>
          <span className="text-sm font-medium text-sky-600">{a.queueGo} →</span>
        </Link>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 타입 확인**

Run: `cd app/frontend && npx tsc --noEmit`
Expected: 에러 없음.

- [ ] **Step 4: 수동 확인**

ADMIN으로 `/admin` → 6개 통계 카드 + 대기 큐 2개 렌더. 큐 클릭 시 각 페이지로 이동.

- [ ] **Step 5: Commit** (승인 시)

```bash
git add app/frontend/src/app/admin/page.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(admin): dashboard page"
```

**✅ PHASE 1 체크포인트** — ADMIN이 `/admin`에서 로그인→대시보드까지 동작. 리뷰 후 진행.

---

# PHASE 2 — 회원 관리 (정지 즉시 반영)

### Task 6: UserStatus 컬럼 (DBA)

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/user/UserStatus.java`
- Modify: `app/backend/src/main/java/com/guidematch/user/User.java`

**Interfaces:**
- Produces: `UserStatus { ACTIVE, SUSPENDED }`; `User.getStatus()`(null→ACTIVE), `User.suspend(String reason)`, `User.reactivate()`, `User.getSuspendedReason()`, `User.getSuspendedAt()`.

- [ ] **Step 1: UserStatus enum 작성**

```java
package com.guidematch.user;

/**
 * 계정 상태. 기본 ACTIVE, 운영자가 정지하면 SUSPENDED.
 * null(기존 회원 포함)은 ACTIVE로 취급한다(백필 불필요, ddl-auto 안전).
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
```

- [ ] **Step 2: User 엔티티에 status 필드 추가**

`role` 필드 블록(User.java ~89-91) 아래에 추가:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private UserStatus status;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_reason", columnDefinition = "text")
    private String suspendedReason;
```

그리고 `getRole()`/`setRole()` 근처(User.java ~209)에 getter/도우미 추가:

```java
    public UserStatus getStatus() {
        return status == null ? UserStatus.ACTIVE : status;
    }

    public boolean isSuspended() {
        return getStatus() == UserStatus.SUSPENDED;
    }

    public void suspend(String reason) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
        this.suspendedReason = (reason == null || reason.isBlank()) ? null : reason.trim();
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspendedReason = null;
    }

    public Instant getSuspendedAt() { return suspendedAt; }
    public String getSuspendedReason() { return suspendedReason; }
```

(`Instant`는 이미 import되어 있음 — createdAt에서 사용 중.)

- [ ] **Step 3: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 스키마 반영 확인**

`gradle bootRun`으로 서버 기동 후(ddl-auto: update) Supabase에서 `users` 테이블에 `status`, `suspended_at`, `suspended_reason` 컬럼이 생겼는지, 기존 행은 `status`가 NULL인지 확인. 앱 로그인은 여전히 정상(getStatus null→ACTIVE).

- [ ] **Step 5: Commit** (승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/user/UserStatus.java app/backend/src/main/java/com/guidematch/user/User.java
git commit -m "feat(user): add account status (ACTIVE/SUSPENDED) column"
```

---

### Task 7: 정지 즉시 반영 — 로그인 차단 + JWT 필터 (backend)

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/auth/AuthService.java` (login)
- Modify: `app/backend/src/main/java/com/guidematch/config/JwtAuthenticationFilter.java`

**Interfaces:**
- Consumes: `User.isSuspended()`(Task 6), `UserRepository`.
- Produces: 정지 계정은 로그인 거부(IllegalArgumentException) + 기존 토큰으로도 모든 인증 요청에서 401.

- [ ] **Step 1: 로그인에서 정지 계정 차단**

AuthService.login의 비밀번호 검증 통과 직후(토큰 생성 전)에 추가:

```java
        if (user.isSuspended()) {
            throw new IllegalArgumentException("정지된 계정입니다. 고객센터에 문의하세요.");
        }
```

- [ ] **Step 2: JWT 필터에 UserRepository 주입 + 정지 확인**

`JwtAuthenticationFilter`를 아래로 수정. 생성자에 `UserRepository` 추가, 토큰 파싱 후 `userId`로 조회해 정지면 `SecurityContextHolder.clearContext()` 후 계속(=인증 없음 → 보호 라우트 401).

생성자/필드:
```java
    private final JwtProvider jwtProvider;
    private final com.guidematch.user.UserRepository userRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider,
                                   com.guidematch.user.UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }
```

`Long userId = jwtProvider.getUserId(token);` 바로 다음에 정지 확인 추가:
```java
                // 정지 계정 즉시 차단: 토큰이 유효해도 status=SUSPENDED면 인증하지 않는다.
                boolean suspended = userRepository.findById(userId)
                        .map(com.guidematch.user.User::isSuspended)
                        .orElse(false);
                if (suspended) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 스모크 (Task 8 완료 후 통합 검증 권장, 지금은 수동)**

임시로 Supabase에서 한 유저 `status='SUSPENDED'`로 바꾼 뒤: (a) 그 계정 로그인 → "정지된 계정입니다", (b) 그 계정의 기존 토큰으로 `curl localhost:8080/api/users/me -H "Authorization: Bearer <token>"` → 401. 확인 후 원복.

- [ ] **Step 5: Commit** (승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/auth/AuthService.java app/backend/src/main/java/com/guidematch/config/JwtAuthenticationFilter.java
git commit -m "feat(auth): enforce account suspension at login and per-request"
```

---

### Task 8: 회원 목록/정지/해제 API (backend)

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/user/UserRepository.java`
- Create: `app/backend/src/main/java/com/guidematch/admin/AdminUserService.java`
- Create: `app/backend/src/main/java/com/guidematch/admin/AdminUserController.java`

**Interfaces:**
- Consumes: `UserRole`, `UserStatus`, `User.suspend/reactivate`(Task 6).
- Produces:
  - `GET /api/admin/users?query=&status=&page=&size=` → `AdminUserService.PageResult<UserRow>`
  - `UserRow(Long id, String email, String fullName, String nickname, String role, String status, Instant createdAt, String suspendedReason)`
  - `POST /api/admin/users/{id}/suspend` body `{reason?}`, `POST /api/admin/users/{id}/reactivate`
  - `AdminUserService.suspend(Long targetId, Long adminId, String reason)` — 공유 정지 로직(모더레이션도 이걸 호출).

- [ ] **Step 1: UserRepository에 검색 쿼리 추가**

`import org.springframework.data.domain.Page;` `import org.springframework.data.domain.Pageable;` `import org.springframework.data.jpa.repository.Query;` `import org.springframework.data.repository.query.Param;` 추가 후:

```java
    @Query("SELECT u FROM User u WHERE " +
           "(:q IS NULL OR :q = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "ORDER BY u.createdAt DESC")
    Page<User> search(@Param("q") String q,
                      @Param("status") com.guidematch.user.UserStatus status,
                      Pageable pageable);
```

주의: 기존 행 `status=NULL`은 `:status=ACTIVE` 필터에 안 걸린다. Task 6에서 신규만 채워지므로, ACTIVE 필터 시 NULL도 포함하려면 다음처럼 확장:
`"AND (:status IS NULL OR u.status = :status OR (:status = com.guidematch.user.UserStatus.ACTIVE AND u.status IS NULL)) "`
→ 위 쿼리의 status 조건 줄을 이 줄로 교체한다.

- [ ] **Step 2: AdminUserService 작성**

```java
package com.guidematch.admin;

import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import com.guidematch.user.UserRole;
import com.guidematch.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 관리자 회원 관리. 정지/해제만 허용(ADMIN 승격·삭제는 UI 비범위). */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UserRow(Long id, String email, String fullName, String nickname,
                          String role, String status, Instant createdAt, String suspendedReason) {}

    public record PageResult<T>(List<T> items, int page, int totalPages, long totalItems) {}

    @Transactional(readOnly = true)
    public PageResult<UserRow> list(String query, String status, int page, int size) {
        UserStatus statusFilter = null;
        if (status != null && !status.isBlank()) statusFilter = UserStatus.valueOf(status);
        Page<User> result = userRepository.search(
                query, statusFilter, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<UserRow> rows = result.getContent().stream().map(u -> new UserRow(
                u.getId(), u.getEmail(), u.getFullName(), u.getNickname(),
                u.getRole().name(), u.getStatus().name(), u.getCreatedAt(), u.getSuspendedReason()
        )).toList();
        return new PageResult<>(rows, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    /** 공유 정지 로직 — 회원관리 화면과 신고 검토 화면이 모두 호출. */
    @Transactional
    public void suspend(Long targetId, Long adminId, String reason) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        if (target.getId().equals(adminId)) {
            throw new IllegalArgumentException("자기 자신은 정지할 수 없습니다.");
        }
        if (target.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("관리자 계정은 정지할 수 없습니다.");
        }
        target.suspend(reason);
        userRepository.save(target);
    }

    @Transactional
    public void reactivate(Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        target.reactivate();
        userRepository.save(target);
    }
}
```

- [ ] **Step 3: AdminUserController 작성**

```java
package com.guidematch.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 관리자 회원 관리 API. /api/admin/** → hasRole("ADMIN"). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminUserService.PageResult<AdminUserService.UserRow> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.list(query, status, page, size);
    }

    @PostMapping("/{id}/suspend")
    public void suspend(@PathVariable Long id,
                        @AuthenticationPrincipal Long adminId,
                        @RequestBody(required = false) Map<String, String> body) {
        adminUserService.suspend(id, adminId, body != null ? body.get("reason") : null);
    }

    @PostMapping("/{id}/reactivate")
    public void reactivate(@PathVariable Long id) {
        adminUserService.reactivate(id);
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 스모크 (ADMIN 토큰)**

`curl -s "localhost:8080/api/admin/users?size=5" -H "Authorization: Bearer <ADMIN_JWT>"` → `items/totalPages` 포함. 한 유저 정지 → `curl -X POST localhost:8080/api/admin/users/<id>/suspend -H "Authorization: Bearer <ADMIN_JWT>" -H 'Content-Type: application/json' -d '{"reason":"test"}'` → 200. 그 유저 로그인 시 정지 메시지. 해제 후 정상.

- [ ] **Step 6: Commit** (승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/user/UserRepository.java app/backend/src/main/java/com/guidematch/admin/AdminUser*.java
git commit -m "feat(admin): member list with suspend/reactivate"
```

---

### Task 9: 회원 관리 페이지 (frontend)

**Files:**
- Create: `app/frontend/src/app/admin/users/page.tsx`
- Modify: `app/frontend/src/lib/i18n.ts` (admin에 회원관리 라벨 — 3언어)

**Interfaces:**
- Consumes: `GET /api/admin/users`, `POST /api/admin/users/{id}/suspend|reactivate`(Task 8).

- [ ] **Step 1: i18n admin에 회원관리 라벨 추가 (ko/en/zh)**

ko: `usersSearch: "이메일·닉네임 검색", usersAll: "전체", usersActive: "활성", usersSuspended: "정지", colEmail: "이메일", colName: "이름", colRole: "권한", colStatus: "상태", colJoined: "가입일", colAction: "조치", suspend: "정지", reactivate: "해제", suspendPrompt: "정지 사유(선택):", confirmSuspend: "이 회원을 정지할까요?", empty: "결과 없음", prev: "이전", next: "다음",`
en: `usersSearch: "Search email/nickname", usersAll: "All", usersActive: "Active", usersSuspended: "Suspended", colEmail: "Email", colName: "Name", colRole: "Role", colStatus: "Status", colJoined: "Joined", colAction: "Action", suspend: "Suspend", reactivate: "Reactivate", suspendPrompt: "Reason (optional):", confirmSuspend: "Suspend this member?", empty: "No results", prev: "Prev", next: "Next",`
zh: `usersSearch: "搜索邮箱/昵称", usersAll: "全部", usersActive: "活跃", usersSuspended: "已停用", colEmail: "邮箱", colName: "姓名", colRole: "权限", colStatus: "状态", colJoined: "注册日", colAction: "操作", suspend: "停用", reactivate: "恢复", suspendPrompt: "停用原因(可选):", confirmSuspend: "确定停用该会员?", empty: "无结果", prev: "上一页", next: "下一页",`

- [ ] **Step 2: 회원 관리 페이지 작성**

```tsx
"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type UserRow = {
  id: number; email: string; fullName: string; nickname: string;
  role: string; status: string; createdAt: string; suspendedReason: string | null;
};
type PageResult = { items: UserRow[]; page: number; totalPages: number; totalItems: number };

export default function AdminUsersPage() {
  const { t } = useLanguage();
  const a = t.admin;
  const [query, setQuery]   = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage]     = useState(0);
  const [data, setData]     = useState<PageResult | null>(null);
  const [error, setError]   = useState("");
  const [busy, setBusy]     = useState<number | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      const qs = new URLSearchParams({ page: String(page), size: "20" });
      if (query) qs.set("query", query);
      if (status) qs.set("status", status);
      setData(await api<PageResult>(`/api/admin/users?${qs}`, { auth: true }));
    } catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
  }, [page, query, status, t.common.error]);

  useEffect(() => { load(); }, [load]);

  async function onSuspend(u: UserRow) {
    if (!confirm(a.confirmSuspend)) return;
    const reason = prompt(a.suspendPrompt) ?? "";
    setBusy(u.id);
    try { await api(`/api/admin/users/${u.id}/suspend`, { method: "POST", body: { reason }, auth: true }); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setBusy(null); }
  }
  async function onReactivate(u: UserRow) {
    setBusy(u.id);
    try { await api(`/api/admin/users/${u.id}/reactivate`, { method: "POST", auth: true }); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setBusy(null); }
  }

  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-4 flex flex-wrap gap-2">
        <input className="input max-w-xs" placeholder={a.usersSearch} value={query}
          onChange={(e) => { setPage(0); setQuery(e.target.value); }} />
        <select className="input max-w-[8rem]" value={status}
          onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
          <option value="">{a.usersAll}</option>
          <option value="ACTIVE">{a.usersActive}</option>
          <option value="SUSPENDED">{a.usersSuspended}</option>
        </select>
      </div>
      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-stone-200 text-left text-stone-500">
            <tr>
              <th className="p-3">{a.colEmail}</th><th className="p-3">{a.colName}</th>
              <th className="p-3">{a.colRole}</th><th className="p-3">{a.colStatus}</th>
              <th className="p-3">{a.colAction}</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((u) => (
              <tr key={u.id} className="border-b border-stone-100">
                <td className="p-3">{u.email}</td>
                <td className="p-3">{u.fullName}</td>
                <td className="p-3">{u.role}</td>
                <td className="p-3">
                  <span className={u.status === "SUSPENDED" ? "text-red-600" : "text-emerald-600"}>
                    {u.status === "SUSPENDED" ? a.usersSuspended : a.usersActive}
                  </span>
                </td>
                <td className="p-3">
                  {u.role === "ADMIN" ? <span className="text-stone-400">—</span>
                    : u.status === "SUSPENDED"
                      ? <button disabled={busy === u.id} onClick={() => onReactivate(u)}
                          className="rounded-lg bg-emerald-100 px-3 py-1 font-medium text-emerald-700">{a.reactivate}</button>
                      : <button disabled={busy === u.id} onClick={() => onSuspend(u)}
                          className="rounded-lg bg-red-100 px-3 py-1 font-medium text-red-700">{a.suspend}</button>}
                </td>
              </tr>
            ))}
            {data && data.items.length === 0 && (
              <tr><td colSpan={5} className="p-6 text-center text-stone-400">{a.empty}</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3 text-sm">
          <button disabled={page === 0} onClick={() => setPage(page - 1)} className="rounded-lg px-3 py-1 disabled:opacity-40">{a.prev}</button>
          <span>{page + 1} / {data.totalPages}</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} className="rounded-lg px-3 py-1 disabled:opacity-40">{a.next}</button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: 타입 확인**

Run: `cd app/frontend && npx tsc --noEmit`
Expected: 에러 없음.

- [ ] **Step 4: 수동 확인**

`/admin/users` → 목록·검색·상태필터·페이징. 한 유저 정지 → 상태 배지 변경, 그 계정 로그인 차단. 해제 후 복구. ADMIN 행은 조치 버튼 없음(—).

- [ ] **Step 5: Commit** (승인 시)

```bash
git add app/frontend/src/app/admin/users/page.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(admin): member management page"
```

**✅ PHASE 2 체크포인트** — 회원 검색·정지·해제 + 정지 즉시 반영 동작. 리뷰 후 진행.

---

# PHASE 3 — 모더레이션 (게시글 숨김 + 신고 통합)

### Task 10: GuidePost.hidden 컬럼 + 피드 제외 (DBA)

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/guide/GuidePost.java`
- Modify: `app/backend/src/main/java/com/guidematch/guide/GuidePostRepository.java`
- Modify: 공개 피드를 만드는 서비스(아래 Step 3에서 grep으로 특정)

**Interfaces:**
- Produces: `GuidePost.isHidden()`(null→false), `GuidePost.hide()`, `GuidePost.unhide()`; `GuidePostRepository.findByHiddenFalseOrderByCreatedAtDesc()`.

- [ ] **Step 1: GuidePost에 hidden 필드 추가**

`viewCount` 필드 블록(GuidePost.java ~40-41) 아래에 추가:

```java
    @Column(name = "hidden")
    private Boolean hidden;
```

`createdAt` getter 근처에 추가:

```java
    public boolean isHidden() { return hidden != null && hidden; }
    public void hide() { this.hidden = true; }
    public void unhide() { this.hidden = false; }
```

- [ ] **Step 2: 피드에서 숨김 제외 쿼리 추가**

GuidePostRepository에 추가:

```java
    // 공개 피드용 — 숨김(hidden=true) 제외. null은 노출(기존 행 안전).
    @Query("SELECT p FROM GuidePost p WHERE p.hidden IS NULL OR p.hidden = false ORDER BY p.createdAt DESC")
    List<GuidePost> findVisibleOrderByCreatedAtDesc();
```

- [ ] **Step 3: 공개 피드 서비스가 새 쿼리를 쓰도록 교체**

Run: `grep -rn "findAllByOrderByCreatedAtDesc" app/backend/src/main/java`
공개 피드(게시글 목록)를 반환하는 호출부를 `findVisibleOrderByCreatedAtDesc()`로 교체한다. (가이드 본인 게시글 관리 화면 `findAllByAuthor`는 그대로 — 본인은 숨김 글도 봐야 함.)
Expected: 공개 피드 호출부 1곳 교체. 교체 대상이 여러 곳이면 "일반 사용자에게 노출되는 피드"만 교체하고 관리/본인 화면은 유지.

- [ ] **Step 4: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 스키마/동작 확인**

`gradle bootRun` 후 `guide_posts.hidden` 컬럼 생성 확인. 한 글을 Supabase에서 `hidden=true`로 바꾸고 공개 피드 API 호출 → 그 글이 빠지는지 확인. 원복.

- [ ] **Step 6: Commit** (승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/guide/GuidePost.java app/backend/src/main/java/com/guidematch/guide/GuidePostRepository.java <교체한 서비스 파일>
git commit -m "feat(guide): add hidden flag and exclude hidden posts from public feed"
```

---

### Task 11: ModerationService + 게시글 모더레이션 API (backend)

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/admin/ModerationService.java`
- Create: `app/backend/src/main/java/com/guidematch/admin/AdminPostService.java`
- Create: `app/backend/src/main/java/com/guidematch/admin/AdminPostController.java`

**Interfaces:**
- Consumes: `GuidePostRepository`, `GuidePost.hide/unhide/isHidden`(Task 10).
- Produces:
  - `ModerationService.hidePost(Long postId)`, `unhidePost(Long postId)`, `deletePost(Long postId)` — 공유 조치 로직.
  - `GET /api/admin/posts?hidden=&page=&size=` → `PageResult<PostRow>`; `PostRow(Long id, Long authorUserId, String authorName, String content, String imageUrl, boolean hidden, Instant createdAt)`.
  - `POST /api/admin/posts/{id}/hide`, `/{id}/unhide`, `DELETE /api/admin/posts/{id}`.

- [ ] **Step 1: ModerationService 작성 (공유 조치 로직)**

```java
package com.guidematch.admin;

import com.guidematch.guide.GuidePost;
import com.guidematch.guide.GuidePostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글 모더레이션 공유 로직 — 브라우징 화면과 신고 검토 화면이 모두 호출한다. */
@Service
public class ModerationService {

    private final GuidePostRepository guidePostRepository;

    public ModerationService(GuidePostRepository guidePostRepository) {
        this.guidePostRepository = guidePostRepository;
    }

    @Transactional
    public void hidePost(Long postId) {
        GuidePost p = guidePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        p.hide();
        guidePostRepository.save(p);
    }

    @Transactional
    public void unhidePost(Long postId) {
        GuidePost p = guidePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        p.unhide();
        guidePostRepository.save(p);
    }

    @Transactional
    public void deletePost(Long postId) {
        if (!guidePostRepository.existsById(postId)) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }
        guidePostRepository.deleteById(postId);
    }
}
```

- [ ] **Step 2: 목록용 쿼리 추가 (GuidePostRepository)**

```java
    @Query("SELECT p FROM GuidePost p WHERE (:onlyHidden = false OR p.hidden = true) ORDER BY p.createdAt DESC")
    org.springframework.data.domain.Page<GuidePost> adminList(
            @org.springframework.data.repository.query.Param("onlyHidden") boolean onlyHidden,
            org.springframework.data.domain.Pageable pageable);
```

- [ ] **Step 3: AdminPostService 작성 (목록 + 작성자 이름 배치조회)**

```java
package com.guidematch.admin;

import com.guidematch.guide.GuidePost;
import com.guidematch.guide.GuidePostRepository;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 관리자 게시글 모더레이션 목록. 조치는 ModerationService로 위임. */
@Service
public class AdminPostService {

    private final GuidePostRepository guidePostRepository;
    private final UserRepository userRepository;

    public AdminPostService(GuidePostRepository guidePostRepository, UserRepository userRepository) {
        this.guidePostRepository = guidePostRepository;
        this.userRepository = userRepository;
    }

    public record PostRow(Long id, Long authorUserId, String authorName, String content,
                          String imageUrl, boolean hidden, Instant createdAt) {}

    @Transactional(readOnly = true)
    public AdminUserService.PageResult<PostRow> list(boolean onlyHidden, int page, int size) {
        Page<GuidePost> result = guidePostRepository.adminList(
                onlyHidden, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));

        List<Long> authorIds = result.getContent().stream()
                .map(GuidePost::getAuthorUserId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> names.put(u.getId(), u.getFullName()));

        List<PostRow> rows = result.getContent().stream().map(p -> new PostRow(
                p.getId(), p.getAuthorUserId(),
                p.getAuthorUserId() != null ? names.getOrDefault(p.getAuthorUserId(), "알 수 없음") : "알 수 없음",
                p.getContent(), p.getImageUrl(), p.isHidden(), p.getCreatedAt()
        )).toList();
        return new AdminUserService.PageResult<>(rows, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }
}
```

(참고: `GuidePost`에 `getAuthorUserId()`, `getContent()`, `getImageUrl()`, `getCreatedAt()` getter가 있어야 함 — 없으면 표준 getter 추가.)

- [ ] **Step 4: AdminPostController 작성**

```java
package com.guidematch.admin;

import org.springframework.web.bind.annotation.*;

/** 관리자 게시글 모더레이션 API. /api/admin/** → hasRole("ADMIN"). */
@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final AdminPostService adminPostService;
    private final ModerationService moderationService;

    public AdminPostController(AdminPostService adminPostService, ModerationService moderationService) {
        this.adminPostService = adminPostService;
        this.moderationService = moderationService;
    }

    @GetMapping
    public AdminUserService.PageResult<AdminPostService.PostRow> list(
            @RequestParam(defaultValue = "false") boolean hidden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminPostService.list(hidden, page, size);
    }

    @PostMapping("/{id}/hide")
    public void hide(@PathVariable Long id) { moderationService.hidePost(id); }

    @PostMapping("/{id}/unhide")
    public void unhide(@PathVariable Long id) { moderationService.unhidePost(id); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { moderationService.deletePost(id); }
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL` (getter 누락 에러 시 GuidePost에 표준 getter 추가 후 재빌드)

- [ ] **Step 6: 스모크 (ADMIN)**

`curl -s "localhost:8080/api/admin/posts?size=5" -H "Authorization: Bearer <ADMIN_JWT>"` → items. 한 글 `POST .../{id}/hide` → 공개 피드에서 제외 확인. `unhide` 원복.

- [ ] **Step 7: Commit** (승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/admin/ModerationService.java app/backend/src/main/java/com/guidematch/admin/AdminPost*.java app/backend/src/main/java/com/guidematch/guide/GuidePost*.java
git commit -m "feat(admin): post moderation (hide/unhide/delete) with shared service"
```

---

### Task 12: 신고 검토에 실제 조치 연결 (backend)

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/admin/AdminReportService.java`
- Modify: `app/backend/src/main/java/com/guidematch/admin/AdminReportController.java`

**Interfaces:**
- Consumes: `ModerationService.hidePost`(Task 11), `AdminUserService.suspend`(Task 8), `Report.getTargetType/getTargetId`.
- Produces: `AdminReportService.act(Long reportId, Long adminId, String action)` — action ∈ {HIDE_POST, SUSPEND_USER}; 대상 조치 후 report를 REVIEWED로 표시. `POST /api/admin/reports/{id}/act` body `{action, reason?}`.

- [ ] **Step 1: AdminReportService에 의존성 + act 메서드 추가**

생성자에 `ModerationService moderationService`, `AdminUserService adminUserService` 추가(필드·주입). 그리고:

```java
    /**
     * 신고 대상에 실제 조치를 취하고 신고를 REVIEWED로 닫는다.
     * action: "HIDE_POST"(대상이 POST), "SUSPEND_USER"(대상이 USER 또는 BOOKING의 가이드).
     */
    @Transactional
    public void act(Long reportId, Long adminId, String action, String reason) {
        Report r = getOpen(reportId);
        switch (action) {
            case "HIDE_POST" -> {
                if (!"POST".equals(r.getTargetType())) {
                    throw new IllegalArgumentException("이 신고 대상은 게시글이 아닙니다.");
                }
                moderationService.hidePost(r.getTargetId());
            }
            case "SUSPEND_USER" -> {
                Long userId = resolveTargetUserId(r);
                adminUserService.suspend(userId, adminId, reason);
            }
            default -> throw new IllegalArgumentException("알 수 없는 조치입니다.");
        }
        r.markReviewed();
    }

    /** 신고 대상에서 정지할 user id를 뽑는다. USER면 그대로, BOOKING이면 가이드 user. */
    private Long resolveTargetUserId(Report r) {
        if ("USER".equals(r.getTargetType())) return r.getTargetId();
        if ("BOOKING".equals(r.getTargetType())) {
            Booking b = bookingRepository.findById(r.getTargetId())
                    .orElseThrow(() -> new IllegalArgumentException("대상 예약을 찾을 수 없습니다."));
            GuideProfile p = guideProfileRepository.findById(b.getGuideProfileId())
                    .orElseThrow(() -> new IllegalArgumentException("대상 가이드를 찾을 수 없습니다."));
            return p.getUserId();
        }
        throw new IllegalArgumentException("이 신고 대상에는 회원 정지를 적용할 수 없습니다.");
    }
```

(`getOpen`은 기존 private 메서드 재사용. `Booking`, `GuideProfile` import는 이미 있음.)

- [ ] **Step 2: AdminReportController에 act 엔드포인트 추가**

```java
    /** 신고 대상 조치 (body: { "action": "HIDE_POST"|"SUSPEND_USER", "reason": "..." }). */
    @PostMapping("/{id}/act")
    public void act(@PathVariable Long id,
                    @org.springframework.security.core.annotation.AuthenticationPrincipal Long adminId,
                    @RequestBody java.util.Map<String, String> body) {
        adminReportService.act(id, adminId, body.get("action"), body.get("reason"));
    }
```

(import `RequestBody`, `PathVariable` 추가 필요 시 상단에 추가.)

- [ ] **Step 3: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 스모크 (ADMIN)**

POST 대상 신고 1건에 `curl -X POST localhost:8080/api/admin/reports/<id>/act -H "Authorization: Bearer <ADMIN_JWT>" -H 'Content-Type: application/json' -d '{"action":"HIDE_POST"}'` → 게시글 숨김 + 신고 OPEN 목록에서 사라짐(REVIEWED). USER/BOOKING 대상에 `SUSPEND_USER`로 정지 확인.

- [ ] **Step 5: Commit** (승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/admin/AdminReport*.java
git commit -m "feat(admin): act on report target (hide post / suspend user)"
```

---

### Task 13: 모더레이션 페이지 + 신고 조치 버튼 (frontend)

**Files:**
- Create: `app/frontend/src/app/admin/posts/page.tsx`
- Modify: `app/frontend/src/app/admin/reports/page.tsx` (조치 버튼 연결)
- Modify: `app/frontend/src/lib/i18n.ts` (admin 모더레이션 라벨 — 3언어)

**Interfaces:**
- Consumes: `GET /api/admin/posts`, `POST /api/admin/posts/{id}/hide|unhide`, `DELETE /api/admin/posts/{id}`(Task 11), `POST /api/admin/reports/{id}/act`(Task 12).

- [ ] **Step 1: i18n admin에 모더레이션 라벨 추가 (ko/en/zh)**

ko: `postsAll: "전체 글", postsHidden: "숨김만", colAuthor: "작성자", colContent: "내용", hide: "숨김", unhide: "숨김해제", del: "삭제", confirmHide: "이 글을 숨길까요?", confirmDelete: "이 글을 삭제할까요? 되돌릴 수 없습니다.", hidden: "숨김됨", actHidePost: "게시글 숨김", actSuspendUser: "작성자/대상 정지",`
en: `postsAll: "All posts", postsHidden: "Hidden only", colAuthor: "Author", colContent: "Content", hide: "Hide", unhide: "Unhide", del: "Delete", confirmHide: "Hide this post?", confirmDelete: "Delete this post? This cannot be undone.", hidden: "Hidden", actHidePost: "Hide post", actSuspendUser: "Suspend target",`
zh: `postsAll: "全部帖子", postsHidden: "仅隐藏", colAuthor: "作者", colContent: "内容", hide: "隐藏", unhide: "取消隐藏", del: "删除", confirmHide: "隐藏该帖?", confirmDelete: "删除该帖?不可撤销。", hidden: "已隐藏", actHidePost: "隐藏帖子", actSuspendUser: "停用对象",`

- [ ] **Step 2: 모더레이션 페이지 작성**

```tsx
"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type PostRow = {
  id: number; authorUserId: number | null; authorName: string;
  content: string; imageUrl: string | null; hidden: boolean; createdAt: string;
};
type PageResult = { items: PostRow[]; page: number; totalPages: number; totalItems: number };

export default function AdminPostsPage() {
  const { t } = useLanguage();
  const a = t.admin;
  const [onlyHidden, setOnlyHidden] = useState(false);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResult | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      const qs = new URLSearchParams({ hidden: String(onlyHidden), page: String(page), size: "20" });
      setData(await api<PageResult>(`/api/admin/posts?${qs}`, { auth: true }));
    } catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
  }, [onlyHidden, page, t.common.error]);

  useEffect(() => { load(); }, [load]);

  async function act(p: PostRow, kind: "hide" | "unhide" | "delete") {
    if (kind === "hide" && !confirm(a.confirmHide)) return;
    if (kind === "delete" && !confirm(a.confirmDelete)) return;
    setBusy(p.id);
    try {
      if (kind === "delete") await api(`/api/admin/posts/${p.id}`, { method: "DELETE", auth: true });
      else await api(`/api/admin/posts/${p.id}/${kind}`, { method: "POST", auth: true });
      await load();
    } catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setBusy(null); }
  }

  return (
    <div className="mx-auto max-w-4xl">
      <label className="mb-4 flex items-center gap-2 text-sm">
        <input type="checkbox" checked={onlyHidden} onChange={(e) => { setPage(0); setOnlyHidden(e.target.checked); }} />
        {a.postsHidden}
      </label>
      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <div className="flex flex-col gap-3">
        {data?.items.map((p) => (
          <div key={p.id} className="card flex items-start justify-between gap-4 p-4">
            <div className="min-w-0">
              <div className="text-xs text-stone-400">{p.authorName}{p.hidden && <span className="ml-2 text-red-500">· {a.hidden}</span>}</div>
              <p className="mt-1 line-clamp-2 text-sm text-stone-800">{p.content}</p>
            </div>
            <div className="flex shrink-0 gap-2">
              {p.hidden
                ? <button disabled={busy === p.id} onClick={() => act(p, "unhide")} className="rounded-lg bg-stone-100 px-3 py-1 text-sm">{a.unhide}</button>
                : <button disabled={busy === p.id} onClick={() => act(p, "hide")} className="rounded-lg bg-amber-100 px-3 py-1 text-sm text-amber-700">{a.hide}</button>}
              <button disabled={busy === p.id} onClick={() => act(p, "delete")} className="rounded-lg bg-red-100 px-3 py-1 text-sm text-red-700">{a.del}</button>
            </div>
          </div>
        ))}
        {data && data.items.length === 0 && <p className="p-6 text-center text-stone-400">{a.empty}</p>}
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3 text-sm">
          <button disabled={page === 0} onClick={() => setPage(page - 1)} className="rounded-lg px-3 py-1 disabled:opacity-40">{a.prev}</button>
          <span>{page + 1} / {data.totalPages}</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} className="rounded-lg px-3 py-1 disabled:opacity-40">{a.next}</button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: 신고 페이지에 조치 버튼 연결**

`app/admin/reports/page.tsx`를 열어, 각 신고 항목의 액션 영역에 조치 버튼을 추가한다. 기존 review/dismiss 호출부 옆에 다음 핸들러/버튼을 넣는다(항목 타입 필드명은 기존 파일에 맞춰 `targetType` 사용):

```tsx
  async function act(reportId: number, action: "HIDE_POST" | "SUSPEND_USER") {
    try {
      await api(`/api/admin/reports/${reportId}/act`, { method: "POST", body: { action }, auth: true });
      await load(); // 기존 목록 리로드 함수명에 맞춤
    } catch (e) { /* 기존 에러 처리 패턴 사용 */ }
  }
```

버튼(항목의 `targetType`에 따라 노출):
```tsx
  {item.targetType === "POST" && (
    <button onClick={() => act(item.id, "HIDE_POST")} className="rounded-lg bg-amber-100 px-3 py-1 text-sm text-amber-700">{t.admin.actHidePost}</button>
  )}
  {(item.targetType === "USER" || item.targetType === "BOOKING") && (
    <button onClick={() => act(item.id, "SUSPEND_USER")} className="rounded-lg bg-red-100 px-3 py-1 text-sm text-red-700">{t.admin.actSuspendUser}</button>
  )}
```

- [ ] **Step 4: 타입 확인**

Run: `cd app/frontend && npx tsc --noEmit`
Expected: 에러 없음. (reports 페이지의 항목 타입에 `targetType`이 없으면 타입 정의에 추가.)

- [ ] **Step 5: 수동 확인**

`/admin/posts` 목록·숨김/해제/삭제·숨김필터. `/admin/reports`에서 POST 신고 → "게시글 숨김" 버튼으로 숨김+검토완료, USER/BOOKING 신고 → "정지" 버튼으로 대상 정지+검토완료.

- [ ] **Step 6: Commit** (승인 시)

```bash
git add app/frontend/src/app/admin/posts/page.tsx app/frontend/src/app/admin/reports/page.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(admin): moderation page and report action buttons"
```

**✅ PHASE 3 체크포인트** — 게시글 모더레이션 + 신고→조치 루프 완결. 리뷰 후 진행.

---

# PHASE 4 — 예약/거래 조회

### Task 14: 예약 조회 + 강제 취소 API (backend)

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/booking/BookingRepository.java`
- Create: `app/backend/src/main/java/com/guidematch/admin/AdminBookingService.java`
- Create: `app/backend/src/main/java/com/guidematch/admin/AdminBookingController.java`

**Interfaces:**
- Consumes: `Booking`, `BookingStatus`, `Booking.cancel()`.
- Produces:
  - `GET /api/admin/bookings?status=&page=&size=` → `PageResult<BookingRow>`; `BookingRow(Long id, Long travelerId, String travelerName, Long guideProfileId, String guideName, String status, Integer totalPrice, String currency, Instant startAt, Instant createdAt)`.
  - `POST /api/admin/bookings/{id}/cancel`.

- [ ] **Step 1: BookingRepository에 페이지 메서드 추가**

`import org.springframework.data.domain.Page;` `import org.springframework.data.domain.Pageable;` 추가 후:

```java
    Page<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);
    Page<Booking> findAllByOrderByCreatedAtDesc(Pageable pageable);
```

- [ ] **Step 2: AdminBookingService 작성**

```java
package com.guidematch.admin;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 관리자 예약 조회 + 강제 취소. 가격은 스냅샷 표시만(재계산 금지). */
@Service
public class AdminBookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final GuideProfileRepository guideProfileRepository;

    public AdminBookingService(BookingRepository bookingRepository, UserRepository userRepository,
                               GuideProfileRepository guideProfileRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.guideProfileRepository = guideProfileRepository;
    }

    public record BookingRow(Long id, Long travelerId, String travelerName,
                             Long guideProfileId, String guideName, String status,
                             Integer totalPrice, String currency, Instant startAt, Instant createdAt) {}

    @Transactional(readOnly = true)
    public AdminUserService.PageResult<BookingRow> list(String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Booking> result = (status == null || status.isBlank())
                ? bookingRepository.findAllByOrderByCreatedAtDesc(pr)
                : bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.valueOf(status), pr);

        // 이름 배치 조회(N+1 방지): 여행자 user + 가이드 프로필→user
        List<Long> profileIds = result.getContent().stream().map(Booking::getGuideProfileId).distinct().toList();
        Map<Long, GuideProfile> profiles = new HashMap<>();
        guideProfileRepository.findAllById(profileIds).forEach(p -> profiles.put(p.getId(), p));
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        result.getContent().forEach(b -> userIds.add(b.getTravelerId()));
        profiles.values().forEach(p -> userIds.add(p.getUserId()));
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getFullName()));

        List<BookingRow> rows = result.getContent().stream().map(b -> {
            GuideProfile p = profiles.get(b.getGuideProfileId());
            String guideName = p != null ? names.getOrDefault(p.getUserId(), "알 수 없음") : "알 수 없음";
            return new BookingRow(
                    b.getId(), b.getTravelerId(), names.getOrDefault(b.getTravelerId(), "알 수 없음"),
                    b.getGuideProfileId(), guideName, b.getStatus().name(),
                    b.getTotalPrice(), b.getCurrency(), b.getStartAt(), b.getCreatedAt());
        }).toList();
        return new AdminUserService.PageResult<>(rows, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    /** 강제 취소 — REQUESTED/ACCEPTED만 취소 가능(Booking.cancel의 상태 가드 재사용). */
    @Transactional
    public void cancel(Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));
        b.cancel();
        bookingRepository.save(b);
    }
}
```

(`Booking`에 `getTravelerId/getGuideProfileId/getStatus/getTotalPrice/getCurrency/getStartAt/getCreatedAt` getter가 있어야 함 — 없으면 표준 getter 추가.)

- [ ] **Step 3: AdminBookingController 작성**

```java
package com.guidematch.admin;

import org.springframework.web.bind.annotation.*;

/** 관리자 예약 조회 API. /api/admin/** → hasRole("ADMIN"). */
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    public AdminBookingController(AdminBookingService adminBookingService) {
        this.adminBookingService = adminBookingService;
    }

    @GetMapping
    public AdminUserService.PageResult<AdminBookingService.BookingRow> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminBookingService.list(status, page, size);
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id) { adminBookingService.cancel(id); }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 스모크 (ADMIN)**

`curl -s "localhost:8080/api/admin/bookings?size=5" -H "Authorization: Bearer <ADMIN_JWT>"` → rows. `status=REQUESTED` 필터. REQUESTED/ACCEPTED 예약 `POST .../{id}/cancel` → CANCELLED. 이미 COMPLETED면 400.

- [ ] **Step 6: Commit** (승인 시)

```bash
git add app/backend/src/main/java/com/guidematch/booking/BookingRepository.java app/backend/src/main/java/com/guidematch/admin/AdminBooking*.java
git commit -m "feat(admin): booking browse and force-cancel"
```

---

### Task 15: 예약 조회 페이지 (frontend)

**Files:**
- Create: `app/frontend/src/app/admin/bookings/page.tsx`
- Modify: `app/frontend/src/lib/i18n.ts` (admin 예약 라벨 — 3언어)

**Interfaces:**
- Consumes: `GET /api/admin/bookings`, `POST /api/admin/bookings/{id}/cancel`(Task 14).

- [ ] **Step 1: i18n admin에 예약 라벨 추가 (ko/en/zh)**

ko: `bkAll: "전체", bkRequested: "요청", bkAccepted: "확정", bkCompleted: "완료", bkCancelled: "취소", bkRejected: "거절", colTraveler: "여행자", colGuide: "가이드", colPrice: "금액", colStart: "일정", colCancel: "취소", confirmCancel: "이 예약을 강제 취소할까요?",`
en: `bkAll: "All", bkRequested: "Requested", bkAccepted: "Accepted", bkCompleted: "Completed", bkCancelled: "Cancelled", bkRejected: "Rejected", colTraveler: "Traveler", colGuide: "Guide", colPrice: "Price", colStart: "Schedule", colCancel: "Cancel", confirmCancel: "Force-cancel this booking?",`
zh: `bkAll: "全部", bkRequested: "请求", bkAccepted: "已确认", bkCompleted: "已完成", bkCancelled: "已取消", bkRejected: "已拒绝", colTraveler: "旅行者", colGuide: "向导", colPrice: "金额", colStart: "行程", colCancel: "取消", confirmCancel: "强制取消该预约?",`

- [ ] **Step 2: 예약 조회 페이지 작성**

```tsx
"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

type BookingRow = {
  id: number; travelerId: number; travelerName: string;
  guideProfileId: number; guideName: string; status: string;
  totalPrice: number | null; currency: string | null; startAt: string; createdAt: string;
};
type PageResult = { items: BookingRow[]; page: number; totalPages: number; totalItems: number };

export default function AdminBookingsPage() {
  const { t } = useLanguage();
  const a = t.admin;
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResult | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      const qs = new URLSearchParams({ page: String(page), size: "20" });
      if (status) qs.set("status", status);
      setData(await api<PageResult>(`/api/admin/bookings?${qs}`, { auth: true }));
    } catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
  }, [status, page, t.common.error]);

  useEffect(() => { load(); }, [load]);

  async function onCancel(b: BookingRow) {
    if (!confirm(a.confirmCancel)) return;
    setBusy(b.id);
    try { await api(`/api/admin/bookings/${b.id}/cancel`, { method: "POST", auth: true }); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setBusy(null); }
  }

  const cancellable = (s: string) => s === "REQUESTED" || s === "ACCEPTED";

  return (
    <div className="mx-auto max-w-5xl">
      <select className="input mb-4 max-w-[10rem]" value={status}
        onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
        <option value="">{a.bkAll}</option>
        <option value="REQUESTED">{a.bkRequested}</option>
        <option value="ACCEPTED">{a.bkAccepted}</option>
        <option value="COMPLETED">{a.bkCompleted}</option>
        <option value="CANCELLED">{a.bkCancelled}</option>
        <option value="REJECTED">{a.bkRejected}</option>
      </select>
      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-stone-200 text-left text-stone-500">
            <tr>
              <th className="p-3">{a.colTraveler}</th><th className="p-3">{a.colGuide}</th>
              <th className="p-3">{a.colStatus}</th><th className="p-3">{a.colPrice}</th>
              <th className="p-3">{a.colCancel}</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((b) => (
              <tr key={b.id} className="border-b border-stone-100">
                <td className="p-3">{b.travelerName}</td>
                <td className="p-3">{b.guideName}</td>
                <td className="p-3">{b.status}</td>
                <td className="p-3">{b.totalPrice != null ? `${b.totalPrice} ${b.currency ?? ""}` : "—"}</td>
                <td className="p-3">
                  {cancellable(b.status)
                    ? <button disabled={busy === b.id} onClick={() => onCancel(b)} className="rounded-lg bg-red-100 px-3 py-1 font-medium text-red-700">{a.colCancel}</button>
                    : <span className="text-stone-400">—</span>}
                </td>
              </tr>
            ))}
            {data && data.items.length === 0 && (
              <tr><td colSpan={5} className="p-6 text-center text-stone-400">{a.empty}</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3 text-sm">
          <button disabled={page === 0} onClick={() => setPage(page - 1)} className="rounded-lg px-3 py-1 disabled:opacity-40">{a.prev}</button>
          <span>{page + 1} / {data.totalPages}</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} className="rounded-lg px-3 py-1 disabled:opacity-40">{a.next}</button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: 타입 확인**

Run: `cd app/frontend && npx tsc --noEmit`
Expected: 에러 없음.

- [ ] **Step 4: 수동 확인**

`/admin/bookings` 목록·상태필터·페이징. REQUESTED/ACCEPTED 강제취소 → CANCELLED. 그 외 상태는 취소 버튼 없음.

- [ ] **Step 5: Commit** (승인 시)

```bash
git add app/frontend/src/app/admin/bookings/page.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(admin): booking browse page with force-cancel"
```

**✅ PHASE 4 체크포인트** — 예약 조회/취소 동작. 리뷰 후 진행.

---

# PHASE 5 — 마감

### Task 16: 기존 페이지 셸 통합 + i18n 완결 + 회귀 확인

**Files:**
- Verify: `app/frontend/src/app/admin/verifications/page.tsx`, `app/frontend/src/app/admin/reports/page.tsx` (layout 하위에서 정상 렌더)
- Modify (필요 시): `app/frontend/src/lib/i18n.ts` (누락 키 3언어 채움)

**Interfaces:** 없음(정리·검증 단계).

- [ ] **Step 1: 기존 admin 페이지가 새 셸과 충돌 없는지 확인**

`/admin/verifications`, `/admin/reports`는 `app/admin/layout.tsx` 아래 자동 편입된다. 두 페이지가 자체 `denied`/`router.replace("/login")` 로직을 갖고 있다면(verifications는 있음), 이제 layout 게이트가 인증을 처리하므로 **중복 리다이렉트가 문제되지 않는지** 확인. layout이 비관리자에게 children을 아예 렌더하지 않으므로 페이지의 `getToken()` 체크는 항상 통과 상태 — 그대로 두어도 무방. 동작만 확인.

- [ ] **Step 2: i18n 3언어 완결성 점검**

Run: `cd app/frontend && npx tsc --noEmit`
그리고 `admin` 네임스페이스의 키가 ko/en/zh 세 객체에 **모두 동일하게** 존재하는지 육안 확인(누락 키는 TS가 잡아주지만, 한쪽에만 있으면 런타임 undefined). 누락 키 채움.

- [ ] **Step 3: 전체 회귀 체크리스트 (수동, 서버+프론트 실행)**

- [ ] 비로그인 `/admin` → 전용 로그인 폼
- [ ] USER 계정 전용 로그인 → "관리자 권한 없음" 에러, 진입 불가
- [ ] ADMIN 로그인 → 6개 탭 이동 모두 정상
- [ ] 메인 앱(`/`, Sidebar) 어디에도 admin 링크 없음
- [ ] 회원 정지 → 그 계정 즉시 로그인/요청 차단 → 해제 시 복구
- [ ] 게시글 숨김 → 공개 피드에서 제외 / 신고→조치 루프
- [ ] 예약 강제취소(REQUESTED/ACCEPTED만)
- [ ] ko/en/zh 전환 시 admin UI 깨짐 없음
- [ ] 로그아웃 → 다시 전용 로그인 화면

- [ ] **Step 4: Commit** (승인 시)

```bash
git add app/frontend/src/lib/i18n.ts
git commit -m "chore(admin): finalize i18n and portal shell integration"
```

**✅ PHASE 5 체크포인트 = 완료.**

---

## 비범위 (YAGNI)

- ADMIN 승격 UI / 계정 완전삭제 UI (DB 전용 유지).
- 감사 로그(audit trail).
- 정지 status 캐시(우선 매요청 PK 조회, 성능 이슈 시 후속).
- 자동화 테스트 하네스 도입.

## Self-Review 결과 (작성자 점검)

- **Spec 커버리지**: 진입(전용 로그인 게이트)=Task 4 / 대시보드=Task 3·5 / 회원관리+정지 즉시반영=Task 6·7·8·9 / 모더레이션+신고통합=Task 10·11·12·13 / 예약조회=Task 14·15 / i18n·마감=Task 16. 스펙 전 항목 대응됨.
- **타입 일관성**: `AdminUserService.PageResult<T>`를 users/posts/bookings 세 서비스가 공유(중복 정의 없음). `UserStatus`/`BookingStatus`/`VerificationStatus` 값 표기 일치. `isAdmin()`/`saveRole()`/`getRole()` 이름 프론트 전반 일치.
- **플레이스홀더**: 없음(모든 코드 스텝에 실제 코드 포함). 단, GuidePost/Booking 표준 getter는 "없으면 추가"로 명시(기존 엔티티에 대체로 존재).
- **잠재 위험**: (1) 기존 행 `status=NULL`의 ACTIVE 필터 포함 — Task 8 Step 1에서 명시 처리. (2) 공개 피드 호출부 특정 — Task 10 Step 3에서 grep으로 확정. (3) 정지 필터의 매요청 PK 조회 비용 — 설계 승인된 트레이드오프.
