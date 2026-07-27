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
}
