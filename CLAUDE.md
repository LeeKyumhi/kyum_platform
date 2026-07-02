# Kyum Platform — Claude Code Project Guide

## What This App Is
A C2C local guide matching platform for foreign travelers in Korea. Travelers browse and book local Korean guides for personalized tours. Guides set their own profiles, rates, and availability.

**Status**: MVP in active development. Core + social features complete. Next priorities: reviews/ratings, real-time chat, payment.

---

## Multi-Agent Roles

| Agent | Trigger | Scope |
|-------|---------|-------|
| `developer` | New backend feature, API endpoint, business logic, Spring Boot bug | `app/backend/` |
| `designer` | UI change, new page, Tailwind styling, UX flow, frontend bug | `app/frontend/` |
| `dba` | Schema change, new entity, new column, query optimization, Supabase config | JPA entities, `application.yml` |

**Coordinator responsibilities**: understand intent → sequence DBA → developer → designer → review output.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 15, React 19, TypeScript, Tailwind CSS 3 |
| Backend | Spring Boot 3.3.5, Java 21, Spring Security, JWT |
| Database | PostgreSQL via Supabase, JPA `ddl-auto: update` |
| File Storage | Supabase Storage |
| Maps/Location | Kakao Map REST API (reverse geocoding; Phase 2 place search) — key `KAKAO_REST_API_KEY` in `.env`, optional |
| Auth | JWT (24h expiry), BCrypt passwords, stored in localStorage |

**Ports**: Frontend → 3000, Backend → 8080

---

## Project Structure

```
kyum_platform/
├── .claude/agents/
│   ├── developer.md
│   ├── designer.md
│   └── dba.md
├── app/
│   ├── frontend/src/
│   │   ├── app/
│   │   │   ├── page.tsx                      # Landing (login-aware CTA)
│   │   │   ├── profile/page.tsx              # User profile (avatar, stats, edit/logout)
│   │   │   ├── guides/
│   │   │   │   ├── page.tsx                  # Feed with PostCard (like/comment)
│   │   │   │   └── [id]/page.tsx             # Guide detail + slot booking
│   │   │   ├── guide/
│   │   │   │   ├── page.tsx                  # Guide dashboard
│   │   │   │   ├── manage/page.tsx           # Guide settings (slots, interests)
│   │   │   │   └── posts/page.tsx            # Guide's own posts (compose + delete)
│   │   │   ├── become-guide/page.tsx         # Guide signup (MBTI, interests)
│   │   │   ├── review/[bookingId]/page.tsx   # Star-rating + comment form (traveler)
│   │   │   ├── traveler/
│   │   │   │   ├── page.tsx                  # Traveler home
│   │   │   │   └── profile/page.tsx          # Traveler MBTI + interests
│   │   │   └── traveler/following/page.tsx
│   │   ├── components/
│   │   │   ├── Navbar.tsx                    # 3-lang picker + avatar → /profile
│   │   │   ├── LanguagePicker.tsx
│   │   │   └── InterestPicker.tsx            # Shared categorized interest picker
│   │   └── lib/
│   │       ├── i18n.ts                       # ko/en/zh, 30 interests, 6 categories
│   │       ├── interests.ts                  # InterestKey type + INTEREST_CATEGORIES
│   │       ├── api.ts                        # saveUserName / getUserName helpers added
│   │       └── mode.ts
│   └── backend/src/main/java/com/guidematch/
│       ├── auth/         # JWT, User entity, signup/login
│       ├── guide/        # GuideProfile, GuidePost, slots; avgRating + reviewCount on GuideProfile
│       ├── booking/      # Booking entity + flow
│       ├── chat/         # ChatMessage
│       ├── post/         # PostLike, PostComment, interactions
│       └── review/       # Review entity, ReviewController, ReviewService, ReviewRepository
└── CLAUDE.md
```

---

## Feature Roadmap

### Done
- [x] User auth (signup, login, JWT)
- [x] Guide profile creation (languages, credentials, avatar upload)
- [x] Guide search & browse (by region)
- [x] Booking flow (request → accept/reject/cancel)
- [x] 1:1 chat per booking
- [x] Traveler/guide mode switching
- [x] **Multi-language (ko/en/zh)** — 3-option dropdown in Navbar
- [x] **Instagram-style posts** — full-width square images, max-w-[468px] PostCard
- [x] **Post likes & comments** — ❤️ like toggle, 💬 comment thread per post
- [x] **Available slots** — guides add date+time slots; travelers pick slots when booking
- [x] **Guide MBTI + interests** — stored in `guide_profiles.mbti/interests`
- [x] **Traveler MBTI + interests** — stored in `users.mbti/interests` (separate from guide)
- [x] **Category-based interests** — 30 items in 6 categories via `InterestPicker` component
- [x] **Landing page login-aware CTA** — shows personalized card (name, mode, dashboard link) if logged in
- [x] **Reviews & ratings** — `POST /api/bookings/{bookingId}/review` (traveler only, 1-5 stars + comment); `GET /api/guides/{guideProfileId}/reviews`; `avgRating` + `reviewCount` on GuideProfile
- [x] **Profile page** — `/profile` shows avatar, stats (posts/rating/followers), edit + dashboard links, logout
- [x] **Guide posts manager** — `/guide/posts` lets guides compose (image + text + category) and delete their own posts
- [x] **Navbar avatar** — shows first-initial avatar linked to `/profile`; `saveUserName`/`getUserName` in `api.ts`
- [x] **Guide list layout** — full-width single-column horizontal cards (avatar · name/region/headline/tags · price/rating)
- [x] **Guide sort & filter** — client-side language filter + sort (인기순=followers, 예약순=bookingCount, 평점순=avgRating); region still server-side search box
- [x] **Post feed sort/filter** — guide-language filter + sort (추천순/인기순/조회순/최신순) on the posts tab; `viewCount` tracked via IntersectionObserver impression (`POST /api/posts/{id}/view`)
- [x] **Booking-availability segmented toggle** — two-button 예약 받는 중 / 예약 중단 control (lit state) on both `/guide/manage` and `/profile`, both hit `PATCH /api/guide-profiles/me/active`
- [x] **Location Phase 1** — structured Korean-city model (`city`/`latitude`/`longitude` on GuideProfile + User), `GET /api/cities`, GPS auto-detect via `GET /api/geo/reverse` (Kakao + nearest-city fallback), `CitySelect` component wired into become-guide/manage/traveler-profile, city-based guide search + near-me distance sort

### Location-based expansion — Phases 1–3 shipped + map viz (see `~/.claude/plans/eager-crunching-leaf.md`)
_All three phases built & verified. **Kakao REST key is live** (`KAKAO_REST_API_KEY` in app/backend/.env) — real place data + reverse geocoding confirmed E2E (`/explore` renders live places). Map route visualization added; needs a separate **JS key** (`NEXT_PUBLIC_KAKAO_JS_KEY` in app/frontend/.env.local) + domain registration — degrades to an amber note without it._
- [x] **Phase 2** — Places: public `GET /api/places?city=&category=` proxies Kakao Local (`PlaceController` + `KakaoLocalClient.searchByCategory`/`searchByKeyword`; facets attraction/food/cafe/culture → AT4/FD6/CE7/CT1, market → keyword 전통시장; radius 20km around the city). Frontend `/explore` page (city select + category tabs + place cards) + home link cards. **Structure-verified only (kakaoEnabled:false + empty without a Kakao key); real place data needs `KAKAO_REST_API_KEY`.**
- [x] **Phase 3** — Itinerary builder: `Itinerary`+`ItineraryItem` entities (owner-scoped, `@OneToMany` orphanRemoval — replace via in-place `clear()+addAll()`), authed CRUD `GET/POST/GET{id}/PUT{id}/DELETE{id} /api/itineraries/me` (PUT replaces meta+items wholesale, `saveAndFlush` so new item ids return). Frontend `/trips` (list + create) and `/trips/[id]` day-by-day builder — day tabs (dayCount = max of date-span / max item day / manual), per-day place list with ▲▼ reorder + memo + remove, "장소 담기" panel = **manual name entry (works without Kakao) + Kakao place search** (reuses `/api/places` by city+category). Home link cards for both roles. **Backend fully curl-verified; Kakao place picker now returns live data (key is set).**
- [x] **Phase 3 map viz** — `TripMap` component (`src/components/TripMap.tsx`) draws the active day's route: numbered CustomOverlay pins (`.trip-map-pin` in globals.css) + indigo Polyline in stored order, `setBounds` to fit. Loads Kakao Maps JS SDK via singleton `sdkPromise` (`sdk.js?appkey=NEXT_PUBLIC_KAKAO_JS_KEY&autoload=false`). Only items with lat/lng shown (manual-typed skipped). Wired into `/trips/[id]` below the day's item list. **No extra REST calls — renders from stored coords. Verified graceful-degrades to amber note without JS key; live map render needs the JS key + `http://localhost:3000` registered as a Web platform domain.**

### Next
- [ ] Real-time chat (WebSocket or Supabase Realtime)
- [ ] Payment integration
- [ ] Signed URLs for credential files
- [ ] JWT in httpOnly cookies
- [ ] Production DB migrations (Flyway or Liquibase)

---

## Key Architecture Patterns

### Auth
- `@AuthenticationPrincipal Long userId` in controllers — returns `null` on public endpoints (do NOT throw 401)
- Optional auth pattern: `listAll(Long currentUserId)` — pass null if unauthenticated
- Security public routes must be declared in `SecurityConfig.java`

### Interests & MBTI
- **Guide** interests/MBTI → `guide_profiles` table, API: `PATCH /api/guide-profiles/me/personality`
- **Traveler** interests/MBTI → `users` table, API: `PATCH /api/users/me/personality`
- Stored as comma-separated string in TEXT column; `getInterestList()` / `setInterestList()` on entities
- 30 interest keys defined in `src/lib/interests.ts` → `INTEREST_CATEGORIES` (6 categories × 5 items)
- `InterestPicker` component shared across become-guide, guide/manage, traveler/profile

### Posts (GuidePost)
- `PostLike` entity: unique(post_id, user_id)
- `PostComment` entity: post_id, user_id, content (text), created_at
- `GuidePostResponse` / `GuidePostWithGuideResponse` include `likeCount`, `commentCount`, `isLiked`
- `GuidePostWithGuideResponse` (feed) also includes `guideLanguages` + `viewCount`
- Feed endpoint `GET /api/posts` and guide posts `GET /api/guide-profiles/{id}/posts` both accept optional auth
- `GuidePost.category` column exists but is **dormant** (content-categories were removed from the UI; column kept since `ddl-auto` won't drop it)

### Location / cities (Phase 1)
- Canonical city list is **backend-owned**: `com.guidematch.geo.KoreanCity` (static ~20 cities, key + ko/en/zh names + lat/lng), served at public `GET /api/cities`. Frontend fetches it (localized names from API → no city i18n churn).
- `GuideProfile` + `User` each have `city` / `latitude` / `longitude` (nullable). `GuideProfile.region` kept as legacy; `updateLocation()` sets `region = city` so old region-based search still works.
- GPS: `GET /api/geo/reverse?lat=&lng=` → `KakaoLocalClient.coord2regioncode` (needs `KAKAO_REST_API_KEY`) for the precise region label, **plus** `KoreanCity.nearestTo()` (pure haversine) which always returns the nearest canonical city even without a Kakao key.
- Location writes: `PATCH /api/guide-profiles/me/location` + `PATCH /api/users/me/location`.
- Guide search: `GET /api/guides?city=` (matches via region fallback) and `?nearLat=&nearLng=` (haversine distance sort, `GeoUtils.distanceKm`). Legacy `?region=` still works.
- Frontend: `src/components/CitySelect.tsx` (dropdown from `/api/cities` + "📍 내 위치" GPS button) — reused in become-guide, guide/manage, traveler/profile, guides search.
- New public routes registered in `SecurityConfig` (`GET /api/cities`, `/api/geo/**`).

### Guide list sort/filter
- `GET /api/guides` returns `bookingCount` (ACCEPTED + COMPLETED only) alongside avgRating/reviewCount/followerCount
- Sorting & language filtering are **client-side** over the loaded list (`useMemo` in `guides/page.tsx`); region stays server-side (`?region=`)
- Language options derived from loaded data (`availableLanguages`), never hardcoded

### Post feed sort/filter (posts tab in `guides/page.tsx`)
- Mirrors the guide-list controls: **guide-language filter** + **sort** dropdowns, client-side over loaded feed
- Sort keys: `recent` (default, newest) · `recommended` = `likeCount*2 + commentCount*2 + viewCount` · `popular` = likeCount · `views` = viewCount
- Language options derived from posts' `guideLanguages` (never hardcoded)
- **View tracking**: `POST /api/posts/{id}/view` (public, atomic `@Modifying` increment) fired via `IntersectionObserver` when a card scrolls into view; deduped per session with module-level `viewedPostIds` Set

### Reviews
- `Review` entity: booking_id, reviewer_id, guide_profile_id, rating (1–5), comment (nullable), created_at
- One review per booking enforced in service layer
- `GuideProfile` carries `avgRating` (Double) and `reviewCount` (int) — updated on each review write
- Write: `POST /api/bookings/{bookingId}/review` — auth required, traveler of that booking only
- Read: `GET /api/guides/{guideProfileId}/reviews` — public
- Frontend: `/review/[bookingId]/page.tsx` — star hover + comment textarea

### Available Slots
- `AvailableSlot` entity: guide_profile_id, start_at, end_at (LocalDateTime, guide's local time)
- Public read: `GET /api/guides/{guideProfileId}/slots`
- Guide management: `POST/DELETE /api/guide-profiles/me/slots`
- Booking UI auto-fills startAt + hours from selected slot

### i18n
- `src/lib/i18n.ts` — single file, 3 language objects (ko/en/zh), all keys must exist in all 3
- `Lang` type: `"ko" | "en" | "zh"`
- When adding new i18n keys, add to ALL 3 language blocks

---

## Key Constraints

- **No new npm packages** without explicit approval — keep frontend bundle lean
- **No architectural changes** (microservices, new frameworks) without coordinator decision
- **Never drop DB columns/tables** without user confirmation
- **Never commit `.env`** — only `.env.example`
- **Price is snapshotted at booking time** — `hourly_rate_snapshot` must never be derived live
- **File uploads** go to Supabase Storage only — never local disk

---

## Running Locally

```bash
# Backend
cd app/backend
./gradlew bootRun      # :8080 — stalls at "80% EXECUTING" when live (normal)

# Frontend
cd app/frontend
npm run dev            # :3000
```

Copy `app/backend/.env.example` → `app/backend/.env` and fill in Supabase credentials.
