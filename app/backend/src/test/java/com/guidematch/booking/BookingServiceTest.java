package com.guidematch.booking;

import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.itinerary.ItineraryService;
import com.guidematch.payment.PaymentService;
import com.guidematch.payment.SettlementService;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 회귀 테스트: cancel()의 상태 가드가 환불(paymentService.refundForBooking)보다
 * 먼저 실행되는지 검증한다. 순서가 뒤집히면 COMPLETED(+PAID) 예약에서 PortOne 환불
 * HTTP 호출이 먼저 나간 뒤에야 booking.cancel()이 예외를 던져, DB는 롤백되어도
 * PG(PortOne) 쪽 환불은 이미 실행돼버리는 money-divergence 버그가 재발한다.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock GuideProfileService guideProfileService;
    @Mock GuideProfileRepository guideProfileRepository;
    @Mock UserRepository userRepository;
    @Mock ItineraryService itineraryService;
    @Mock SettlementService settlementService;
    @Mock PaymentService paymentService;

    BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, guideProfileService, guideProfileRepository,
                userRepository, itineraryService, settlementService, paymentService);
    }

    /** Booking()의 no-arg 생성자는 protected라 BeanUtils.instantiateClass로 접근성을 우회한다
     * (PaymentServiceTest의 동일 헬퍼 패턴을 그대로 따른다). */
    private Booking booking(Long id, Long travelerId, BookingStatus status) {
        Booking b = BeanUtils.instantiateClass(Booking.class);
        ReflectionTestUtils.setField(b, "id", id);
        ReflectionTestUtils.setField(b, "travelerId", travelerId);
        ReflectionTestUtils.setField(b, "status", status);
        return b;
    }

    @Test
    void 완료된_예약_취소는_환불_호출_전에_거부된다() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(
                booking(1L, 10L, BookingStatus.COMPLETED)));

        assertThrows(IllegalArgumentException.class, () -> service.cancel(10L, 1L));

        verify(paymentService, never()).refundForBooking(anyLong());
    }
}
