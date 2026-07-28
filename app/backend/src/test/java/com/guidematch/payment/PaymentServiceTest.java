package com.guidematch.payment;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.payment.dto.PreparePaymentResponse;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock UserRepository userRepository;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(bookingRepository, paymentRepository, settlementRepository,
                portOneClient, guideProfileService, userRepository, 0.15);
    }

    /** 연락처가 등록된 구매자. prepare가 결제창에 넘길 buyer* 를 채우는지 보는 데 쓴다. */
    private User buyerWithPhone(String e164) {
        // Booking과 같은 이유 — JPA용 no-arg 생성자가 다른 패키지에서 직접 new 불가.
        User u = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(u, "id", 1L);
        ReflectionTestUtils.setField(u, "fullName", "김여행");
        ReflectionTestUtils.setField(u, "email", "traveler@test.com");
        u.setPhone(e164);
        return u;
    }

    /** 필드 세팅용 booking 헬퍼. 생성자 대신 리플렉션으로 최소 필드만 채운다.
     * Booking()의 no-arg 생성자는 protected(다른 패키지에서 직접 new 불가)라
     * BeanUtils.instantiateClass로 접근성을 우회해 인스턴스를 만든다. */
    private Booking booking(Long id, Long travelerId, BookingStatus status, int total) {
        Booking b = BeanUtils.instantiateClass(Booking.class);
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
    void PENDING_잔재는_재사용하고_새로_저장하지_않는다() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.ACCEPTED, 50000)));
        Payment pending = new Payment(1L, "m-existing", 50000, "KRW");
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(pending));

        PreparePaymentResponse res = service.prepare(1L, 1L);

        assertEquals("m-existing", res.merchantUid());
        verify(paymentRepository, never()).save(any());
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

    @Test
    void 주문번호는_특수문자없는_영숫자여야_한다() {
        // 일부 PG(스마트로)가 주문번호 특수문자(하이픈 등)를 거부한다 — 회귀 방지.
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.ACCEPTED, 50000)));
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PreparePaymentResponse res = service.prepare(1L, 1L);

        assertTrue(res.merchantUid().matches("^[A-Za-z0-9]+$"),
                "merchantUid에 특수문자가 있으면 안 됨: " + res.merchantUid());
    }

    @Test
    void 결제준비는_본인_연락처를_PG형식으로_함께_준다() {
        // 스마트로는 결제창 호출 시 구매자 연락처가 필수라 prepare가 함께 내려줘야 한다.
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.ACCEPTED, 50000)));
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyerWithPhone("+821012345678")));

        PreparePaymentResponse res = service.prepare(1L, 1L);

        assertEquals("010-1234-5678", res.buyerPhone());
        assertEquals("김여행", res.buyerName());
        assertEquals("traveler@test.com", res.buyerEmail());
        // 스마트로 간편결제는 구매자 고유 식별번호도 결제창 호출 시 필수로 요구한다.
        assertEquals("1", res.buyerId());
    }

    @Test
    void 연락처_미등록이면_buyerPhone은_null이다() {
        // 프론트는 이 null을 보고 입력 모달을 띄운다 — 가짜 기본값을 넣지 않는다.
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.ACCEPTED, 50000)));
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyerWithPhone(null)));

        assertNull(service.prepare(1L, 1L).buyerPhone());
    }

    @Test
    void 연락처_저장후_재시도하면_기존주문_그대로_연락처가_채워진다() {
        // 연락처 미등록 → 모달 저장 → 재결제. 이때 prepare는 기존 PENDING 주문을 재사용하는데,
        // 이 재사용 경로에서도 buyer* 가 채워져야 결제창이 열린다. 실제 재시도가 매번 타는 경로.
        Payment pending = new Payment(1L, "booking1t99", 50000, "KRW");
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.ACCEPTED, 50000)));
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyerWithPhone("+821012345678")));

        PreparePaymentResponse res = service.prepare(1L, 1L);

        assertEquals("booking1t99", res.merchantUid(), "기존 주문번호를 유지해야 한다");
        assertEquals("010-1234-5678", res.buyerPhone());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void 외국번호는_국가번호를_유지한_채_내려간다() {
        // 한국 번호가 없는 외국인 여행자가 이 서비스의 주 사용자다.
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 1L, BookingStatus.ACCEPTED, 50000)));
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyerWithPhone("+14155551234")));

        assertEquals("+14155551234", service.prepare(1L, 1L).buyerPhone());
    }

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
    void 통화_불일치는_PAID_거부() {
        Payment pending = new Payment(1L, "m-1", 50000, "KRW");
        when(paymentRepository.findByMerchantUid("m-1")).thenReturn(Optional.of(pending));
        // PortOne이 알려준 실제 결제 통화가 USD (예약은 KRW)
        when(portOneClient.getPayment("imp_x"))
                .thenReturn(new PortOneClient.PortOnePayment("PAID", 50000, "USD"));

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
}
