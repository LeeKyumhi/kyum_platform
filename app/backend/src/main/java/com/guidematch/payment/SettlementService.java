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
