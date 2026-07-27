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

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(bookingRepository, paymentRepository, settlementRepository,
                portOneClient, guideProfileService, 0.15);
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
}
