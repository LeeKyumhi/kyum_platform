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
