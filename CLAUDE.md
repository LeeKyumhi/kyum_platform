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
│   │   │   ├── page.tsx                  # Landing (login-aware CTA)
│   │   │   ├── guides/
│   │   │   │   ├── page.tsx              # Feed with PostCard (like/comment)
│   │   │   │   └── [id]/page.tsx         # Guide detail + slot booking
│   │   │   ├── guide/manage/page.tsx     # Guide dashboard (slots, interests)
│   │   │   ├── become-guide/page.tsx     # Guide signup (MBTI, interests)
│   │   │   ├── traveler/
│   │   │   │   ├── page.tsx              # Traveler home
│   │   │   │   └── profile/page.tsx      # Traveler MBTI + interests
│   │   │   └── traveler/following/page.tsx
│   │   ├── components/
│   │   │   ├── Navbar.tsx                # 3-lang picker (ko/en/zh)
│   │   │   ├── LanguagePicker.tsx
│   │   │   └── InterestPicker.tsx        # Shared categorized interest picker
│   │   └── lib/
│   │       ├── i18n.ts                   # ko/en/zh, 30 interests, 6 categories
│   │       ├── interests.ts              # InterestKey type + INTEREST_CATEGORIES
│   │       ├── api.ts
│   │       └── mode.ts
│   └── backend/src/main/java/com/kyum/platform/
│       ├── auth/         # JWT, User entity, signup/login
│       ├── guide/        # GuideProfile, GuidePost, slots
│       ├── booking/      # Booking entity + flow
│       ├── chat/         # ChatMessage
│       └── post/         # PostLike, PostComment, interactions
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

### Next
- [ ] Reviews & ratings (after booking completion)
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
- Feed endpoint `GET /api/posts/feed` and guide posts `GET /api/guide-profiles/{id}/posts` both accept optional auth

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
