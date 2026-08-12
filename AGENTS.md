# Kyum Platform — Codex Project Guide

## What This App Is
A C2C local guide matching platform for foreign travelers in Korea. Travelers browse and book local Korean guides for personalized tours. Guides set their own profiles, rates, and availability.

**Status**: MVP in active development. Core + social + location + translation features complete. Next priorities: real-time chat, payment.

---

## Multi-Agent Roles

| Agent | Trigger | Scope |
|-------|---------|-------|
| `developer` | New backend feature, API endpoint, business logic, Spring Boot bug | `app/backend/` |
| `designer` | UI change, new page, Tailwind styling, UX flow, frontend bug | `app/frontend/` |
| `dba` | Schema change, new entity, new column, query optimization, Supabase config | JPA entities, `application.yml` |
| `marketing_lead` | Positioning, ICP, GTM plan, marketing roadmap, specialist coordination | Strategy, research, briefs |
| `growth_marketer` | Acquisition, conversion, experiments, attribution, analytics, paid-growth plan | Funnel, measurement, campaign proposals |
| `content_seo_marketer` | Content strategy, SEO, AI-search, site copy, organic growth | Content/SEO briefs and copy |
| `lifecycle_community_marketer` | Email/SMS lifecycle, onboarding, retention, social, community, partnerships | CRM/community briefs and copy |

**Coordinator responsibilities**: understand intent → route marketing strategy to `marketing_lead` → assign specialist marketing work → sequence DBA → developer → designer → review output. Marketing agents must not publish, spend budget, contact external parties, or modify production tracking without explicit user approval.

### Marketing documentation
- The user has approved recording finalized marketing strategies, campaign briefs, execution plans, research summaries, decisions, and metric reviews in the authenticated Notion MCP workspace.
- Use the `notion` MCP server to create or update the `Kyum Marketing Log` parent page (create it if absent). Create one dated child page per deliverable with: objective, audience/market, recommendation, execution plan, metrics, assumptions/risks, decisions needed, and sources.
- Never write credentials, access tokens, personal data, or unapproved external-contact details to Notion. If a write changes or archives an existing decision, state the change clearly in the page.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 15, React 19, TypeScript, Tailwind CSS 3, @dnd-kit (드래그 일정 빌더) |
| Backend | Spring Boot 3.3.5, Java 21, Spring Security, JWT |
| Database | PostgreSQL via Supabase, JPA `ddl-auto: update` |
| File Storage | Supabase Storage |
| Maps/Location | Kakao Map REST API (reverse geocoding + place search) — `KAKAO_REST_API_KEY` in `app/backend/.env` |
| Translation | Google Cloud Translation API v2 — `GOOGLE_TRANSLATE_API_KEY` in `app/backend/.env` |
| Auth | JWT (24h expiry), BCrypt passwords, stored in localStorage |

**Ports**: Frontend → 3000, Backend → 8080

---

## Project Structure

```
kyum_platform/
├── .Codex/agents/
│   ├── developer.md
│   ├── designer.md
│   └── dba.md
├── app/
│   ├── frontend/src/
│   │   ├── app/
│   │   │   ├── page.tsx                      # Landing — 흰 배경, feature tiles, 한국 명소 갤러리, CTA
│   │   │   ├── explore/page.tsx              # 장소 탐색 (도시+구 선택 → 카테고리 탭 → 장소 카드)
│   │   │   ├── trips/page.tsx                # 여행 일정 목록 + 생성
│   │   │   ├── trips/[id]/page.tsx           # 일정 빌더 (일차 탭, ▲▼ 정렬, 장소 담기, TripMap)
│   │   │   ├── profile/page.tsx              # User profile (avatar, stats, edit/logout)
│   │   │   ├── guides/
│   │   │   │   ├── page.tsx                  # 가이드 목록 + 게시글 피드 (탭 전환)
│   │   │   │   └── [id]/page.tsx             # Guide detail + slot booking
│   │   │   ├── guide/
│   │   │   │   ├── page.tsx                  # Guide dashboard
│   │   │   │   ├── manage/page.tsx           # Guide settings (slots, interests)
│   │   │   │   └── posts/page.tsx            # Guide's own posts (compose + delete)
│   │   │   ├── traveler/
│   │   │   │   ├── page.tsx                  # Traveler home
│   │   │   │   ├── profile/page.tsx          # Traveler MBTI + interests
│   │   │   │   ├── bookings/page.tsx         # 내 예약 목록
│   │   │   │   └── following/page.tsx        # 팔로잉 목록
│   │   │   ├── become-guide/page.tsx
│   │   │   └── review/[bookingId]/page.tsx
│   │   ├── components/
│   │   │   ├── Sidebar.tsx                   # ★ 메인 내비 — 데스크탑 left rail + 모바일 top/bottom bar
│   │   │   ├── CitySelect.tsx                # 도시 드롭다운 + GPS 버튼; loadCities() 캐시 공유
│   │   │   ├── DistrictSelect.tsx            # 구(district) 드롭다운 — 도시에 구 있을 때만 렌더
│   │   │   ├── TripMap.tsx                   # 카카오 지도 — 일차 장소 핀 + 폴리라인
│   │   │   ├── LanguagePicker.tsx            # 첫 방문 언어 선택 모달
│   │   │   └── InterestPicker.tsx            # 관심사 선택 (30개 6카테고리)
│   │   ├── context/
│   │   │   └── LanguageContext.tsx           # lang, setLang, t, showPicker, dismissPicker
│   │   └── lib/
│   │       ├── i18n.ts                       # ko/en/zh 번역 키 (모든 키 3개 언어 필수)
│   │       ├── interests.ts                  # InterestKey type + INTEREST_CATEGORIES
│   │       ├── api.ts                        # fetch wrapper, getToken, saveUserName/getUserName
│   │       └── mode.ts                       # getMode / clearMode (localStorage)
│   └── backend/src/main/java/com/guidematch/
│       ├── auth/         # JWT, User entity, signup/login
│       ├── guide/        # GuideProfile, GuidePost, slots, follow, avgRating+reviewCount
│       ├── booking/      # Booking entity + flow
│       ├── chat/         # ChatMessage (WebSocket + STOMP)
│       ├── itinerary/    # Itinerary + ItineraryItem (owner-scoped CRUD)
│       ├── review/       # Review entity + ReviewController/Service/Repository
│       ├── geo/          # ★ KoreanCity, CityController, PlaceController, GeoController
│       │                 #   KakaoLocalClient, GoogleTranslateClient
│       │                 #   TranslationCache, TranslationCacheRepository, TranslationService
│       └── storage/      # SupabaseStorageClient
└── AGENTS.md
```

---

## Feature Roadmap

### Done
- [x] User auth (signup, login, JWT)
- [x] Guide profile creation (languages, credentials, avatar upload)
- [x] Guide search & browse (city + near-me distance sort)
- [x] Booking flow (request → accept/reject/cancel)
- [x] 1:1 chat per booking (WebSocket/STOMP)
- [x] Traveler/guide mode switching
- [x] **Multi-language (ko/en/zh)** — Sidebar 언어 전환 (lang 선택 → localStorage 저장)
- [x] **Instagram-style posts** — full-width square images, PostCard
- [x] **Post likes & comments** — ❤️ like toggle, 💬 comment thread per post
- [x] **Available slots** — guides add date+time slots; travelers pick slots when booking
- [x] **MBTI + interests** — guide(`guide_profiles`) / traveler(`users`) 분리 저장
- [x] **Reviews & ratings** — 1-5 stars + comment; avgRating + reviewCount on GuideProfile
- [x] **Profile page** — `/profile` avatar, stats, edit, logout
- [x] **Guide posts manager** — `/guide/posts` compose + delete
- [x] **Guide list** — horizontal cards, client-side language filter + sort (인기/예약/평점)
- [x] **Post feed sort/filter** — guide-language filter + sort (추천/인기/조회/최신); viewCount via IntersectionObserver
- [x] **Booking-availability toggle** — 예약 받는 중 / 예약 중단 (guide/manage + profile)

### ★ Location (Phases 1–3, 완료)
- [x] **Phase 1** — KoreanCity list, GPS reverse geocode (`GET /api/geo/reverse`), CitySelect, guide search by city
- [x] **Phase 2** — `GET /api/places?city=&category=&district=&lang=` — Kakao place search (AT4/FD6/CE7/CT1/전통시장), radius 20km (구 선택 시 6km)
- [x] **Phase 3** — `/trips` 일정 빌더 + `/trips/[id]` TripMap (카카오 지도 핀 + 폴리라인)
- [x] **District drill-down** — 도시 → 구 세부 선택 (`DistrictSelect`), 10개 도시 전 구 목록, `/explore` + `/trips/[id]` 적용
- [x] **Google Translate** — 장소명/카테고리 번역 (`TranslationService` 캐시-우선, Google Cloud Translation API v2), `?lang=` 파라미터

### Next
- [ ] **Real-time chat** (WebSocket or Supabase Realtime)
- [ ] **Payment integration**
- [ ] Signed URLs for credential files
- [ ] JWT in httpOnly cookies
- [ ] Production DB migrations (Flyway or Liquibase)

---

## Key Architecture Patterns

### Navigation (Sidebar)
- `Sidebar.tsx` — `"use client"`, `useEffect`로 localStorage에서 token/mode/userName 읽음
- 역할별 메뉴: guest(홈/가이드찾기/탐색/여행일정) / traveler(+여행자홈/내예약/팔로잉) / guide(+가이드홈/예약요청/내게시글/프로필관리)
- 모바일 하단 탭 5개 고정; 데스크탑 `w-64 fixed left rail`
- **Navbar.tsx 삭제됨** — Sidebar로 완전 교체. `layout.tsx`에 `<div class="md:pl-64">` 오프셋.
- `globals.css` `.page` → `pt-20 pb-24 md:pt-10 md:pb-16` (모바일 top/bottom bar 공간)

### Google Translate (장소명 번역)
- `GoogleTranslateClient` — Cloud Translation API v2 REST (`format=text`, `zh→zh-CN`)
- `TranslationCache` entity — DB 캐시 `(sourceText, targetLang)` unique constraint
- `TranslationService` — 캐시 우선 → 미스만 Google 배치 호출 → 결과 저장 → 원문 폴백
- `PlaceController` — `?lang=` 파라미터. ko이면 번역 스킵. address는 항상 한국어 유지 (택시/지도 앱 편의)
- 프론트: `/explore`, `/trips/[id]` 모두 `&lang=${lang}` 전달
- **비용 거의 0**: 같은 장소명은 캐시에서 반환, 무료 쿼터 500,000자/월

### District (구) 선택
- `KoreanCity.DISTRICTS` map — 10개 도시 × 구 목록 (ko/en/zh record)
- `CityController` — `CityDto`에 `List<District>` 포함해서 `/api/cities`에서 함께 반환
- `DistrictSelect.tsx` — city prop 바뀌면 해당 도시의 districts 렌더. 라벨=현재 lang, 값=ko (Kakao 지오코딩 기준)
- `PlaceController` — district 파라미터 유효성 검증 → Kakao address.json 지오코딩 → 6km radius

### Auth
- `@AuthenticationPrincipal Long userId` — public 엔드포인트에서 null 반환 (절대 401 던지지 말 것)
- Security public routes: `SecurityConfig.java`에 반드시 등록

### i18n
- `src/lib/i18n.ts` — 단일 파일, ko/en/zh 3개 객체, **모든 키 3개 언어 필수**
- `Lang` type: `"ko" | "en" | "zh"`
- 동적 데이터(장소명 등)는 i18n에 넣지 않고 Google Translate API로 처리

### Itinerary
- `Itinerary` + `ItineraryItem` (orphanRemoval=true) — PUT 교체 시 반드시 `clear()+addAll()` 인-플레이스
- `saveAndFlush` 필수 — 새 item id가 응답에 포함되어야 함

### Kakao API 주의사항
- REST key `.env` 값에 **따옴표 없이** raw 값만 (따옴표 포함 시 401 `wrong appKey format`)
- 카카오 콘솔에서 "카카오맵/로컬(OPEN_MAP_AND_LOCAL)" 서비스 ON 필수
- JS SDK key는 REST key와 **다른 값** (`NEXT_PUBLIC_KAKAO_JS_KEY` in `app/frontend/.env.local`)
- JS SDK: Kakao 콘솔 JS 키 → "JavaScript SDK 도메인"에 `http://localhost:3000` 등록 필수

---

## Key Constraints

- **No new npm packages** without explicit approval — keep frontend bundle lean
- **No architectural changes** (microservices, new frameworks) without coordinator decision
- **Never drop DB columns/tables** without user confirmation
- **Never commit `.env`** — only `.env.example`
- **Price is snapshotted at booking time** — `hourly_rate_snapshot` must never be derived live
- **File uploads** go to Supabase Storage only — never local disk
- **Address는 항상 한국어 유지** — 번역 대상에서 제외 (택시/지도 앱 사용 편의)

---

## Running Locally

```bash
# Backend (Java 21 필수 — 기본 java가 다른 버전이면 아래 명령 사용)
cd app/backend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
gradle bootRun      # :8080 — "80% EXECUTING"에서 멈추면 정상 (서버 실행 중)

# Frontend
cd app/frontend
npm run dev         # :3000
```

**ENV 파일 세팅:**
- `app/backend/.env` ← `.env.example` 복사 후 값 채우기
  - `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `JWT_SECRET`
  - `SUPABASE_URL`, `SUPABASE_SERVICE_KEY`
  - `KAKAO_REST_API_KEY` (선택, 없으면 장소검색/GPS 비활성)
  - `GOOGLE_TRANSLATE_API_KEY` (선택, 없으면 장소명 한국어 그대로 표시)
- `app/frontend/.env.local`
  - `NEXT_PUBLIC_KAKAO_JS_KEY` (선택, 없으면 지도 amber 안내로 degrade)
