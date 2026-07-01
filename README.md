<h1 align="center">🗺️ Kyum Platform</h1>

<p align="center">
  <b>외국인 여행자와 한국 현지 가이드를 연결하는 C2C 가이드 매칭 플랫폼</b><br/>
  <i>A C2C local guide matching platform connecting foreign travelers with Korean locals</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Next.js-15-black?logo=next.js" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?logo=spring" />
  <img src="https://img.shields.io/badge/PostgreSQL-Supabase-3ECF8E?logo=supabase" />
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=java" />
  <img src="https://img.shields.io/badge/status-MVP-indigo" />
</p>

---

## 📖 Overview

Kyum Platform은 한국을 여행하는 외국인 여행자와 현지 가이드를 직접 연결하는 **C2C 마켓플레이스**입니다.
여행자는 원하는 지역·스타일의 가이드를 찾고, 가이드는 자신의 프로필과 가능 일정을 설정해 맞춤형 투어를 제공합니다.

---

## ✨ Features

### 👤 Auth & Profile
- JWT 기반 회원가입 / 로그인
- **가이드 / 여행자 모드 전환** — 하나의 계정으로 두 역할 모두 사용 가능
- 가이드 프로필 (헤드라인, 지역, 시간당 요금, 아바타, 소개글)
- MBTI + 30가지 카테고리별 관심사 (가이드 & 여행자 각각 독립 저장)

### 🔍 Guide Discovery
- 지역별 가이드 검색 & 피드
- 가이드 팔로우 / 팔로잉 목록
- 평균 별점 & 리뷰 수 표시

### 📅 Booking Flow
- **캘린더 UI** — 가이드가 월별 캘린더에서 가능 시간대 추가/삭제
- 여행자는 캘린더에서 가능한 날짜를 한눈에 확인 후 슬롯 선택
- 예약 요청 → 가이드 수락/거절/취소 플로우
- 부킹 시 시간당 요금 스냅샷 저장

### 💬 Chat
- 예약 건별 1:1 채팅 (REST 기반)

### 📸 Social Posts
- 가이드 인스타그램 스타일 피드 (정사각형 이미지)
- ❤️ 좋아요 토글 & 💬 댓글 스레드

### ⭐ Reviews & Ratings
- 완료된 부킹에 대한 별점 + 코멘트 리뷰
- 가이드 상세 페이지에 평균 별점 / 리뷰 수 노출

### 🌐 Multi-language
- 한국어 / English / 中文 3개 언어 전환 (Navbar 드롭다운)
- 모든 UI 텍스트 i18n 처리

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | Next.js 15, React 19, TypeScript, Tailwind CSS 3 |
| **Backend** | Spring Boot 3.3.5, Java 21, Spring Security, JWT |
| **Database** | PostgreSQL via Supabase (JPA `ddl-auto: update`) |
| **File Storage** | Supabase Storage |
| **Auth** | JWT (24h expiry), BCrypt |

---

## 🗂️ Project Structure

```
kyum_platform/
├── app/
│   ├── frontend/                      # Next.js 15
│   │   └── src/
│   │       ├── app/
│   │       │   ├── page.tsx           # 랜딩 (로그인 인식 CTA)
│   │       │   ├── guides/            # 가이드 피드 & 상세
│   │       │   ├── guide/manage/      # 가이드 대시보드
│   │       │   ├── become-guide/      # 가이드 가입
│   │       │   ├── traveler/          # 여행자 홈 & 프로필
│   │       │   ├── chat/[bookingId]/  # 1:1 채팅
│   │       │   └── review/[bookingId] # 리뷰 작성
│   │       ├── components/
│   │       │   ├── Navbar.tsx         # 3개 언어 전환
│   │       │   ├── SlotCalendar.tsx   # 가이드/여행자 공용 캘린더
│   │       │   └── InterestPicker.tsx # 카테고리별 관심사 선택기
│   │       └── lib/
│   │           ├── i18n.ts            # ko / en / zh 번역
│   │           ├── interests.ts       # 30개 관심사 정의
│   │           └── api.ts             # API 클라이언트
│   └── backend/                       # Spring Boot 3
│       └── src/main/java/com/guidematch/
│           ├── auth/                  # JWT, User 엔티티
│           ├── guide/                 # 프로필, 포스트, 슬롯, 팔로우
│           ├── booking/               # 예약 플로우
│           ├── chat/                  # 채팅 메시지
│           └── review/                # 리뷰 & 별점
└── .claude/agents/                    # Multi-agent 설정
    ├── developer.md
    ├── designer.md
    └── dba.md
```

---

## 🚀 Getting Started

### Prerequisites
- Node.js 20+
- Java 21
- Supabase 프로젝트 (DB + Storage)

### Backend

```bash
cd app/backend
cp .env.example .env   # Supabase 자격증명 입력
./gradlew bootRun      # http://localhost:8080
```

> `80% EXECUTING`에서 멈추는 게 정상입니다 — 서버가 실행 중인 상태예요.

### Frontend

```bash
cd app/frontend
npm install
npm run dev            # http://localhost:3000
```

---

## 🗺️ Roadmap

| Status | Feature |
|--------|---------|
| ✅ | Auth (JWT), Guide & Traveler profiles |
| ✅ | Guide search & browse |
| ✅ | Booking flow (request → accept/reject/cancel) |
| ✅ | 1:1 chat per booking |
| ✅ | Mode switching (guide ↔ traveler) |
| ✅ | Multi-language (ko / en / zh) |
| ✅ | Instagram-style posts with likes & comments |
| ✅ | Available slots — Calendar UI |
| ✅ | MBTI + categorized interests (30 items) |
| ✅ | Reviews & ratings |
| 🔜 | Real-time chat (WebSocket) |
| 🔜 | Payment integration |
| 🔜 | Signed URLs for credential files |
| 🔜 | JWT in httpOnly cookies |
| 🔜 | Production DB migrations (Flyway) |

---

## 📄 License

Private project — all rights reserved.
