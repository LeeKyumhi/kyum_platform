# 가이드 매칭 플랫폼 개발 — 학습 정리

> C2C 가이드 매칭 플랫폼을 **Next.js + Spring Boot + Supabase**로 만든 전 과정을, 처음 보는 사람도 이해할 수 있게 정리한 문서.

---

## 0. 큰 그림: 이 앱은 어떻게 생겼나

세 덩어리가 각자 다른 포트에서 돌면서 협력한다.

```
[브라우저]
   │  화면 보여주고 입력 받음
   ▼
[프론트엔드 - Next.js]   http://localhost:3000
   │  REST API 호출 (HTTP) / WebSocket (실시간)
   ▼
[백엔드 - Spring Boot]   http://localhost:8080
   │  JDBC (DB 연결) / REST (파일 업로드)
   ▼
[DB·저장소 - Supabase]   PostgreSQL + Storage
```

이렇게 **화면 / 로직 / 데이터**를 나눈 구조를 **3-tier(3계층) 아키텍처**라고 한다. 각 층을 독립적으로 고치고 키울 수 있어 유지보수에 유리하다.

**왜 이 스택인가**
- **Next.js (React)**: 한 언어(TypeScript)로 화면을 빠르게. App Router로 폴더가 곧 주소가 된다.
- **Spring Boot (Java)**: 백엔드 로직·인증. 한국 채용 시장 표준이라 학습 가치가 큼.
- **Supabase**: PostgreSQL + 파일 저장소(Storage)를 관리형으로 제공. 서버 운영 부담 ↓.

---

## 1. 꼭 알아야 할 용어 사전

| 용어 | 쉬운 설명 |
|---|---|
| **REST API** | 프론트가 백엔드에 "이거 줘/해줘"라고 요청하는 규칙. 주소(URL) + 방식(GET/POST/…)으로 표현 |
| **GET / POST / PATCH / DELETE** | 각각 조회 / 생성 / 부분수정 / 삭제. HTTP "메서드" |
| **JSON** | 데이터를 주고받는 글자 형식. `{"key":"value"}` |
| **Entity (엔티티)** | 자바 클래스 1개 = DB 테이블 1개. 필드 = 컬럼 |
| **Repository** | DB 접근 창구. 메서드 이름만 적으면 SQL 자동 생성 (`findByEmail` 등) |
| **Service** | 실제 규칙·로직을 담는 층 (중복검사, 계산 등) |
| **Controller** | 외부 요청을 받고 응답을 돌려주는 창구 |
| **DTO** | 계층 간 데이터를 나르는 그릇. 엔티티를 그대로 노출하지 않으려고 따로 씀 (예: 비밀번호 제외) |
| **JPA / Hibernate** | 자바 객체 ↔ DB 테이블을 자동 매핑해주는 도구(ORM). SQL을 덜 짜게 해줌 |
| **ddl-auto: update** | 엔티티를 보고 DB에 테이블을 자동 생성/수정 (개발 초기에 편리) |
| **JWT** | 로그인 증명용 "출입증" 토큰. 서버가 서명해서 위조 불가. `eyJ...`로 시작 |
| **BCrypt** | 비밀번호를 되돌릴 수 없게 암호화(해시)하는 방식. 원문은 절대 저장 안 함 |
| **Spring Security** | 인증/인가(누구인지, 무엇을 할 수 있는지)를 다루는 프레임워크 |
| **CORS** | "다른 출처(3000→8080) 요청을 허용할지" 정하는 브라우저 보안 규칙 |
| **환경변수 / .env** | 비밀번호·키처럼 코드에 직접 적으면 안 되는 값을 따로 보관하는 파일 |
| **multipart/form-data** | 파일 업로드용 요청 형식 (일반 JSON과 다름) |
| **WebSocket / STOMP** | 연결을 계속 열어두고 양방향 즉시 통신. STOMP는 그 위의 구독/발행 약속 |
| **localStorage** | 브라우저에 값을 저장하는 공간. 우리는 JWT·모드 저장에 사용 |

---

## 2. 데이터 모델 (테이블 설계)

가장 먼저 한 일. 집으로 치면 기초공사.

```
users (계정·공통)
  └─ guide_profiles (가이드로 활동 시 1:1)
        ├─ guide_languages   (가능 언어, 1:N)
        ├─ guide_credentials (학력·자격증 파일, 1:N)
        └─ avatar_url        (프로필 사진)

bookings (예약·매칭)   ── traveler_id → users,  guide_profile_id → guide_profiles
  ├─ messages (채팅, 예약 단위)
  └─ reviews  (리뷰, 완료된 예약당 1개)
```

**핵심 설계 결정**
- **users와 guide_profiles 분리**: 한 계정이 여행자/가이드 둘 다 가능. 가이드로 활동할 때만 `guide_profiles` 생성.
- **1:N은 별도 테이블로**: 한 가이드가 여러 언어를 가지므로 `guide_languages`를 따로. 한 칸에 몰면 검색이 어려움.
- **시급 스냅샷(`hourly_rate_snapshot`)**: 예약 시점의 요금을 복사 저장. 가이드가 나중에 요금을 바꿔도 과거 예약 금액은 고정 → 정산·분쟁 안전.

---

## 3. 단계별 개발 과정

### 1단계 — 데이터 모델 설계 (ERD)
무엇을 저장할지 테이블과 관계를 먼저 그림. 여기가 틀어지면 이후 전부 고생.

### 2단계 — 프로젝트 뼈대
- 프론트(Next.js) + 백엔드(Spring Boot) + Supabase 연결.
- 백엔드는 처음에 "켜지는 것"만 확인(`/api/health`) → 그다음 DB 연결.
- `.env`에 DB 접속정보 보관, `application.yml`에서 `${...}`로 읽음.

### 3단계 — 회원가입·로그인 (인증)
가장 묵직한 학습 파트. 흐름:

```
회원가입: 비밀번호를 BCrypt로 해시 → users에 저장
로그인:   비밀번호 대조 성공 → JWT 발급
이후요청: 헤더에 "Authorization: Bearer <토큰>" 첨부
          → JwtAuthenticationFilter가 토큰 검증 → "이 요청은 userId N"이라고 표시
보호자원: 토큰 없으면 401(인증 필요)
```

핵심 파일: `User`(엔티티), `AuthController/AuthService`(가입·로그인), `JwtProvider`(토큰 생성/검증), `JwtAuthenticationFilter`(매 요청 검사), `SecurityConfig`(공개/보호 주소 + CORS).

**계층 구조 (Spring의 표준 패턴):**
```
Controller (요청 받기) → Service (규칙 처리) → Repository (DB) → DB
```
역할이 섞이지 않아 수정·테스트가 쉽다.

### 4단계 — 가이드 프로필 등록
- `guide_profiles` + `guide_languages`(`@OneToMany`, cascade로 함께 저장).
- 자격증·프로필 사진은 **Supabase Storage**에 업로드(`multipart`) 후 URL만 DB에 저장.
- `@Transactional`: 여러 DB 작업을 한 묶음으로 → 중간 실패 시 전부 취소(반쪽 저장 방지).

### 5단계 — 가이드 검색·목록·상세
- 여행자가 **로그인 없이** 둘러볼 수 있도록 `GET /api/guides`를 공개(SecurityConfig에서 GET 허용).
- 원칙: **둘러보기는 공개, 행동(예약)은 로그인.** (마켓플레이스 표준, 이탈 방지)

### 6단계 — 매칭/예약
- 상태 흐름: `REQUESTED`(요청) → `ACCEPTED`(수락)/`REJECTED`(거절) → `COMPLETED`(완료), `CANCELLED`(취소).
- 권한 체크: 가이드만 수락/거절, 여행자만 취소, 본인 프로필은 예약 불가.
- 예약 시 시급 스냅샷 + 총액(시급×시간) 저장.

### 7단계 — 실시간 채팅 (WebSocket/STOMP)
- 처음엔 3초 폴링(임시) → **WebSocket**으로 교체해 즉시 전송.
- 연결 흐름:
```
브라우저 → /ws 접속 → STOMP CONNECT 시 토큰 검증(인터셉터)
구독: /topic/bookings/{id}  (이 예약의 메시지 수신)
발행: /app/bookings/{id}/send → 서버 저장 후 구독자 모두에게 broadcast
```
- HTTP가 아니라서 JWT 필터를 안 거침 → `StompAuthChannelInterceptor`에서 직접 토큰 검증.

### 8단계 — 리뷰
- 완료된 예약 → 여행자가 별점+후기 작성(예약당 1개).
- 가이드 목록/상세에 **평균 별점** 표시 → 다음 여행자의 선택을 돕는 신뢰 루프 완성.

---

## 4. 인증이 작동하는 전체 그림 (가장 중요)

```
[회원가입] 비번 → BCrypt 해시 → DB 저장 (원문 절대 저장 안 함)

[로그인]   이메일로 사용자 찾기 → 입력 비번 vs 해시 대조
           성공 → JWT 발급 → 프론트가 localStorage에 저장

[일반 요청] 프론트가 헤더에 "Bearer <토큰>" 첨부
           → 백엔드 JwtAuthenticationFilter가 토큰 까서 userId 확인
           → 컨트롤러에서 @AuthenticationPrincipal Long userId 로 사용

[WebSocket] CONNECT 시 토큰을 STOMP 헤더로 전달
           → StompAuthChannelInterceptor가 검증 → 연결에 사용자 심음
```

**보안 포인트들**
- 로그인 실패 메시지는 "이메일/비번 중 무엇이 틀렸는지" 안 알려줌 (공격 힌트 차단).
- 응답 DTO에서 비밀번호 제외.
- 비밀키·DB비번은 `.env`에만 (git에 안 올라감).

---

## 5. 우리가 실제로 겪은 함정과 교훈

실수에서 배운 것들 — 다음엔 빨리 알아챌 수 있게.

1. **코드 고치면 백엔드 재시작 필수**
   실행 중인 서버는 자동으로 안 바뀜. 새 엔드포인트가 404/401로 안 보이면 십중팔구 재시작 안 한 것.

2. **포트 8080 충돌**
   백엔드를 안 끄고 또 켜면 `Port 8080 was already in use`. 해결: `kill -9 $(lsof -t -i:8080)` 후 재시작. **백엔드는 한 번에 하나만.**

3. **`gradle bootRun`이 80%에서 멈춘 듯 보임**
   정상이다. 서버는 "끝나는 작업"이 아니라 계속 켜져 있는 상태라 그 자리에 머문다. 확인은 브라우저/curl로.

4. **`.env`의 `#` 문제**
   값에 `#`이 있으면 주석으로 오해돼 잘림. 특수문자 있는 값은 `"큰따옴표"`로 감싸기.

5. **키 오타 (앞글자 빠짐)**
   service_role 키가 `eyJ`가 아니라 `yJ`로 시작 → 업로드 실패가 401로 둔갑. JWT는 항상 `eyJ`로 시작함을 기억.

6. **curl 토큰 변수는 같은 터미널에서만 유효**
   `TOKEN=$(...)`로 담은 변수는 다른 터미널 창에서는 비어 있음 → 401.

7. **gradle 명령은 backend 폴더 안에서**
   다른 폴더에서 실행하면 "Run gradle init" 에러. `build.gradle`이 있는 폴더에서 실행.

8. **두 계정 동시 테스트**
   같은 브라우저는 localStorage 공유라 한 계정만. **시크릿 창**이나 다른 브라우저로 분리.

9. **403/500이 401로 보일 수 있음**
   인증 자원에서 내부 에러가 나면 에러 페이지가 다시 인증을 요구해 401처럼 보이기도 한다. 진짜 원인은 서버 로그에서 확인.

---

## 6. 실행 방법 (다시 켤 때)

```bash
# 백엔드 (터미널 1)
cd ~/kyum_platform/app/backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # Java 21 고정
gradle bootRun

# 프론트 (터미널 2)
cd ~/kyum_platform/app/frontend
npm install   # 최초 1회
npm run dev
```
브라우저: http://localhost:3000

---

## 7. 폴더 구조 한눈에

```
backend/src/main/java/com/guidematch/
  ├─ config/    SecurityConfig, JwtProvider, JwtAuthenticationFilter
  ├─ user/      User, UserRepository, UserController
  ├─ auth/      AuthController, AuthService, dto/
  ├─ guide/     GuideProfile, GuideLanguage, GuideCredential, 컨트롤러·서비스
  ├─ booking/   Booking, BookingService, BookingController
  ├─ chat/      Message, MessageService, ChatController, ws/(WebSocket)
  ├─ review/    Review, ReviewService, ReviewController
  └─ storage/   SupabaseStorageClient

frontend/src/app/
  ├─ page.tsx           랜딩
  ├─ signup, login      인증
  ├─ select-mode        여행자/가이드 모드 선택
  ├─ traveler, guide    각 모드 대시보드
  ├─ guides, guides/[id] 검색·상세
  ├─ become-guide, guide/manage  가이드 등록·관리
  ├─ chat/[bookingId]   실시간 채팅
  └─ review/[bookingId] 리뷰 작성
frontend/src/lib/  api.ts(통신), mode.ts(모드)
```

---

## 8. 더 공부하면 좋은 주제

- **Spring**: `@Transactional` 동작 원리, 예외 처리(`@RestControllerAdvice`), 페이징, 테스트(JUnit)
- **인증 심화**: JWT 만료·갱신(refresh token), httpOnly 쿠키 vs localStorage
- **DB**: 인덱스, N+1 문제, JPA 연관관계(`@ManyToOne` 등), 마이그레이션(Flyway)
- **배포**: 백엔드(Railway/Render), 프론트(Vercel), 환경변수 관리
- **확장**: WebSocket 다중 서버 시 Redis 브로커, 캐시(Redis)
- **결제**: Stripe Connect + 에스크로 (사업 신고 후)

---

*이 문서는 개발 과정을 학습용으로 정리한 것입니다. 현재 진행 상황과 실행법은 `PROGRESS.md`를 참고하세요.*
