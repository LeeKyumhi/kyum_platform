# 결제 시스템 설계 — 에스크로 + PortOne V2 + 수동 정산원장

- 작성일: 2026-07-27
- 상태: 설계 승인 완료 (구현 대기)
- 관련 도메인: `booking`, 신규 `payment`, `admin`

## 1. 목표와 범위

외국인 여행자가 한국 로컬 가이드를 예약할 때, 예약을 **에스크로 방식으로 선결제**하고
일정 완료 후 플랫폼이 수수료를 뗀 정산액을 가이드에게 지급하는 결제 시스템.

### 이번 범위 (In Scope)
- 여행자 결제 (수락된 예약 → PortOne V2 결제창 → 서버 검증 → 확정)
- 에스크로 보관 (플랫폼이 전액 수취, `Payment` 엔티티로 기록)
- 수동 정산 원장 (`Settlement` 엔티티, 관리자가 지급완료 처리)
- 전액 환불 (여행자 취소 시, 가이드 지급 전까지만)
- 관리자 정산 조회/지급완료 처리

### 이번 범위 아님 (Out of Scope — 향후)
- 기한별 차등 환불 (48h/24h 티어) — MVP는 단순 전액환불
- PortOne 자동 분할정산(sub-merchant) — 수동 원장으로 대체
- 가이드 정산 계좌/사업자정보 수집 UI — 관리자 수기 이체
- 부분 결제 / 카드 hold-then-capture

## 2. 확정된 결정 사항

| 항목 | 결정 |
|------|------|
| 돈 흐름 모델 | 에스크로 (선결제 → 완료 후 정산) |
| PG | PortOne (아임포트) — 국내+해외카드 어그리게이팅 |
| PortOne 버전 | **V2** (REST V2 + 브라우저 SDK V2) |
| 정산 방식 | 수동 정산 원장, 관리자 지급완료 처리 |
| 수수료율 | **15%** (설정값, 정산 시점 스냅샷) |
| 결제 시점 | **수락 후 결제** (REQUESTED → ACCEPTED → 결제 → COMPLETED) |
| 환불 정책 | 단순 전액환불 (가이드 지급 전까지만) |
| 통화 | 전액 **KRW 정수** (PortOne 원화 청구, 외국인 카드가 FX 처리) |

## 3. 핵심 원칙 (보안·정합성)

1. **`BookingStatus` enum은 손대지 않는다.** PAID를 넣지 않는다.
   결제 상태는 예약 생명주기와 직교(orthogonal). "결제됨" = `Payment` 행이 PAID로 존재하는 것.
   - 근거: `complete()`는 ACCEPTED를 요구하고 리뷰는 COMPLETED를 요구한다.
     PAID를 사이에 끼우면 이 상태 머신과 리뷰 게이트가 깨진다.
     또 COMPLETED가 된 예약이 "결제됐었나?" 비트를 잃어버리는데, 정산은 그 비트를 필요로 한다.

2. **서버측 금액 검증은 타협 불가.** 브라우저가 반환한 결제 결과를 신뢰하지 않는다.
   반드시 서버에서 PortOne API로 `paymentId`를 재조회해 **실제 결제금액 === `booking.totalPrice`** 이고
   결제 상태가 `PAID`인지 확인한 뒤에만 우리 `Payment`를 PAID로 만든다.
   (₩1,000 결제하고 ₩100,000 투어를 확정하는 고전적 결제 취약점 차단.)

3. **웹훅이 진실의 원천 + 멱등성.** 브라우저 콜백만 믿지 않는다(결제 후 탭 닫으면 확정 유실).
   PortOne 웹훅도 처리한다. 콜백·웹훅이 각각/중복 발화해도 결과는 한 번의 PAID.
   - PortOne 결제 id(`portoneUid`)에 **unique 제약** → 이중 확정/이중 정산 원천 차단.
   - 확정 로직은 멱등: 이미 PAID면 재조회·검증 통과해도 no-op.

4. **수수료율 스냅샷.** `Settlement`에 `commissionRate`(0.15)를 정산 생성 시점에 복사 저장.
   나중에 플랫폼 수수료를 바꿔도 기존 정산 금액은 변하지 않는다 (`hourlyRateSnapshot` 철학 그대로).

5. **환불 가드.** 전액환불은 `Payment`가 PAID이고 해당 `Settlement`가 아직 PAID_OUT이 아닐 때만 허용.
   이미 가이드에게 이체된 건은 PortOne 결제취소로 되돌릴 수 없다.

6. **모든 금액 KRW 정수.** `totalPrice`가 `Integer`인 것과 일치. 비-KRW가 끼면 Integer가 깨지므로 KRW 고정.

## 4. 데이터 모델 (신규 `com.guidematch.payment` 패키지)

### Payment — 돈이 들어옴 (즉시 확정)
| 필드 | 타입 | 비고 |
|------|------|------|
| id | Long PK | |
| bookingId | Long | **unique** — 예약당 결제 1건 |
| portoneUid | String | **unique** — PortOne 결제 id, 멱등 키. PENDING 단계에선 null 가능 |
| merchantUid | String | **unique** — 우리가 생성하는 주문번호 (예: `booking-{id}-{timestamp}`) |
| amount | Integer | 결제 금액(KRW) = `booking.totalPrice` 스냅샷 |
| currency | String | "KRW" |
| status | enum | `PENDING` / `PAID` / `FAILED` / `REFUNDED` |
| paidAt | Instant | nullable |
| refundedAt | Instant | nullable |
| createdAt | Instant | |

### Settlement — 돈이 나감 (수동)
| 필드 | 타입 | 비고 |
|------|------|------|
| id | Long PK | |
| bookingId | Long | **unique** — 예약당 정산 1건 |
| guideProfileId | Long | |
| grossAmount | Integer | 총액 = `booking.totalPrice` |
| commissionRate | Double | 정산 시점 스냅샷 (0.15) |
| commissionAmount | Integer | `round(gross × rate)` |
| netAmount | Integer | `gross − commissionAmount` = 가이드 수령액 |
| status | enum | `PENDING` / `PAID_OUT` |
| createdAt | Instant | COMPLETED 시점 |
| paidOutAt | Instant | nullable, 관리자 지급완료 시점 |
| adminMemo | String | nullable, 이체 참조번호 등 |

`BookingStatus`는 변경 없음. JPA `ddl-auto: update`로 두 테이블 신규 생성.

## 5. 상태 흐름

```
가이드 수락 → Booking.status = ACCEPTED
  │
  ├─ 여행자 "결제하기"
  │    POST /api/payments/prepare {bookingId}
  │      → 소유·상태 검증(내 예약·ACCEPTED·미결제) → Payment PENDING 생성(merchantUid 발급)
  │      → { merchantUid, amount, storeId, channelKey } 반환
  │    프론트: PortOne V2 브라우저 SDK로 결제창 오픈
  │
  ├─ [브라우저 콜백]  POST /api/payments/complete {paymentId(=portoneUid), merchantUid}
  ├─ [PortOne 웹훅]  POST /api/payments/webhook   (payload는 신뢰 안 함, 재조회로 검증)
  │      → PortOne GET /payments/{paymentId} 재조회
  │      → status==PAID && amount==Payment.amount 검증
  │      → Payment.status = PAID (멱등: 이미 PAID면 no-op)
  │
  ├─ 일정 종료 (기존)  PATCH /api/bookings/{id}/complete
  │      → Booking.status = COMPLETED
  │      → Payment가 PAID면 Settlement PENDING 생성 (net = gross − round(gross×0.15))
  │        (미결제 예약도 완료는 가능하되 Settlement는 생성 안 함)
  │
  └─ (관리자) 은행이체 후
       PATCH /api/admin/settlements/{id}/payout → Settlement.status = PAID_OUT
```

### 환불 (여행자 취소)
```
PATCH /api/bookings/{id}/cancel  (기존, 여행자만)
  → Payment가 PAID이고 Settlement가 PAID_OUT 아님 → PortOne 결제취소 API 호출
  → Payment.status = REFUNDED, Settlement가 있으면 정리(취소/삭제)
  → Booking.status = CANCELLED
가드: Settlement PAID_OUT이면 환불 거부(이미 가이드 지급됨).
```

## 6. API & 보안

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/payments/prepare` | 여행자 | Payment PENDING 생성, merchantUid·결제파라미터 반환 |
| POST | `/api/payments/complete` | 여행자 | 브라우저 콜백. 재조회·검증 후 PAID 확정 |
| POST | `/api/payments/webhook` | **public** | PortOne 웹훅. payload 불신, 재조회로 검증 |
| GET | `/api/payments/booking/{bookingId}` | 참여자 | 예약의 결제 상태 조회 |
| GET | `/api/admin/settlements` | 관리자 | 정산 원장 목록(상태 필터) |
| PATCH | `/api/admin/settlements/{id}/payout` | 관리자 | 지급완료 처리(adminMemo 옵션) |

- 환불은 별도 엔드포인트 대신 기존 `PATCH /api/bookings/{id}/cancel` 경로에 결제취소를 endpoint로 연결.
- **SecurityConfig**: `/api/payments/webhook`만 public 등록. 나머지는 인증. 관리자 경로는 admin 권한.
- 웹훅이 public이어도 안전한 이유: payload를 신뢰하지 않고 PortOne API 재조회로만 확정하기 때문.
- **ENV** (`.env`만, `.env.example`엔 플레이스홀더):
  - `PORTONE_API_SECRET` (V2 REST 인증)
  - `PORTONE_STORE_ID`
  - `PORTONE_CHANNEL_KEY` (결제 채널)
  - `PORTONE_WEBHOOK_SECRET` (웹훅 서명 검증, V2 지원 시)
  - 프론트 `.env.local`: `NEXT_PUBLIC_PORTONE_STORE_ID`, `NEXT_PUBLIC_PORTONE_CHANNEL_KEY`
- 키 미설정 시 기동 실패 금지 — Kakao/Google 클라이언트 패턴대로 degrade(결제 비활성), 예약 흐름은 정상.

## 7. 백엔드 컴포넌트

- `Payment`, `PaymentStatus`, `PaymentRepository`
- `Settlement`, `SettlementStatus`, `SettlementRepository`
- `PortOneClient` — V2 REST 래퍼 (결제 재조회 `GET /payments/{id}`, 결제취소 `POST /payments/{id}/cancel`).
  `KakaoLocalClient`/`GoogleTranslateClient` 패턴 (WebClient/RestClient, 키 없으면 비활성).
- `PaymentService` — prepare / confirm(검증·멱등) / refund. `@Transactional`.
- `SettlementService` — createOnComplete(booking) / listForAdmin / markPaidOut.
- `PaymentController`, `AdminSettlementController` (admin-portal 패턴).
- `BookingService.complete()`에 훅 추가: 완료 시 Settlement 생성 호출.
- `BookingService.cancel()`에 훅 추가: PAID 예약이면 환불 호출.

## 8. 프론트엔드

- **PortOne V2 브라우저 SDK는 CDN `<script>`로 로드** (npm 패키지 무추가 원칙 준수).
  Next.js `<Script>` 또는 결제 시점 동적 로드.
- 예약 상세/목록(`/bookings/[id]`, traveler bookings): ACCEPTED·미결제 → **"결제하기"** 버튼.
  결제창 → 콜백 → 서버 `/complete` → 상태 반영(결제완료 배지).
- 결제 완료/영수증 상태, 취소·환불 버튼(가드 조건 노출).
- 관리자 정산 페이지(admin 포털): 정산 원장 목록 + 지급완료 버튼.
- **i18n**: 신규 결제 문구 전부 ko/en/zh 3언어 필수 (`src/lib/i18n.ts`).
- `lib/api.ts` fetch 래퍼 재사용, 신규 `lib/payment.ts` (prepare→SDK→complete 오케스트레이션).

## 9. 테스트 (이 레포 `src/test` Mockito 패턴)

- **금액 검증**: PortOne 재조회 금액 ≠ Payment.amount → PAID 거부, 예외.
- **멱등성**: 웹훅 2회(또는 콜백+웹훅) → Payment PAID 1회, 상태·정산 중복 없음.
- **정산 계산**: net = gross − round(gross×0.15) 반올림 경계값.
- **환불 가드**: Settlement PAID_OUT 후 환불 시도 → 거부.
- **정산 생성 조건**: 미결제 예약 complete → Settlement 미생성.
- `PortOneClient`는 목킹 (외부 호출 없이 검증 로직만 단위 테스트).

## 10. 마이그레이션 / 운영 주의

- `ddl-auto: update`로 `payments`·`settlements` 테이블 자동 생성. 컬럼 드롭 없음.
- 기존 예약(결제 개념 이전)은 Payment 없음 → "미결제"로 표시, 정산 대상 아님.
- 프로덕션 웹훅은 공개 URL 필요 (로컬 개발은 PortOne 콘솔 테스트 결제 / 콜백 위주).
- PortOne 콘솔: V2 채널 설정, 웹훅 URL 등록, 테스트 모드 키.

## 11. 열린 질문 (구현 중 확정)

- PortOne V2 웹훅 서명 검증 방식(V2 시그니처 헤더) — 클라이언트 구현 시 문서 확인.
- 관리자 권한 판별 방식은 admin-portal 브랜치 패턴 재사용 (해당 브랜치 머지 상태 확인 필요).
