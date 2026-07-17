# PeerUp 코드 학습 정리

> C2C 가이드 매칭 플랫폼(Next.js + Spring Boot + Supabase)을 파일 단위로 학습한 내용을 카테고리별로 정리한 문서.
> 사용법: 이 파일을 노션에 **가져오기(Import) → Markdown** 으로 올리면 제목·표·코드가 그대로 들어갑니다.

---

## 0. 큰 그림 (아키텍처)

세 덩어리가 각자 다른 포트에서 돌며 협력한다. 화면 / 로직 / 데이터를 나눈 **3계층(3-tier) 구조**.

```
[브라우저]
   │  화면 표시, 입력 받기
   ▼
[프론트엔드 - Next.js]   http://localhost:3000
   │  REST API 호출(HTTP) / WebSocket(실시간)
   ▼
[백엔드 - Spring Boot]   http://localhost:8080
   │  JDBC(DB) / REST(파일 업로드)
   ▼
[DB·저장소 - Supabase]   PostgreSQL + Storage
```

**백엔드 4층 구조 (거의 모든 기능이 이 틀)**

| 층 | 역할 | 예 |
|---|---|---|
| Controller | 요청 받는 문지기. 주소↔메서드 연결 | `AuthController` |
| Service | 실제 로직·권한·검증 | `AuthService` |
| Repository | DB 접근 (조회/저장) | `UserRepository` |
| Entity | DB 테이블 = 자바 클래스 | `User` |
| DTO | 계층 간 데이터 그릇 (입력/출력 분리) | `LoginRequest`, `UserResponse` |

---

## 1. 핵심 개념 사전 — 백엔드 (Spring Boot / Java)

| 개념 | 쉬운 설명 |
|---|---|
| **어노테이션 `@`** | "이 클래스/메서드를 이렇게 취급해줘"라고 프레임워크에 붙이는 꼬리표 |
| **`@RestController`** | JSON을 주고받는 웹 컨트롤러 선언 |
| **`@Service` / `@Component` / `@Repository`** | 이 클래스를 스프링 **빈(Bean)**으로 등록 (`@Component`의 용도별 별명) |
| **의존성 주입(DI)** | 필요한 부품을 직접 `new` 안 하고 생성자에 적으면 스프링이 넣어줌 |
| **`@RequestBody`** | 요청 JSON을 DTO 객체로 자동 변환 (역직렬화) |
| **`@RequestParam`** | 주소 뒤 쿼리 파라미터(`?city=서울`)를 받음 (조회용) |
| **`@PathVariable`** | 주소 경로의 값(`/guides/{id}`)을 받음 |
| **`@Valid` + 검증 어노테이션** | `@Email`·`@NotBlank`·`@Size` 규칙을 자동 검사 |
| **`@AuthenticationPrincipal`** | JWT 필터가 심어둔 현재 로그인 userId를 꺼냄 |
| **`ResponseEntity`** | HTTP 상태코드(200/201/204/400…) + 본문을 담는 응답 상자 |
| **Entity `@Entity`** | 클래스=테이블, 필드=컬럼, 객체=행 (ORM/JPA) |
| **`@Id` / `@GeneratedValue`** | 기본키(고유번호), DB가 자동 증가로 매김 |
| **`@Column(unique/nullable)`** | 컬럼 제약 (중복 금지 / 빈 값 금지) |
| **`Optional<T>`** | "값이 있을 수도 없을 수도" 있는 안전 상자 (NPE 예방) |
| **이름 규칙 쿼리** | `findByEmail` 같은 메서드 이름만으로 SQL 자동 생성 |
| **`@Query`** | 이름으로 표현 어려운 쿼리를 JPQL로 직접 작성 |
| **N+1 문제 / `join fetch`** | 목록 조회 시 관계를 한 번에 로딩해 쿼리 폭증 방지 |
| **일괄 집계 + Map** | 여러 대상의 통계를 한 쿼리로, 사전(Map)에서 꺼내 조립 |
| **`@Transactional`** | DB 작업을 하나의 안전한 묶음으로 (실패 시 전체 취소). 조회엔 `readOnly=true` |
| **`enum`** | 정해진 값 목록 (상태 5종 등). 오타·잘못된 값 원천 차단 |
| **상태 머신 / 풍부한 도메인 모델** | 엔티티가 `accept()`·`cancel()`로 상태 전이 규칙을 스스로 강제 |
| **스냅샷 패턴** | 계약 시점 값(시급) 복사 저장 → 나중에 원본 바뀌어도 과거 기록 불변 |
| **BCrypt 단방향 해시** | 비밀번호를 되돌릴 수 없게 저장, 로그인 땐 해시끼리 대조 |
| **JWT** | 서버가 서명한 출입증. 발급(로그인) 후 매 요청에 첨부 |
| **멱등성(idempotent)** | 같은 요청을 여러 번 보내도 결과가 한 번과 같음 (팔로우 중복 무시) |
| **`MultipartFile`** | 업로드된 파일을 담는 타입 (multipart/form-data) |
| **`RestClient`** | 우리 백엔드가 **다른 서버**에 HTTP 요청을 보내는 도구 |

---

## 1. 핵심 개념 사전 — 프론트엔드 (Next.js / React / TypeScript)

| 개념 | 쉬운 설명 |
|---|---|
| **`"use client"`** | 이 파일은 브라우저에서 동작(상호작용 필요) 선언 |
| **App Router** | 폴더 경로 = URL 주소 (`app/login/page.tsx` → `/login`) |
| **동적 라우팅 `[id]`** | 주소의 변하는 부분. `useParams()`로 값을 꺼냄 |
| **컴포넌트 / JSX** | 화면 한 조각 = 함수 하나. JS 안에 HTML 섞어 쓰기 |
| **props** | 부모가 자식 컴포넌트에 넘기는 입력값 |
| **`useState`** | 바뀌면 화면을 다시 그리는 상태값 `[값, set함수]` |
| **제어 컴포넌트** | `value` + `onChange` 짝으로 입력창과 상태를 일치시킴 |
| **불변성 / 스프레드 `...`** | 상태는 직접 수정 X, 복사 후 교체 `{ ...form, key: val }` |
| **`useEffect`** | 화면 진입/값 변경 시 코드 실행. `[]`면 최초 1회 |
| **정리(cleanup) 함수** | `useEffect`가 반환하는 함수. 컴포넌트 사라질 때 실행(연결 끊기) |
| **`useMemo`** | 파생 데이터(필터·정렬 결과)를 재료 바뀔 때만 계산 |
| **`useRef`** | 화면 갱신과 무관한 값·DOM 요소 참조. `.current`로 접근 |
| **`useCallback`** | 함수를 기억해 재사용 (`useEffect` 의존성에 넣을 때) |
| **커스텀 훅** | `useLanguage`처럼 여러 훅을 조합한 나만의 훅 |
| **React Context** | props 없이 앱 전체가 값을 공유하는 방송 시스템 |
| **목록 렌더링 `.map()` + `key`** | 데이터 배열 → JSX 배열. `key`는 항목 구분용 |
| **조건부 렌더링** | `{조건 && <요소>}`, `{조건 ? A : B}` |
| **상태 4분기 UI** | 로딩(스켈레톤) / 에러 / 빈 목록 / 목록 |
| **낙관적 업데이트** | 서버에 다시 안 묻고 화면 먼저 갱신 (빠릿한 반응) |
| **`async/await` / `Promise`** | 서버 응답을 기다리기. `.then()`은 병렬 호출용 |
| **`fetch` / `api()`** | HTTP 요청. `api()`는 주소·헤더·토큰·에러를 모은 공통 도우미 |
| **`localStorage`** | 브라우저 영구 저장 (토큰·언어 유지). 서버엔 없음(`typeof window` 방어) |
| **`FormData` / `apiUpload`** | 파일 업로드 전용. `Content-Type` 직접 지정 안 함 |
| **`router.push` / `router.replace`** | 페이지 이동. replace는 히스토리 갈아치움(뒤로가기 방지) |

---

## 2. 기능별 정리

### 🔐 인증 — 로그인 / 회원가입

**흐름**: 입력 → `api()` → Controller → Service(비번 해시 대조) → JWT 발급 → 저장 → 이후 요청에 토큰 첨부

| 파일 | 역할 |
|---|---|
| `login/page.tsx`, `signup/page.tsx` | 로그인·회원가입 화면 (useState, onSubmit) |
| `lib/api.ts` | 백엔드 통신 공통 도우미 (토큰 저장/첨부, 에러 처리) |
| `AuthController` | `/api/auth/login`·`/signup` 문지기 |
| `LoginRequest`·`SignupRequest`·`TokenResponse`·`UserResponse` | 입출력 DTO |
| `AuthService` | 비밀번호 BCrypt 해시·대조, 토큰 발급 |
| `JwtProvider` | JWT 발급/검증 (비밀키 서명) |
| `SecurityConfig` | 주소별 출입 규칙, CORS, BCrypt 빈 등록 |
| `JwtAuthenticationFilter` | 매 요청 토큰 검증 → 신원 기록 |
| `User`·`UserRepository`·`UserController` | 사용자 엔티티·DB·`/me` |
| `GlobalExceptionHandler` | 예외 → `{"error":"..."}` 통일 → 프론트 빨간 박스 |

**핵심**: 가입 ≠ 로그인 / BCrypt 단방향 해시 / JWT 무상태 인증 / 에러 메시지 일부러 뭉뚱그림(보안)

### 🧭 가이드 목록 · 검색 · 상세

**흐름**: `useEffect`로 `GET /api/guides?city=…` → Service 검색 → 일괄 집계 → 카드 렌더 → `<Link>`로 상세 이동

| 파일 | 역할 |
|---|---|
| `guides/page.tsx` | 목록 화면 (useEffect·useMemo, 필터·정렬은 프론트) |
| `guides/[id]/page.tsx` | 상세 (동적 라우팅, 병렬 API, 낙관적 팔로우, 예약 폼) |
| `GuideController` | 목록·상세. 여러 테이블 데이터 일괄 집계 |
| `GuideProfile`·`GuideProfileRepository`·`GuideProfileService` | 엔티티(언어 `@OneToMany`)·조회(join fetch)·로직 |
| `GuideSummaryResponse`·`GuideDetailResponse` | 목록용(가벼움)·상세용(introduction·credentials 추가) DTO |

**핵심**: `@OneToMany`+LAZY / N+1 방지(join fetch + 일괄 집계) / map·filter·sorted / 동적 라우팅 / 낙관적 업데이트

### 📅 예약 (매칭)

**흐름**: 상세 예약 폼 → `POST /api/bookings` → 상태 머신(요청→수락→완료) → 여행자/가이드 목록에서 상태별 버튼

| 파일 | 역할 |
|---|---|
| `Booking`·`BookingStatus` | 엔티티(상태 전이 규칙 내장)·enum 5종 |
| `BookingController` | 생성(POST)·상태변경(PATCH) |
| `BookingService` | 권한 검사, 시간 충돌(이중예약) 방지, 스냅샷 |
| `traveler/bookings/page.tsx`·`guide/requests/page.tsx` | 여행자/가이드 화면 (상태별 버튼) |

**핵심**: enum 상태 머신 / 스냅샷 / 권한·충돌 검사 / 재조회 vs 낙관적 / 프론트는 안내, 백엔드가 진짜 방어

### ⭐ 리뷰

**흐름**: 예약 COMPLETED → 리뷰 작성 → 집계로 가이드 평점 반영

| 파일 | 역할 |
|---|---|
| `Review`·`ReviewRepository` | 엔티티(예약당 1개 unique)·집계 쿼리(avg/count/group by) |
| `ReviewService` | 3중 검증(권한·상태·중복) |
| `ReviewController` | 자원 중첩 URL (`/api/bookings/{id}/review`, `/api/guides/{id}/reviews`) |

**핵심**: 기능 간 의존(예약 완료 필요) / 이중 방어 / 집계로 목록 평점(N+1 방지) / coalesce

### 💬 실시간 채팅 (WebSocket + STOMP)

**흐름**: `/ws` 연결(토큰 인증) → `/topic/bookings/{id}` 구독 → `/app/.../send` 발행 → 참여자 전원 브로드캐스트

| 파일 | 역할 |
|---|---|
| `WebSocketConfig` | 엔드포인트·브로커(/topic 뿌리기, /app 보내기) |
| `StompAuthChannelInterceptor` | CONNECT 시점 토큰 검증(HTTP 필터 안 거침) |
| `ChatWebSocketController`·`StompPrincipal` | 발행 받아 저장·브로드캐스트 / 연결 신분증 |
| `MessageService` | 참여자 권한(`assertParticipant`) |
| `chat/[bookingId]/page.tsx` | STOMP 클라이언트, useRef, 정리 함수 |

**핵심**: REST(요청-응답) vs WebSocket(지속 양방향) / 발행·구독 / 연결 시점 인증 / useRef·cleanup

### 🗂️ 가이드 등록 + 파일 업로드

**흐름**: 프로필 텍스트(JSON) 등록 → 이후 자격증·사진(multipart) 업로드 → Storage에 올리고 DB엔 URL만

| 파일 | 역할 |
|---|---|
| `become-guide/page.tsx` | 등록 화면 (동적 폼: 언어 추가/삭제) |
| `SupabaseStorageClient` | 외부 Storage에 파일 업로드(RestClient) |
| `GuideCredentialController`·`GuideCredentialService` | multipart 수신·경로 생성(UUID) |
| `GuideProfileController` | 프로필 생성·아바타 업로드 |

**핵심**: multipart / 파일은 Storage·DB엔 URL만 / UUID로 충돌 방지 / 동적 폼(배열 추가·삭제) / 2단계 등록

### 👥 팔로우

**흐름**: 상세 팔로우 버튼 → POST/DELETE `/api/guides/{id}/follow` → 연결 테이블에 관계 저장

| 파일 | 역할 |
|---|---|
| `Follow`·`FollowRepository` | 연결 테이블(복합 unique)·집계(팔로워 수) |
| `FollowService` | 멱등 팔로우/언팔로우, 팔로잉 목록 |
| `FollowController` | POST(생성)/DELETE(삭제) 대칭 |

**핵심**: 다대다(N:M) → 연결 테이블 / 복합 유니크 / 멱등성 / POST·DELETE 대칭·204

### 🌐 다국어 (i18n / React Context)

**흐름**: `LanguageProvider`가 앱 전체를 감싸 현재 언어 사전(`t`)을 방송 → 각 파일이 `useLanguage()`로 수신

| 파일 | 역할 |
|---|---|
| `context/LanguageContext.tsx` | createContext·Provider·useLanguage(커스텀 훅) |
| `app/layout.tsx` | 앱 전체를 Provider로 감쌈 |
| `lib/i18n.ts` | 언어별·화면별 번역 사전(중첩 객체), localStorage 저장 |

**핵심**: prop drilling 회피 / Context 3요소(createContext·Provider·useContext) / `t=translations[lang]` 교체로 전체 재렌더

### ⚙️ 앱 부팅과 설정

| 파일 | 역할 |
|---|---|
| `build.gradle` | 빌드 도구·라이브러리 목록(starter). 모든 기능의 출처 |
| `GuideMatchApplication.java` | `main`+`@SpringBootApplication` → Tomcat·컴포넌트 스캔 |
| `application.yml` | 포트·파일크기·DB·JPA(`ddl-auto`)·JWT·Supabase 설정 |

**핵심**: 컴포넌트 스캔이 DI의 근본 / `ddl-auto:update`로 테이블 자동 생성 / `${ENV}`로 비밀값은 코드 밖에서

---

## 3. 반복되는 뼈대 (어디서나 재사용)

이 패턴들이 눈에 익으면 새 기능/새 프로젝트도 빠르게 읽힌다.

1. **백엔드 4층**: Controller(입구) → Service(로직·권한) → Repository(DB) → Entity(데이터)
2. **의존성 주입**: 생성자에 부품을 적으면 스프링이 주입 (컴포넌트 스캔 덕분)
3. **DTO 입출력 분리**: 입력 `~Request`, 출력 `~Response`. 엔티티(특히 비밀번호) 직접 노출 X
4. **검증 순서**: 권한 → 상태 → 규칙(중복 등). 예약·리뷰·채팅에서 반복
5. **이중 방어**: 서비스에서 친절히 막고 + DB 제약(unique)으로 최종 방어
6. **N+1 방지**: `join fetch` + 일괄 집계 + Map 사전 조립
7. **정적 팩토리 `from()`/`of()`**: 엔티티 → DTO 변환을 한 곳에
8. **프론트 데이터 화면**: `useEffect`(불러오기) → 상태 4분기 → `.map()`(렌더) → `<Link>`(이동)
9. **상태 관리 훅**: `useState`(상태) · `useEffect`(생명주기) · `useMemo`/`useRef`/`useCallback`
10. **HTTP 메서드**: 생성 POST(201) · 조회 GET · 부분수정 PATCH · 삭제 DELETE(204)

---

## 4. 배운 것 체크리스트

**백엔드**
- [x] 4층 구조 + 의존성 주입
- [x] DTO 입출력 분리 + 검증(@Valid)
- [x] JWT 인증 (발급·필터·연결시점) + Spring Security
- [x] JPA (엔티티·연관관계·N+1·트랜잭션·집계·enum)
- [x] 상태 머신 / 스냅샷 / 멱등성 / 권한·충돌 검사
- [x] 파일 업로드(multipart) + 외부 Storage
- [x] WebSocket/STOMP 실시간
- [x] 앱 부팅·설정(gradle·yml·컴포넌트 스캔)

**프론트엔드**
- [x] 컴포넌트·JSX·props, `useState`, 제어 컴포넌트
- [x] `useEffect`·`useMemo`·`useRef`·`useCallback` + 정리 함수
- [x] 목록·조건부 렌더링, 상태 4분기, 동적 폼
- [x] 동적 라우팅, 낙관적 업데이트, 병렬 API
- [x] React Context(전역 상태), 커스텀 훅

**아직 안 본 것 (다음 후보)**
- [ ] 지도·지오코딩 (Kakao/Google 외부 API 연동, 캐싱) — `explore`·`trips`
- [ ] 투어 코스 (가이드 상품)
- [ ] 여행 일정(Itinerary)
- [ ] 사이드바·모드 전환 로직

---

*작성: 코드 파일 단위 학습 정리 · PeerUp (가이드 매칭 플랫폼)*
