# 결제 시스템 (에스크로 + PortOne V2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 수락된 예약을 여행자가 PortOne V2로 선결제(에스크로)하고, 일정 완료 시 수수료 15%를 뗀 정산액을 수동 원장으로 가이드에게 지급하는 결제 시스템을 만든다.

**Architecture:** 결제 상태는 `BookingStatus`와 직교하는 별도 `Payment`/`Settlement` 엔티티로 관리한다. 결제 확정은 브라우저 콜백을 신뢰하지 않고 서버가 PortOne API를 재조회해 금액을 검증한 뒤에만 이뤄지며, 웹훅이 진실의 원천이고 PortOne 결제 id의 unique 제약으로 멱등성을 보장한다. 정산은 완료(COMPLETED) 시 생성되는 수동 원장이고 관리자가 지급완료 처리한다.

**Tech Stack:** Spring Boot 3.3.5 / Java 21 / JPA(`ddl-auto: update`) / `RestClient`(PortOne V2 REST) / JUnit5 + Mockito(단위 테스트) / Next.js 15 + PortOne V2 브라우저 SDK(CDN).

## Global Constraints

- **금액은 전부 KRW 정수(`Integer`).** PortOne은 원화 청구, 외국인 카드가 FX 처리.
- **`BookingStatus` enum은 절대 수정하지 않는다.** PAID를 넣지 않는다. "결제됨" = `Payment` 행이 PAID.
- **서버측 금액 검증 필수.** 브라우저가 준 값을 신뢰하지 말고 PortOne API 재조회로 `실제금액 == Payment.amount` 확인 후에만 PAID.
- **멱등성.** PortOne 결제 id(`portoneUid`)에 DB unique 제약. 콜백·웹훅 중복 발화해도 PAID 1회.
- **수수료율 스냅샷.** `Settlement.commissionRate`를 정산 생성 시점에 복사 저장. 설정값 `payment.commission-rate:0.15`.
- **환불 가드.** `Payment` PAID이고 `Settlement`가 PAID_OUT 아닐 때만 환불 허용.
- **키 없으면 degrade, 기동 실패 금지.** `KakaoLocalClient` 패턴대로 `isEnabled()` false면 결제 비활성, 예약 흐름은 정상.
- **npm 패키지 무추가.** PortOne 브라우저 SDK는 CDN `<script>`로 로드.
- **i18n 3언어 필수.** 신규 프론트 문구는 `src/lib/i18n.ts`에 ko/en/zh 전부.
- **`.env` 커밋 금지.** 키는 `.env`에만, `.env.example`엔 플레이스홀더.
- **원격 DB N+1 금지.** 예약 목록 DTO에 결제 조회를 걸지 않는다. 결제 상태는 예약 상세 전용 엔드포인트로 단건 조회.

**Java 백엔드 패키지 루트:** `app/backend/src/main/java/com/guidematch/`
**테스트 실행:** `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests '<FQCN>'`
**전체 빌드(부트jar 제외 빠른 컴파일):** `gradle build -x bootJar`

---

## File Structure

**신규 백엔드 패키지 `com.guidematch.payment`:**
- `Payment.java` — 결제 엔티티(돈 들어옴)
- `PaymentStatus.java` — enum PENDING/PAID/FAILED/REFUNDED
- `PaymentRepository.java`
- `Settlement.java` — 정산 엔티티(돈 나감)
- `SettlementStatus.java` — enum PENDING/PAID_OUT
- `SettlementRepository.java`
- `PortOneClient.java` — PortOne V2 REST 래퍼(재조회·취소)
- `PaymentService.java` — prepare/confirm/refund
- `SettlementService.java` — createOnComplete/list/markPaidOut
- `PaymentController.java` — /api/payments/**
- `AdminSettlementController.java` — /api/admin/settlements/**
- `dto/PreparePaymentRequest.java`, `dto/PreparePaymentResponse.java`, `dto/ConfirmPaymentRequest.java`, `dto/PaymentStatusResponse.java`, `dto/SettlementRow.java`, `dto/PayoutRequest.java`

**수정 백엔드:**
- `booking/BookingService.java` — `complete()`에 정산 생성 훅, `cancel()`에 환불 훅
- `config/SecurityConfig.java` — `/api/payments/webhook` public 등록
- `.env.example` — PortOne 플레이스홀더

**신규/수정 프론트(`app/frontend/src/`):**
- `lib/payment.ts` — prepare→SDK→complete 오케스트레이션
- `app/bookings/[id]/page.tsx` (또는 예약 상세 컴포넌트) — 결제하기 버튼 + 상태
- `app/admin/settlements/page.tsx` — 정산 원장(관리자)
- `lib/i18n.ts` — 결제 문구 3언어
- `.env.local` 예시(문서화)

**신규 테스트:**
- `src/test/java/com/guidematch/payment/PaymentServiceTest.java`
- `src/test/java/com/guidematch/payment/SettlementServiceTest.java`

---

## Task 1: Payment·Settlement 엔티티 + 리포지토리 + enum

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/payment/PaymentStatus.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/Payment.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/PaymentRepository.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/SettlementStatus.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/Settlement.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/SettlementRepository.java`

**Interfaces:**
- Produces:
  - `Payment(Long bookingId, String merchantUid, Integer amount, String currency)` 생성자 → status PENDING, createdAt 자동
  - `Payment#markPaid(String portoneUid)`, `Payment#markRefunded()`, `Payment#markFailed()`
  - getter: `getId/getBookingId/getPortoneUid/getMerchantUid/getAmount/getCurrency/getStatus/getPaidAt/getRefundedAt`
  - `PaymentStatus{PENDING,PAID,FAILED,REFUNDED}`
  - `Settlement(Long bookingId, Long guideProfileId, Integer grossAmount, double commissionRate)` → commissionAmount/netAmount 계산, status PENDING
  - `Settlement#markPaidOut(String adminMemo)`; getter: `getId/getBookingId/getGuideProfileId/getGrossAmount/getCommissionRate/getCommissionAmount/getNetAmount/getStatus/getPaidOutAt/getAdminMemo/getCreatedAt`
  - `SettlementStatus{PENDING,PAID_OUT}`
  - `PaymentRepository.findByBookingId(Long) : Optional<Payment>`, `existsByPortoneUid(String) : boolean`, `findByPortoneUid(String) : Optional<Payment>`, `findByMerchantUid(String) : Optional<Payment>`
  - `SettlementRepository.findByBookingId(Long) : Optional<Settlement>`, `findByStatus(SettlementStatus) : List<Settlement>`, `findAllByOrderByCreatedAtDesc() : List<Settlement>`

- [ ] **Step 1: PaymentStatus enum 작성**

```java
package com.guidematch.payment;

/** 결제 상태. PENDING(결제창 준비) → PAID(검증완료) / FAILED / REFUNDED(전액취소). */
public enum PaymentStatus {
    PENDING, PAID, FAILED, REFUNDED
}
```

- [ ] **Step 2: SettlementStatus enum 작성**

```java
package com.guidematch.payment;

/** 정산 상태. PENDING(지급 대기) → PAID_OUT(관리자 이체 완료). */
public enum SettlementStatus {
    PENDING, PAID_OUT
}
```

- [ ] **Step 3: Payment 엔티티 작성**

```java
package com.guidematch.payment;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 결제 한 건 (에스크로: 여행자→플랫폼). 예약당 1건.
 * BookingStatus와 직교 — "결제됨"은 이 행이 PAID인 것으로 판단한다.
 */
@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_booking", columnNames = "booking_id"),
        @UniqueConstraint(name = "uk_payments_portone_uid", columnNames = "portone_uid"),
        @UniqueConstraint(name = "uk_payments_merchant_uid", columnNames = "merchant_uid")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** 우리가 만든 주문번호. 결제창에 넘기고, 콜백/웹훅에서 매칭 키로 쓴다. */
    @Column(name = "merchant_uid", nullable = false)
    private String merchantUid;

    /** PortOne 결제 id. 결제 확정 시 채워진다. 멱등 키(unique). */
    @Column(name = "portone_uid")
    private String portoneUid;

    /** 결제 금액(KRW). booking.totalPrice 스냅샷. */
    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = PaymentStatus.PENDING;
    }

    protected Payment() {}

    public Payment(Long bookingId, String merchantUid, Integer amount, String currency) {
        this.bookingId = bookingId;
        this.merchantUid = merchantUid;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
    }

    /** 서버 검증 통과 후 PAID로 확정. 멱등: 이미 PAID면 아무것도 하지 않는다. */
    public void markPaid(String portoneUid) {
        if (this.status == PaymentStatus.PAID) return;
        this.portoneUid = portoneUid;
        this.status = PaymentStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = Instant.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getMerchantUid() { return merchantUid; }
    public String getPortoneUid() { return portoneUid; }
    public Integer getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: Settlement 엔티티 작성**

```java
package com.guidematch.payment;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 정산 한 건 (플랫폼→가이드). 예약당 1건, COMPLETED 시 생성.
 * 수수료율은 생성 시점 스냅샷(hourlyRateSnapshot 철학) — 이후 요율 변경에 영향받지 않는다.
 */
@Entity
@Table(name = "settlements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlements_booking", columnNames = "booking_id")
})
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    /** 총액 = booking.totalPrice */
    @Column(name = "gross_amount", nullable = false)
    private Integer grossAmount;

    /** 정산 시점 수수료율 스냅샷 (예: 0.15) */
    @Column(name = "commission_rate", nullable = false)
    private double commissionRate;

    /** 수수료 = round(gross × rate) */
    @Column(name = "commission_amount", nullable = false)
    private Integer commissionAmount;

    /** 가이드 수령액 = gross − commission */
    @Column(name = "net_amount", nullable = false)
    private Integer netAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    @Column(name = "admin_memo")
    private String adminMemo;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = SettlementStatus.PENDING;
    }

    protected Settlement() {}

    public Settlement(Long bookingId, Long guideProfileId, Integer grossAmount, double commissionRate) {
        this.bookingId = bookingId;
        this.guideProfileId = guideProfileId;
        this.grossAmount = grossAmount;
        this.commissionRate = commissionRate;
        this.commissionAmount = (int) Math.round(grossAmount * commissionRate);
        this.netAmount = grossAmount - this.commissionAmount;
        this.status = SettlementStatus.PENDING;
    }

    public void markPaidOut(String adminMemo) {
        this.status = SettlementStatus.PAID_OUT;
        this.paidOutAt = Instant.now();
        this.adminMemo = adminMemo;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Long getGuideProfileId() { return guideProfileId; }
    public Integer getGrossAmount() { return grossAmount; }
    public double getCommissionRate() { return commissionRate; }
    public Integer getCommissionAmount() { return commissionAmount; }
    public Integer getNetAmount() { return netAmount; }
    public SettlementStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidOutAt() { return paidOutAt; }
    public String getAdminMemo() { return adminMemo; }
}
```

- [ ] **Step 5: 리포지토리 2개 작성**

```java
package com.guidematch.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingId(Long bookingId);
    Optional<Payment> findByPortoneUid(String portoneUid);
    Optional<Payment> findByMerchantUid(String merchantUid);
    boolean existsByPortoneUid(String portoneUid);
}
```

```java
package com.guidematch.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByBookingId(Long bookingId);
    List<Settlement> findByStatus(SettlementStatus status);
    List<Settlement> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/backend/src/main/java/com/guidematch/payment/
git commit -m "feat(payment): Payment·Settlement 엔티티 + 리포지토리 (에스크로 데이터 모델)"
```

---

## Task 2: PortOneClient (V2 REST 래퍼)

PortOne V2는 REST 인증에 API secret으로 access token을 발급받아 `Authorization: Bearer` 헤더로 호출한다. 이 태스크는 결제 재조회와 결제취소만 감싼다. 키가 없으면 `isEnabled()==false`로 비활성.

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/payment/PortOneClient.java`

**Interfaces:**
- Produces:
  - `PortOneClient(String apiSecret)` (`@Value("${portone.api-secret:}")`)
  - `boolean isEnabled()`
  - `PortOnePayment getPayment(String paymentId)` — PortOne에서 실제 결제 조회. 실패/미설정 시 null.
  - `void cancelPayment(String paymentId, String reason)` — 전액 취소. 실패 시 예외.
  - `record PortOnePayment(String status, long amount, String currency)` — status 예: "PAID","CANCELLED"; amount=총 결제금액(KRW 정수)
- Consumes: 없음(외부 API)

- [ ] **Step 1: PortOneClient 작성**

```java
package com.guidematch.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * PortOne V2 REST 클라이언트. 결제 재조회(서버 검증용)·전액 취소만 감싼다.
 * apiSecret(.env PORTONE_API_SECRET)로 매 호출 시 PortOne 인증 헤더를 만든다.
 * 키가 없으면 isEnabled()==false — 결제 기능 비활성, 앱은 정상 부팅.
 */
@Component
public class PortOneClient {

    private static final Logger log = LoggerFactory.getLogger(PortOneClient.class);
    private static final String BASE = "https://api.portone.io";

    private final RestClient restClient = RestClient.create();
    private final String apiSecret;

    public PortOneClient(@Value("${portone.api-secret:}") String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public boolean isEnabled() {
        return apiSecret != null && !apiSecret.isBlank();
    }

    /** PortOne V2 인증 헤더: "PortOne {apiSecret}". */
    private String authHeader() {
        return "PortOne " + apiSecret;
    }

    /** 결제 단건 재조회. 서버 금액 검증의 유일한 신뢰 소스. 실패/미설정이면 null. */
    public PortOnePayment getPayment(String paymentId) {
        if (!isEnabled()) return null;
        try {
            PaymentBody body = restClient.get()
                    .uri(BASE + "/payments/{paymentId}", paymentId)
                    .header("Authorization", authHeader())
                    .retrieve()
                    .body(PaymentBody.class);
            if (body == null || body.amount == null) return null;
            return new PortOnePayment(body.status, body.amount.total, body.currency);
        } catch (Exception e) {
            log.warn("PortOne getPayment 실패 paymentId={}", paymentId, e);
            return null;
        }
    }

    /** 전액 취소(환불). 실패 시 예외를 던져 호출자가 트랜잭션을 롤백하게 한다. */
    public void cancelPayment(String paymentId, String reason) {
        if (!isEnabled()) throw new IllegalStateException("PortOne 미설정 — 환불 불가");
        restClient.post()
                .uri(BASE + "/payments/{paymentId}/cancel", paymentId)
                .header("Authorization", authHeader())
                .body(Map.of("reason", reason == null ? "예약 취소" : reason))
                .retrieve()
                .toBodilessEntity();
    }

    /** 서버 검증에 필요한 최소 필드만. */
    public record PortOnePayment(String status, long amount, String currency) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PaymentBody {
        public String status;      // "PAID", "CANCELLED", "FAILED", ...
        public String currency;    // "KRW"
        public Amount amount;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Amount {
        public long total;         // 총 결제금액
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/backend/src/main/java/com/guidematch/payment/PortOneClient.java
git commit -m "feat(payment): PortOne V2 REST 클라이언트 (결제 재조회·취소, 키없으면 비활성)"
```

---

## Task 3: PaymentService.prepare + 결제 준비 API

여행자가 "결제하기"를 누르면 서버가 예약 소유·상태(ACCEPTED)·미결제를 검증하고 `Payment` PENDING을 만든 뒤 `merchantUid`와 금액을 돌려준다. 프론트는 이 값으로 PortOne 결제창을 연다.

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/payment/dto/PreparePaymentRequest.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/dto/PreparePaymentResponse.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/PaymentService.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/PaymentController.java`
- Test: `app/backend/src/test/java/com/guidematch/payment/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `BookingRepository.findById`, `Booking#getTravelerId/getGuideProfileId/getStatus/getTotalPrice/getCurrency`, `BookingStatus.ACCEPTED`, `PaymentRepository`, `PortOneClient`
- Produces:
  - `PaymentService(BookingRepository, PaymentRepository, SettlementRepository, PortOneClient, GuideProfileService, @Value commissionRate)`
  - `PreparePaymentResponse prepare(Long travelerId, Long bookingId)`
  - `record PreparePaymentRequest(Long bookingId)`
  - `record PreparePaymentResponse(String merchantUid, Integer amount, String currency)`

- [ ] **Step 1: DTO 2개 작성**

```java
package com.guidematch.payment.dto;

public record PreparePaymentRequest(Long bookingId) {}
```

```java
package com.guidematch.payment.dto;

/** 결제창에 넘길 값. storeId/channelKey는 프론트가 NEXT_PUBLIC env로 이미 가지므로 서버는 주문번호·금액만 준다. */
public record PreparePaymentResponse(String merchantUid, Integer amount, String currency) {}
```

- [ ] **Step 2: 실패 테스트 작성 — 남의 예약/미수락/이미결제 검증**

```java
package com.guidematch.payment;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.payment.dto.PreparePaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SettlementRepository settlementRepository;
    @Mock PortOneClient portOneClient;
    @Mock GuideProfileService guideProfileService;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(bookingRepository, paymentRepository, settlementRepository,
                portOneClient, guideProfileService, 0.15);
    }

    /** 필드 세팅용 booking 헬퍼. 생성자 대신 리플렉션으로 최소 필드만 채운다. */
    private Booking booking(Long id, Long travelerId, BookingStatus status, int total) {
        Booking b = new Booking();
        ReflectionTestUtils.setField(b, "id", id);
        ReflectionTestUtils.setField(b, "travelerId", travelerId);
        ReflectionTestUtils.setField(b, "guideProfileId", 7L);
        ReflectionTestUtils.setField(b, "status", status);
        ReflectionTestUtils.setField(b, "totalPrice", total);
        ReflectionTestUtils.setField(b, "currency", "KRW");
        return b;
    }

    @Test
    void 남의_예약_결제준비는_예외() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 99L, BookingStatus.ACCEPTED, 50000)));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(1L, 1L));
    }

    @Test
    void 미수락_예약_결제준비는_예외() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.REQUESTED, 50000)));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(1L, 1L));
    }

    @Test
    void 이미_결제된_예약은_예외() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.ACCEPTED, 50000)));
        Payment paid = new Payment(1L, "m-1", 50000, "KRW");
        paid.markPaid("imp_x");
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(paid));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(1L, 1L));
    }

    @Test
    void 정상_결제준비는_PENDING_생성_후_금액반환() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.ACCEPTED, 50000)));
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PreparePaymentResponse res = service.prepare(1L, 1L);

        assertEquals(50000, res.amount());
        assertEquals("KRW", res.currency());
        assertNotNull(res.merchantUid());
        verify(paymentRepository).save(any(Payment.class));
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.PaymentServiceTest'`
Expected: 컴파일 에러(PaymentService 없음) 또는 FAIL

- [ ] **Step 4: PaymentService 작성 (prepare만)**

```java
package com.guidematch.payment;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.payment.dto.PreparePaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final PortOneClient portOneClient;
    private final GuideProfileService guideProfileService;
    private final double commissionRate;

    public PaymentService(BookingRepository bookingRepository,
                          PaymentRepository paymentRepository,
                          SettlementRepository settlementRepository,
                          PortOneClient portOneClient,
                          GuideProfileService guideProfileService,
                          @Value("${payment.commission-rate:0.15}") double commissionRate) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.settlementRepository = settlementRepository;
        this.portOneClient = portOneClient;
        this.guideProfileService = guideProfileService;
        this.commissionRate = commissionRate;
    }

    /** 여행자가 결제창을 열기 전 서버 준비. 소유·ACCEPTED·미결제 검증 후 Payment PENDING 생성. */
    @Transactional
    public PreparePaymentResponse prepare(Long travelerId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        if (!booking.getTravelerId().equals(travelerId)) {
            throw new IllegalArgumentException("본인의 예약만 결제할 수 있습니다.");
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new IllegalArgumentException("수락된 예약만 결제할 수 있습니다.");
        }
        Optional<Payment> existing = paymentRepository.findByBookingId(bookingId);
        if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("이미 결제된 예약입니다.");
        }

        // 재시도(PENDING 잔재)면 그대로 재사용, 없으면 새로 만든다.
        Payment payment = existing.orElseGet(() -> {
            String merchantUid = "booking-" + bookingId + "-" + System.currentTimeMillis();
            return paymentRepository.save(
                    new Payment(bookingId, merchantUid, booking.getTotalPrice(), booking.getCurrency()));
        });

        return new PreparePaymentResponse(payment.getMerchantUid(), payment.getAmount(), payment.getCurrency());
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.PaymentServiceTest'`
Expected: PASS (4 tests)

- [ ] **Step 6: PaymentController 작성 (prepare 엔드포인트만)**

```java
package com.guidematch.payment;

import com.guidematch.payment.dto.PreparePaymentRequest;
import com.guidematch.payment.dto.PreparePaymentResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 결제 API. webhook 외에는 로그인 필요. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 여행자: 결제 준비 (결제창 오픈 직전) */
    @PostMapping("/prepare")
    public PreparePaymentResponse prepare(@AuthenticationPrincipal Long userId,
                                          @RequestBody PreparePaymentRequest request) {
        return paymentService.prepare(userId, request.bookingId());
    }
}
```

- [ ] **Step 7: 컴파일 + Commit**

```bash
cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava
git add app/backend/src/main/java/com/guidematch/payment/ app/backend/src/test/java/com/guidematch/payment/
git commit -m "feat(payment): 결제 준비(prepare) — 소유·ACCEPTED·미결제 검증 + PENDING 생성"
```

---

## Task 4: PaymentService.confirm — 서버 금액 검증 + 멱등 PAID

이 태스크가 결제 시스템의 심장이다. 브라우저 콜백/웹훅 어느 쪽이든 `confirm(portoneUid, merchantUid)`를 호출하고, 서버는 PortOne에서 실제 결제를 재조회해 **금액이 일치하고 status가 PAID일 때만** 우리 Payment를 PAID로 만든다. 멱등.

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/payment/PaymentService.java`
- Test: `app/backend/src/test/java/com/guidematch/payment/PaymentServiceTest.java` (테스트 추가)

**Interfaces:**
- Consumes: `PortOneClient.getPayment(String) : PortOnePayment`
- Produces: `PaymentService.confirm(String portoneUid, String merchantUid) : void` (검증 실패 시 `IllegalArgumentException`)

- [ ] **Step 1: confirm 테스트 추가 (금액검증·멱등·상태검증)**

`PaymentServiceTest`에 아래 테스트들을 추가한다.

```java
    // ── confirm: 서버 금액 검증 + 멱등 ──

    @Test
    void 금액_불일치는_PAID_거부() {
        Payment pending = new Payment(1L, "m-1", 50000, "KRW");
        when(paymentRepository.findByMerchantUid("m-1")).thenReturn(Optional.of(pending));
        // PortOne이 알려준 실제 결제금액이 1000원 (조작 시도)
        when(portOneClient.getPayment("imp_x"))
                .thenReturn(new PortOneClient.PortOnePayment("PAID", 1000, "KRW"));

        assertThrows(IllegalArgumentException.class, () -> service.confirm("imp_x", "m-1"));
        assertEquals(PaymentStatus.PENDING, pending.getStatus());
    }

    @Test
    void PortOne상태가_PAID아니면_거부() {
        Payment pending = new Payment(1L, "m-1", 50000, "KRW");
        when(paymentRepository.findByMerchantUid("m-1")).thenReturn(Optional.of(pending));
        when(portOneClient.getPayment("imp_x"))
                .thenReturn(new PortOneClient.PortOnePayment("FAILED", 50000, "KRW"));

        assertThrows(IllegalArgumentException.class, () -> service.confirm("imp_x", "m-1"));
        assertEquals(PaymentStatus.PENDING, pending.getStatus());
    }

    @Test
    void 검증통과하면_PAID_확정() {
        Payment pending = new Payment(1L, "m-1", 50000, "KRW");
        when(paymentRepository.findByMerchantUid("m-1")).thenReturn(Optional.of(pending));
        when(portOneClient.getPayment("imp_x"))
                .thenReturn(new PortOneClient.PortOnePayment("PAID", 50000, "KRW"));

        service.confirm("imp_x", "m-1");

        assertEquals(PaymentStatus.PAID, pending.getStatus());
        assertEquals("imp_x", pending.getPortoneUid());
    }

    @Test
    void 웹훅_중복발화는_한번만_PAID_멱등() {
        Payment pending = new Payment(1L, "m-1", 50000, "KRW");
        when(paymentRepository.findByMerchantUid("m-1")).thenReturn(Optional.of(pending));
        when(portOneClient.getPayment("imp_x"))
                .thenReturn(new PortOneClient.PortOnePayment("PAID", 50000, "KRW"));

        service.confirm("imp_x", "m-1");   // 콜백
        service.confirm("imp_x", "m-1");   // 웹훅 (중복)

        assertEquals(PaymentStatus.PAID, pending.getStatus());
        assertNotNull(pending.getPaidAt());
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.PaymentServiceTest'`
Expected: 컴파일 에러(confirm 없음)

- [ ] **Step 3: confirm 구현 추가**

`PaymentService`에 아래 메서드를 추가한다.

```java
    /**
     * 결제 확정 — 콜백/웹훅 공통 진입점. 멱등.
     * PortOne에서 실제 결제를 재조회해 금액·상태를 검증한 뒤에만 PAID로 만든다.
     * 브라우저가 준 값은 매칭 키(merchantUid, portoneUid)로만 쓰고 금액은 절대 신뢰하지 않는다.
     */
    @Transactional
    public void confirm(String portoneUid, String merchantUid) {
        Payment payment = paymentRepository.findByMerchantUid(merchantUid)
                .orElseThrow(() -> new IllegalArgumentException("결제 주문을 찾을 수 없습니다: " + merchantUid));

        // 멱등: 이미 PAID면 재검증 없이 통과(콜백·웹훅 중복 발화 대비).
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        PortOneClient.PortOnePayment actual = portOneClient.getPayment(portoneUid);
        if (actual == null) {
            throw new IllegalArgumentException("PortOne 결제 조회 실패: " + portoneUid);
        }
        if (!"PAID".equalsIgnoreCase(actual.status())) {
            throw new IllegalArgumentException("결제가 완료되지 않았습니다. status=" + actual.status());
        }
        if (actual.amount() != payment.getAmount().longValue()) {
            log.error("결제 금액 불일치! 예상={} 실제={} merchantUid={}",
                    payment.getAmount(), actual.amount(), merchantUid);
            throw new IllegalArgumentException("결제 금액이 예약 금액과 일치하지 않습니다.");
        }

        payment.markPaid(portoneUid);
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.PaymentServiceTest'`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add app/backend/src/main/java/com/guidematch/payment/PaymentService.java app/backend/src/test/java/com/guidematch/payment/PaymentServiceTest.java
git commit -m "feat(payment): 결제 확정 confirm — 서버 금액검증 + 멱등 PAID (콜백·웹훅 공통)"
```

---

## Task 5: 결제 콜백·웹훅 엔드포인트 + Security 공개 등록 + 상태조회

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/payment/dto/ConfirmPaymentRequest.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/dto/PaymentStatusResponse.java`
- Modify: `app/backend/src/main/java/com/guidematch/payment/PaymentController.java`
- Modify: `app/backend/src/main/java/com/guidematch/payment/PaymentService.java` (상태조회 메서드)
- Modify: `app/backend/src/main/java/com/guidematch/config/SecurityConfig.java`

**Interfaces:**
- Consumes: `PaymentService.confirm`, `PaymentRepository.findByBookingId`
- Produces:
  - `record ConfirmPaymentRequest(String paymentId, String merchantUid)` (paymentId = PortOne 결제 id = portoneUid)
  - `record PaymentStatusResponse(String status, Integer amount, String currency)` (status: NONE|PENDING|PAID|FAILED|REFUNDED)
  - `PaymentService.statusForBooking(Long userId, Long bookingId) : PaymentStatusResponse`

- [ ] **Step 1: DTO 2개 작성**

```java
package com.guidematch.payment.dto;

/** 브라우저 콜백 본문. paymentId는 PortOne이 발급한 결제 id. */
public record ConfirmPaymentRequest(String paymentId, String merchantUid) {}
```

```java
package com.guidematch.payment.dto;

/** 예약 상세용 결제 상태. 결제 이력 없으면 status="NONE". */
public record PaymentStatusResponse(String status, Integer amount, String currency) {}
```

- [ ] **Step 2: PaymentService에 statusForBooking 추가**

```java
    /** 예약 상세용 결제 상태 조회. 참여자(여행자/가이드)만. 결제 없으면 NONE. */
    @Transactional(readOnly = true)
    public com.guidematch.payment.dto.PaymentStatusResponse statusForBooking(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        boolean isTraveler = booking.getTravelerId().equals(userId);
        boolean isGuide = guideProfileService.getById(booking.getGuideProfileId()).getUserId().equals(userId);
        if (!isTraveler && !isGuide) {
            throw new IllegalArgumentException("이 예약을 조회할 권한이 없습니다.");
        }
        return paymentRepository.findByBookingId(bookingId)
                .map(p -> new com.guidematch.payment.dto.PaymentStatusResponse(
                        p.getStatus().name(), p.getAmount(), p.getCurrency()))
                .orElse(new com.guidematch.payment.dto.PaymentStatusResponse("NONE", null, null));
    }
```

- [ ] **Step 3: PaymentController에 complete·webhook·status 추가**

`PaymentController`에 아래 메서드/의존성을 추가한다. `PaymentRepository`는 필요 없다 — 서비스만 주입.

```java
    // (기존 import에 아래 추가)
    // import com.guidematch.payment.dto.ConfirmPaymentRequest;
    // import com.guidematch.payment.dto.PaymentStatusResponse;

    /** 브라우저 콜백: 결제창이 성공을 알리면 프론트가 호출. 서버가 재검증 후 확정. */
    @PostMapping("/complete")
    public void complete(@AuthenticationPrincipal Long userId,
                         @RequestBody ConfirmPaymentRequest request) {
        paymentService.confirm(request.paymentId(), request.merchantUid());
    }

    /**
     * PortOne 웹훅(public). payment_id·merchant_uid만 신뢰 매칭 키로 쓰고,
     * 금액·상태는 confirm 안에서 PortOne 재조회로 검증한다. 멱등.
     */
    @PostMapping("/webhook")
    public void webhook(@RequestBody java.util.Map<String, Object> body) {
        // PortOne V2 웹훅 본문에서 paymentId·merchant_uid를 꺼낸다.
        // 실제 필드 경로는 PortOne 콘솔 웹훅 스펙에 맞춰 조정(열린 질문 §11).
        Object data = body.get("data");
        String paymentId = null, merchantUid = null;
        if (data instanceof java.util.Map<?, ?> d) {
            Object pid = d.get("paymentId");
            paymentId = pid != null ? pid.toString() : null;
            Object mid = d.get("merchantId");   // = merchant_uid 매핑 (스펙 확인)
            merchantUid = mid != null ? mid.toString() : null;
        }
        if (paymentId == null || merchantUid == null) return; // 매칭 불가 → 무시(재발화 대비)
        paymentService.confirm(paymentId, merchantUid);
    }

    /** 예약의 결제 상태 (예약 상세 전용, 단건). */
    @GetMapping("/booking/{bookingId}")
    public PaymentStatusResponse status(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long bookingId) {
        return paymentService.statusForBooking(userId, bookingId);
    }
```

- [ ] **Step 4: SecurityConfig에 webhook public 등록**

`SecurityConfig.java`의 `authorizeHttpRequests` 안, `/api/admin/**` 규칙 **위**에 아래 줄을 추가한다.

```java
                        // PortOne 웹훅 — 페이로드를 신뢰하지 않고 PortOne 재조회로만 확정하므로 public 안전
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
```

- [ ] **Step 5: 컴파일 + 기존 테스트 회귀 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava && gradle test --tests 'com.guidematch.payment.*'`
Expected: BUILD SUCCESSFUL, 테스트 PASS

- [ ] **Step 6: Commit**

```bash
git add app/backend/src/main/java/com/guidematch/payment/ app/backend/src/main/java/com/guidematch/config/SecurityConfig.java
git commit -m "feat(payment): 콜백/웹훅 확정 엔드포인트 + webhook public 등록 + 결제상태 조회"
```

---

## Task 6: 정산 생성 (COMPLETED 훅) + SettlementService

일정이 완료되면(기존 `/bookings/{id}/complete`) Payment가 PAID인 예약에 한해 Settlement PENDING을 만든다. 미결제 예약은 완료돼도 정산을 만들지 않는다.

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/payment/SettlementService.java`
- Modify: `app/backend/src/main/java/com/guidematch/booking/BookingService.java`
- Test: `app/backend/src/test/java/com/guidematch/payment/SettlementServiceTest.java`

**Interfaces:**
- Consumes: `PaymentRepository.findByBookingId`, `SettlementRepository`, `Booking#getGuideProfileId/getTotalPrice`
- Produces:
  - `SettlementService(SettlementRepository, PaymentRepository, @Value commissionRate)`
  - `void createOnComplete(Booking booking)` — 멱등(이미 정산 있으면 no-op), Payment PAID 아니면 no-op
  - `Settlement` 계산: `commissionAmount = round(gross×rate)`, `netAmount = gross − commissionAmount`

- [ ] **Step 1: SettlementServiceTest 작성**

```java
package com.guidematch.payment;

import com.guidematch.booking.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock SettlementRepository settlementRepository;
    @Mock PaymentRepository paymentRepository;

    SettlementService service;

    @BeforeEach
    void setUp() {
        service = new SettlementService(settlementRepository, paymentRepository, 0.15);
    }

    private Booking booking(Long id, int total) {
        Booking b = new Booking();
        ReflectionTestUtils.setField(b, "id", id);
        ReflectionTestUtils.setField(b, "guideProfileId", 7L);
        ReflectionTestUtils.setField(b, "totalPrice", total);
        return b;
    }

    private Payment paidPayment(Long bookingId) {
        Payment p = new Payment(bookingId, "m-" + bookingId, 100000, "KRW");
        p.markPaid("imp_" + bookingId);
        return p;
    }

    @Test
    void 미결제_예약은_정산_미생성() {
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.empty());
        service.createOnComplete(booking(1L, 100000));
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void 이미_정산있으면_멱등_미생성() {
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(paidPayment(1L)));
        when(settlementRepository.findByBookingId(1L)).thenReturn(Optional.of(mock(Settlement.class)));
        service.createOnComplete(booking(1L, 100000));
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void PAID예약은_수수료15퍼센트_떼고_정산생성() {
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(paidPayment(1L)));
        when(settlementRepository.findByBookingId(1L)).thenReturn(Optional.empty());

        service.createOnComplete(booking(1L, 100000));

        ArgumentCaptor<Settlement> cap = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(cap.capture());
        Settlement s = cap.getValue();
        assertEquals(100000, s.getGrossAmount());
        assertEquals(15000, s.getCommissionAmount());
        assertEquals(85000, s.getNetAmount());
        assertEquals(0.15, s.getCommissionRate());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.SettlementServiceTest'`
Expected: 컴파일 에러(SettlementService 없음)

- [ ] **Step 3: SettlementService 작성**

```java
package com.guidematch.payment;

import com.guidematch.booking.Booking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;
    private final double commissionRate;

    public SettlementService(SettlementRepository settlementRepository,
                             PaymentRepository paymentRepository,
                             @Value("${payment.commission-rate:0.15}") double commissionRate) {
        this.settlementRepository = settlementRepository;
        this.paymentRepository = paymentRepository;
        this.commissionRate = commissionRate;
    }

    /**
     * 예약 완료 시 정산 원장 생성. PAID 결제가 있을 때만, 예약당 1회(멱등).
     * 같은 트랜잭션(BookingService.complete) 안에서 호출된다.
     */
    @Transactional
    public void createOnComplete(Booking booking) {
        boolean paid = paymentRepository.findByBookingId(booking.getId())
                .map(p -> p.getStatus() == PaymentStatus.PAID)
                .orElse(false);
        if (!paid) return;
        if (settlementRepository.findByBookingId(booking.getId()).isPresent()) return;

        settlementRepository.save(new Settlement(
                booking.getId(),
                booking.getGuideProfileId(),
                booking.getTotalPrice(),
                commissionRate));
    }

    @Transactional(readOnly = true)
    public List<Settlement> listAll() {
        return settlementRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 관리자 지급완료 처리. */
    @Transactional
    public void markPaidOut(Long settlementId, String adminMemo) {
        Settlement s = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("정산 건을 찾을 수 없습니다."));
        s.markPaidOut(adminMemo);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.SettlementServiceTest'`
Expected: PASS (3 tests)

- [ ] **Step 5: BookingService.complete()에 정산 훅 연결**

`BookingService`에 `SettlementService` 의존성을 추가한다 — 생성자에 파라미터 추가:

```java
    private final SettlementService settlementService;
```

생성자 시그니처와 대입에 `SettlementService settlementService`를 추가한다(마지막 파라미터로). 그리고 import 추가:

```java
import com.guidematch.payment.SettlementService;
```

`complete()` 메서드의 `booking.complete();` 바로 다음 줄에 훅을 넣는다:

```java
        booking.complete();
        settlementService.createOnComplete(booking);   // PAID면 정산 원장 생성(멱등)
        return toResponse(booking);
```

- [ ] **Step 6: 전체 컴파일 + payment 테스트 회귀**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava && gradle test --tests 'com.guidematch.payment.*'`
Expected: BUILD SUCCESSFUL, PASS

- [ ] **Step 7: Commit**

```bash
git add app/backend/src/main/java/com/guidematch/payment/SettlementService.java app/backend/src/main/java/com/guidematch/booking/BookingService.java app/backend/src/test/java/com/guidematch/payment/SettlementServiceTest.java
git commit -m "feat(payment): 완료 시 정산 원장 생성(수수료 15% 스냅샷) + BookingService 훅"
```

---

## Task 7: 환불 — 취소 훅 + 가드

여행자가 PAID 예약을 취소하면 PortOne 전액 취소 후 Payment REFUNDED. 단, Settlement가 PAID_OUT이면(이미 가이드 지급) 환불 거부.

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/payment/PaymentService.java` (refundForBooking)
- Modify: `app/backend/src/main/java/com/guidematch/booking/BookingService.java` (cancel 훅)
- Test: `app/backend/src/test/java/com/guidematch/payment/PaymentServiceTest.java` (추가)

**Interfaces:**
- Consumes: `PortOneClient.cancelPayment`, `SettlementRepository.findByBookingId`, `PaymentRepository.findByBookingId`
- Produces: `PaymentService.refundForBooking(Long bookingId) : void` — Payment 없거나 PAID 아니면 no-op; Settlement PAID_OUT이면 예외

- [ ] **Step 1: 환불 테스트 추가**

`PaymentServiceTest`에 추가:

```java
    // ── refund: 취소 시 환불 + 가드 ──

    @Test
    void 미결제_예약_환불은_noop() {
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.empty());
        service.refundForBooking(1L);
        verify(portOneClient, never()).cancelPayment(any(), any());
    }

    @Test
    void 지급완료된_정산은_환불_거부() {
        Payment paid = new Payment(1L, "m-1", 50000, "KRW");
        paid.markPaid("imp_x");
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(paid));
        Settlement s = new Settlement(1L, 7L, 50000, 0.15);
        s.markPaidOut("이체완료");
        when(settlementRepository.findByBookingId(1L)).thenReturn(Optional.of(s));

        assertThrows(IllegalStateException.class, () -> service.refundForBooking(1L));
        verify(portOneClient, never()).cancelPayment(any(), any());
        assertEquals(PaymentStatus.PAID, paid.getStatus());
    }

    @Test
    void PAID예약_환불은_PortOne취소_후_REFUNDED() {
        Payment paid = new Payment(1L, "m-1", 50000, "KRW");
        paid.markPaid("imp_x");
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(paid));
        when(settlementRepository.findByBookingId(1L)).thenReturn(Optional.empty());

        service.refundForBooking(1L);

        verify(portOneClient).cancelPayment("imp_x", "예약 취소");
        assertEquals(PaymentStatus.REFUNDED, paid.getStatus());
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.PaymentServiceTest'`
Expected: 컴파일 에러(refundForBooking 없음)

- [ ] **Step 3: refundForBooking 구현 추가**

`PaymentService`에 추가:

```java
    /**
     * 예약 취소에 수반되는 환불. PAID 결제가 있고 아직 지급되지 않은 경우에만 PortOne 전액 취소.
     * BookingService.cancel 트랜잭션 안에서 호출된다. cancelPayment 실패 시 예외로 롤백.
     */
    @Transactional
    public void refundForBooking(Long bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PAID) {
            return; // 미결제/이미환불 — 환불할 것 없음
        }
        boolean paidOut = settlementRepository.findByBookingId(bookingId)
                .map(s -> s.getStatus() == SettlementStatus.PAID_OUT)
                .orElse(false);
        if (paidOut) {
            throw new IllegalStateException("이미 가이드에게 정산 지급된 예약은 환불할 수 없습니다.");
        }
        portOneClient.cancelPayment(payment.getPortoneUid(), "예약 취소");
        payment.markRefunded();
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.PaymentServiceTest'`
Expected: PASS (11 tests)

- [ ] **Step 5: BookingService.cancel()에 환불 훅 연결**

`BookingService`에 `PaymentService` 의존성을 추가한다(생성자 파라미터·필드·대입). import:

```java
import com.guidematch.payment.PaymentService;
```

`cancel()` 메서드에서 `booking.cancel();` **앞**에 환불을 호출한다(환불 실패 시 취소도 롤백되도록):

```java
        paymentService.refundForBooking(bookingId);   // PAID면 PortOne 전액취소(가드 포함)
        booking.cancel();
        return toResponse(booking);
```

- [ ] **Step 6: 전체 컴파일 + payment 테스트 회귀**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava && gradle test --tests 'com.guidematch.payment.*'`
Expected: BUILD SUCCESSFUL, PASS

- [ ] **Step 7: Commit**

```bash
git add app/backend/src/main/java/com/guidematch/payment/PaymentService.java app/backend/src/main/java/com/guidematch/booking/BookingService.java app/backend/src/test/java/com/guidematch/payment/PaymentServiceTest.java
git commit -m "feat(payment): 예약 취소 시 전액 환불 + 지급완료 가드 + BookingService 훅"
```

---

## Task 8: 관리자 정산 원장 API

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/payment/dto/SettlementRow.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/dto/PayoutRequest.java`
- Create: `app/backend/src/main/java/com/guidematch/payment/AdminSettlementController.java`

**Interfaces:**
- Consumes: `SettlementService.listAll`, `SettlementService.markPaidOut`
- Produces:
  - `record SettlementRow(Long id, Long bookingId, Long guideProfileId, Integer grossAmount, Integer commissionAmount, Integer netAmount, String status, Instant createdAt, Instant paidOutAt, String adminMemo)`
  - `record PayoutRequest(String adminMemo)`
  - `GET /api/admin/settlements` → `List<SettlementRow>`
  - `PATCH /api/admin/settlements/{id}/payout` (body: PayoutRequest)

- [ ] **Step 1: DTO 작성**

```java
package com.guidematch.payment.dto;

import java.time.Instant;

public record SettlementRow(
        Long id, Long bookingId, Long guideProfileId,
        Integer grossAmount, Integer commissionAmount, Integer netAmount,
        String status, Instant createdAt, Instant paidOutAt, String adminMemo) {}
```

```java
package com.guidematch.payment.dto;

public record PayoutRequest(String adminMemo) {}
```

- [ ] **Step 2: SettlementService에 행 매핑 헬퍼 추가**

`SettlementService`에 추가(import `com.guidematch.payment.dto.SettlementRow`, `java.util.stream.Collectors`):

```java
    @Transactional(readOnly = true)
    public List<com.guidematch.payment.dto.SettlementRow> listRows() {
        return settlementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(s -> new com.guidematch.payment.dto.SettlementRow(
                        s.getId(), s.getBookingId(), s.getGuideProfileId(),
                        s.getGrossAmount(), s.getCommissionAmount(), s.getNetAmount(),
                        s.getStatus().name(), s.getCreatedAt(), s.getPaidOutAt(), s.getAdminMemo()))
                .toList();
    }
```

- [ ] **Step 3: AdminSettlementController 작성**

```java
package com.guidematch.payment;

import com.guidematch.payment.dto.PayoutRequest;
import com.guidematch.payment.dto.SettlementRow;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 관리자 정산 원장. /api/admin/** → hasRole("ADMIN") (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    private final SettlementService settlementService;

    public AdminSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public List<SettlementRow> list() {
        return settlementService.listRows();
    }

    @PatchMapping("/{id}/payout")
    public void payout(@PathVariable Long id, @RequestBody(required = false) PayoutRequest request) {
        settlementService.markPaidOut(id, request != null ? request.adminMemo() : null);
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/backend/src/main/java/com/guidematch/payment/
git commit -m "feat(payment): 관리자 정산 원장 API (목록 + 지급완료 처리)"
```

---

## Task 9: .env.example + application.yml 설정

**Files:**
- Modify: `app/backend/.env.example`
- Modify: `app/backend/src/main/resources/application.yml`

- [ ] **Step 1: application.yml에 PortOne·수수료 설정 매핑 추가**

`application.yml`에서 기존 `kakao`/`google` env 매핑 패턴을 찾아 그 옆에 추가한다(이미 존재하는 키 스타일을 그대로 따른다):

```yaml
portone:
  api-secret: ${PORTONE_API_SECRET:}
  store-id: ${PORTONE_STORE_ID:}
  channel-key: ${PORTONE_CHANNEL_KEY:}
payment:
  commission-rate: ${PAYMENT_COMMISSION_RATE:0.15}
```

- [ ] **Step 2: .env.example에 플레이스홀더 추가**

`app/backend/.env.example` 끝에 추가:

```bash
# PortOne V2 결제 (선택 — 없으면 결제 기능 비활성, 예약 흐름은 정상)
PORTONE_API_SECRET=
PORTONE_STORE_ID=
PORTONE_CHANNEL_KEY=
# 플랫폼 수수료율 (기본 0.15 = 15%)
PAYMENT_COMMISSION_RATE=0.15
```

- [ ] **Step 3: 기동 회귀 확인 (키 없이도 부팅)**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.payment.*'`
Expected: PASS (키 없이 단위 테스트 통과 — degrade 동작 확인은 통합 스모크에서)

- [ ] **Step 4: Commit**

```bash
git add app/backend/.env.example app/backend/src/main/resources/application.yml
git commit -m "chore(payment): PortOne·수수료 설정 매핑 + .env.example 플레이스홀더"
```

---

## Task 10: 프론트 결제 오케스트레이션 (lib/payment.ts + SDK 로더)

PortOne V2 브라우저 SDK를 CDN으로 로드하고, prepare→결제창→complete를 잇는 함수를 만든다.

**Files:**
- Create: `app/frontend/src/lib/payment.ts`

**Interfaces:**
- Consumes: `lib/api.ts`의 fetch 래퍼(`apiFetch`/`getToken` — 실제 export 이름은 기존 파일 확인 후 맞춘다)
- Produces:
  - `loadPortOneSdk() : Promise<void>` — window.PortOne 준비
  - `payForBooking(bookingId: number) : Promise<'PAID' | 'FAILED'>`
  - `getPaymentStatus(bookingId: number) : Promise<{status: string; amount: number|null; currency: string|null}>`

- [ ] **Step 1: lib/api.ts의 export 확인**

Run: `grep -nE "export (async )?function|export const" app/frontend/src/lib/api.ts | head -30`
Expected: fetch 래퍼·토큰 헬퍼 이름 확인(예: `api`, `apiFetch`, `getToken`). 아래 코드의 `apiFetch`를 실제 이름으로 맞춘다.

- [ ] **Step 2: lib/payment.ts 작성**

`NEXT_PUBLIC_PORTONE_STORE_ID`, `NEXT_PUBLIC_PORTONE_CHANNEL_KEY`를 사용한다. `apiFetch`는 Step 1에서 확인한 실제 래퍼로 교체한다.

```ts
import { apiFetch } from "./api"; // ← Step 1에서 확인한 실제 export로 맞출 것

const SDK_URL = "https://cdn.portone.io/v2/browser-sdk.js";

/** PortOne V2 브라우저 SDK를 1회만 로드한다. */
export function loadPortOneSdk(): Promise<void> {
  if (typeof window === "undefined") return Promise.resolve();
  if ((window as any).PortOne) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${SDK_URL}"]`);
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("PortOne SDK 로드 실패")));
      return;
    }
    const s = document.createElement("script");
    s.src = SDK_URL;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error("PortOne SDK 로드 실패"));
    document.head.appendChild(s);
  });
}

type PrepareResponse = { merchantUid: string; amount: number; currency: string };

/**
 * 결제 전체 흐름:
 * 1) 서버 prepare → merchantUid·금액
 * 2) PortOne 결제창(requestPayment)
 * 3) 서버 complete로 재검증·확정
 * 반환: 최종 'PAID' | 'FAILED'
 */
export async function payForBooking(bookingId: number): Promise<"PAID" | "FAILED"> {
  const prep: PrepareResponse = await apiFetch("/api/payments/prepare", {
    method: "POST",
    body: JSON.stringify({ bookingId }),
  });

  await loadPortOneSdk();
  const PortOne = (window as any).PortOne;

  const result = await PortOne.requestPayment({
    storeId: process.env.NEXT_PUBLIC_PORTONE_STORE_ID,
    channelKey: process.env.NEXT_PUBLIC_PORTONE_CHANNEL_KEY,
    paymentId: prep.merchantUid,             // V2는 paymentId가 주문 식별자
    orderName: `PeerUp 예약 #${bookingId}`,
    totalAmount: prep.amount,
    currency: "CURRENCY_KRW",
    payMethod: "CARD",
  });

  if (result.code != null) {
    // 사용자 취소/실패
    return "FAILED";
  }

  // 서버 재검증·확정 (금액은 서버가 PortOne 재조회로 검증)
  await apiFetch("/api/payments/complete", {
    method: "POST",
    body: JSON.stringify({ paymentId: result.paymentId, merchantUid: prep.merchantUid }),
  });

  return "PAID";
}

export async function getPaymentStatus(bookingId: number): Promise<{
  status: string;
  amount: number | null;
  currency: string | null;
}> {
  return apiFetch(`/api/payments/booking/${bookingId}`, { method: "GET" });
}
```

- [ ] **Step 3: 타입 체크**

Run: `cd app/frontend && npx tsc --noEmit`
Expected: 에러 0 (기존 무관 경고 제외). `apiFetch` 이름/시그니처 불일치면 Step 1 확인값으로 수정.

- [ ] **Step 4: Commit**

```bash
git add app/frontend/src/lib/payment.ts
git commit -m "feat(payment): 프론트 결제 오케스트레이션(lib/payment) + PortOne V2 SDK CDN 로더"
```

---

## Task 11: 예약 상세에 "결제하기" 버튼 + 결제 상태 표시

**Files:**
- Modify: `app/frontend/src/app/bookings/[id]/page.tsx` (예약 상세 — 실제 경로는 아래 Step 1로 확인)
- Modify: `app/frontend/src/lib/i18n.ts` (Task 12에서 키 추가 — 이 태스크는 키 사용)

**Interfaces:**
- Consumes: `payForBooking`, `getPaymentStatus` (Task 10), `t()` (LanguageContext)

- [ ] **Step 1: 예약 상세 페이지 경로 확인**

Run: `find app/frontend/src/app -path '*bookings*' -name 'page.tsx'; grep -rln "status.*ACCEPTED\|BookingStatus\|/api/bookings/" app/frontend/src/app | head`
Expected: 예약 상세 파일 경로 확정(계획은 `app/bookings/[id]/page.tsx` 가정 — 다르면 그 파일로).

- [ ] **Step 2: 결제 상태 로드 + 버튼 조건부 렌더 추가**

예약 상세 컴포넌트에 결제 상태 상태변수와 로더를 추가한다. `booking.status === "ACCEPTED"` 이고 결제 상태가 `NONE`/`PENDING`/`FAILED`일 때만 결제 버튼을 보이고, `PAID`면 완료 배지를 보인다. (아래는 삽입할 골격 — 파일의 기존 상태/렌더 구조에 맞춰 배치)

```tsx
import { payForBooking, getPaymentStatus } from "@/lib/payment";
// 컴포넌트 내부:
const [payStatus, setPayStatus] = useState<string>("NONE");
const [paying, setPaying] = useState(false);

useEffect(() => {
  if (!booking?.id) return;
  getPaymentStatus(booking.id).then((r) => setPayStatus(r.status)).catch(() => {});
}, [booking?.id]);

async function handlePay() {
  if (!booking) return;
  setPaying(true);
  try {
    const result = await payForBooking(booking.id);
    if (result === "PAID") {
      setPayStatus("PAID");
    } else {
      alert(t("payment.failed"));
    }
  } catch (e) {
    alert(t("payment.error"));
  } finally {
    setPaying(false);
  }
}

// 렌더(예약 금액 영역 근처):
{booking.status === "ACCEPTED" && payStatus !== "PAID" && (
  <button
    onClick={handlePay}
    disabled={paying}
    className="w-full rounded-xl bg-sky-600 px-4 py-3 font-semibold text-white hover:bg-sky-700 disabled:opacity-60"
  >
    {paying ? t("payment.processing") : t("payment.pay")}
  </button>
)}
{payStatus === "PAID" && (
  <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-3 py-1 text-sm font-medium text-emerald-700">
    ✓ {t("payment.paid")}
  </span>
)}
```

- [ ] **Step 3: 타입 체크 + 린트**

Run: `cd app/frontend && npx tsc --noEmit && npm run lint`
Expected: 에러 0 (i18n 키는 Task 12 완료 후 최종 통과; 순서상 Task 12를 먼저 하거나 이 두 태스크를 함께 커밋)

- [ ] **Step 4: Commit**

```bash
git add app/frontend/src/app
git commit -m "feat(payment): 예약 상세 결제하기 버튼 + 결제 상태 배지"
```

---

## Task 12: i18n 결제 문구 (ko/en/zh)

**Files:**
- Modify: `app/frontend/src/lib/i18n.ts`

- [ ] **Step 1: 세 언어 객체 각각에 payment 키 추가**

`ko`, `en`, `zh` 세 객체 모두에 동일 구조로 추가한다(기존 네임스페이스 스타일에 맞춰 배치).

ko:
```ts
  payment: {
    pay: "결제하기",
    processing: "결제 진행 중…",
    paid: "결제 완료",
    failed: "결제가 취소되었거나 실패했습니다.",
    error: "결제 중 오류가 발생했습니다. 다시 시도해주세요.",
  },
```

en:
```ts
  payment: {
    pay: "Pay now",
    processing: "Processing…",
    paid: "Paid",
    failed: "Payment was cancelled or failed.",
    error: "Something went wrong during payment. Please try again.",
  },
```

zh:
```ts
  payment: {
    pay: "立即支付",
    processing: "支付处理中…",
    paid: "已支付",
    failed: "支付已取消或失败。",
    error: "支付过程中出现错误，请重试。",
  },
```

- [ ] **Step 2: 타입 체크 + 린트 (Task 11 포함 최종 통과)**

Run: `cd app/frontend && npx tsc --noEmit && npm run lint`
Expected: 에러 0

- [ ] **Step 3: Commit**

```bash
git add app/frontend/src/lib/i18n.ts
git commit -m "feat(payment): 결제 문구 i18n (ko/en/zh)"
```

---

## Task 13: 관리자 정산 페이지

**Files:**
- Create: `app/frontend/src/app/admin/settlements/page.tsx`

**Interfaces:**
- Consumes: 기존 admin 페이지 패턴(관리자 라우팅·fetch). 실제 admin 페이지 구조는 Step 1로 확인.

- [ ] **Step 1: 기존 admin 페이지 패턴 확인**

Run: `find app/frontend/src/app/admin -name 'page.tsx'; sed -n '1,40p' $(find app/frontend/src/app/admin -name 'page.tsx' | head -1)`
Expected: 관리자 인증 가드·fetch·표 렌더 패턴 파악 → 아래 페이지를 그 패턴에 맞춘다.

- [ ] **Step 2: 정산 원장 페이지 작성**

기존 admin 페이지의 인증 가드/스타일을 그대로 따르되, 데이터는 `GET /api/admin/settlements`, 지급완료는 `PATCH /api/admin/settlements/{id}/payout`를 호출한다. (아래는 핵심 로직 골격 — 기존 admin 레이아웃/가드로 감싼다)

```tsx
"use client";
import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api"; // Task 10 Step 1에서 확인한 실제 래퍼

type SettlementRow = {
  id: number; bookingId: number; guideProfileId: number;
  grossAmount: number; commissionAmount: number; netAmount: number;
  status: string; createdAt: string; paidOutAt: string | null; adminMemo: string | null;
};

export default function AdminSettlementsPage() {
  const [rows, setRows] = useState<SettlementRow[]>([]);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try {
      setRows(await apiFetch("/api/admin/settlements", { method: "GET" }));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); }, []);

  async function payout(id: number) {
    const memo = prompt("이체 참조 메모(선택)") ?? "";
    await apiFetch(`/api/admin/settlements/${id}/payout`, {
      method: "PATCH",
      body: JSON.stringify({ adminMemo: memo }),
    });
    await load();
  }

  if (loading) return <div className="p-8">불러오는 중…</div>;

  return (
    <div className="p-8">
      <h1 className="mb-6 text-2xl font-bold">정산 원장</h1>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-gray-500">
            <th className="py-2">예약</th><th>가이드</th><th>총액</th>
            <th>수수료</th><th>정산액</th><th>상태</th><th></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-b">
              <td className="py-2">#{r.bookingId}</td>
              <td>{r.guideProfileId}</td>
              <td>₩{r.grossAmount.toLocaleString()}</td>
              <td>₩{r.commissionAmount.toLocaleString()}</td>
              <td className="font-semibold">₩{r.netAmount.toLocaleString()}</td>
              <td>
                <span className={r.status === "PAID_OUT" ? "text-emerald-600" : "text-amber-600"}>
                  {r.status === "PAID_OUT" ? "지급완료" : "지급대기"}
                </span>
              </td>
              <td>
                {r.status !== "PAID_OUT" && (
                  <button onClick={() => payout(r.id)}
                    className="rounded bg-sky-600 px-3 py-1 text-white hover:bg-sky-700">
                    지급완료
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 3: 타입 체크 + 린트**

Run: `cd app/frontend && npx tsc --noEmit && npm run lint`
Expected: 에러 0

- [ ] **Step 4: Commit**

```bash
git add app/frontend/src/app/admin/settlements/page.tsx
git commit -m "feat(payment): 관리자 정산 원장 페이지 (목록 + 지급완료)"
```

---

## Task 14: 통합 스모크 (수동) + 문서 갱신

**Files:**
- Modify: `app/PROGRESS.md` (진행 기록 추가)

- [ ] **Step 1: 백엔드 전체 빌드·테스트**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle build -x bootJar`
Expected: BUILD SUCCESSFUL (payment 단위 테스트 11+3 포함 전부 PASS)

- [ ] **Step 2: 프론트 빌드**

Run: `cd app/frontend && npm run build`
Expected: 성공, `/admin/settlements` 라우트 포함

- [ ] **Step 3: 수동 스모크 시나리오 (문서화, PortOne 테스트 키 필요)**

`app/PROGRESS.md`에 스모크 체크리스트를 기록한다:
```
- [ ] 여행자 요청 → 가이드 수락 → 예약 상세 "결제하기" 노출
- [ ] 결제창(PortOne 테스트) 결제 → complete → PAID 배지
- [ ] 잘못된 금액 위조 시도 → 서버 거부(로그 "결제 금액 불일치")
- [ ] 완료 처리 → 관리자 정산 원장에 PENDING 15% 수수료 행 생성
- [ ] 여행자 취소(PAID·미지급) → PortOne 환불 → REFUNDED
- [ ] 관리자 지급완료 → PAID_OUT, 이후 취소 시 환불 거부
- [ ] 키 미설정 시 앱 정상 부팅(결제 버튼 눌러도 degrade)
- [ ] en/zh 결제 문구 렌더 확인
```

- [ ] **Step 4: 브랜치 리뷰 요청**

REQUIRED SUB-SKILL: superpowers:requesting-code-review — 결제/보안 민감 영역이므로 머지 전 전체 브랜치 리뷰. 특히 금액 검증·멱등·환불 가드·webhook public 안전성 집중.

- [ ] **Step 5: Commit**

```bash
git add app/PROGRESS.md
git commit -m "docs(payment): 결제 시스템 진행 기록 + 스모크 체크리스트"
```

---

## Self-Review 결과 (스펙 대비)

- **에스크로/PortOne/수동정산/수락후결제/전액환불** — Task 3~8로 전부 커버.
- **BookingStatus 불변** — enum 미수정, "결제됨"은 Payment PAID로 판정(Task 1,3,4). ✓
- **서버 금액 검증** — Task 4 confirm의 핵심, 테스트 3종. ✓
- **웹훅+멱등** — Task 4 멱등 + Task 5 webhook + portoneUid unique(Task 1). ✓
- **수수료 스냅샷** — Settlement.commissionRate 저장(Task 1,6). ✓
- **환불 가드** — Task 7 PAID_OUT 차단 테스트. ✓
- **KRW 정수/키없으면 degrade/npm무추가/i18n 3언어** — Task 1,2,9,10,12. ✓
- **N+1 금지** — 목록 DTO 미변경, 결제 상태는 단건 엔드포인트(Task 5,11). ✓

**열린 질문(구현 중 확정):** PortOne V2 웹훅 본문 필드 경로(Task 5 Step 3 주석) / V2 웹훅 서명 검증 / 관리자 권한은 기존 `/api/admin/**` hasRole 재사용(SecurityConfig 기존 규칙).
