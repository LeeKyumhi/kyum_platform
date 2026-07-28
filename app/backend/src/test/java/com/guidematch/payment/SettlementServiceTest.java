package com.guidematch.payment;

import com.guidematch.booking.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
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

    /** Booking()의 no-arg 생성자는 protected(다른 패키지에서 직접 new 불가)라
     * BeanUtils.instantiateClass로 접근성을 우회해 인스턴스를 만든다(PaymentServiceTest와 동일 패턴). */
    private Booking booking(Long id, int total) {
        Booking b = BeanUtils.instantiateClass(Booking.class);
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

    // --- 수수료 반올림 ---
    // 위 테스트의 100,000원은 15,000원으로 딱 떨어져서 반올림 방향을 전혀 검증하지 못한다.
    // 총액이 20의 배수가 아니면 총액×0.15에 소수가 생긴다 — 그 경로를 고정한다.
    // Settlement는 같은 패키지라 서비스/목 없이 생성자로 직접 계산을 검증한다.

    @Test
    void 수수료_소수점_절반은_올림() {
        Settlement s = new Settlement(1L, 7L, 1010, 0.15); // 151.5 → 152
        assertEquals(152, s.getCommissionAmount());
        assertEquals(858, s.getNetAmount());
    }

    @Test
    void 수수료_소수점_절반미만은_내림() {
        Settlement s = new Settlement(1L, 7L, 1002, 0.15); // 150.3 → 150
        assertEquals(150, s.getCommissionAmount());
        assertEquals(852, s.getNetAmount());
    }

    /**
     * 반올림이 어느 방향으로 가든 수수료+정산액은 총액과 정확히 같아야 한다.
     * 이게 깨지면 플랫폼이 받은 돈과 장부가 1원씩 어긋난다 — 건수가 쌓이면 대사가 불가능해진다.
     */
    @Test
    void 수수료와_정산액의_합은_항상_총액() {
        for (int gross = 1000; gross <= 2000; gross++) {
            Settlement s = new Settlement(1L, 7L, gross, 0.15);
            assertEquals(gross, s.getCommissionAmount() + s.getNetAmount(),
                    "총액 " + gross + "원에서 장부가 어긋남");
            assertTrue(s.getCommissionAmount() >= 0 && s.getNetAmount() >= 0,
                    "총액 " + gross + "원에서 음수 발생");
        }
    }
}
