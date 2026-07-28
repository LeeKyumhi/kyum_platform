package com.guidematch.payment;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.payment.dto.PreparePaymentResponse;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
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
    private final UserRepository userRepository;
    private final double commissionRate;

    public PaymentService(BookingRepository bookingRepository,
                          PaymentRepository paymentRepository,
                          SettlementRepository settlementRepository,
                          PortOneClient portOneClient,
                          GuideProfileService guideProfileService,
                          UserRepository userRepository,
                          @Value("${payment.commission-rate:0.15}") double commissionRate) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.settlementRepository = settlementRepository;
        this.portOneClient = portOneClient;
        this.guideProfileService = guideProfileService;
        this.userRepository = userRepository;
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

        Payment payment;
        if (existing.isPresent()) {
            Payment found = existing.get();
            if (found.getStatus() == PaymentStatus.PAID) {
                throw new IllegalArgumentException("이미 결제된 예약입니다.");
            }
            if (found.getStatus() == PaymentStatus.PENDING) {
                // 재시도(PENDING 잔재)면 그대로 재사용 — 새 행을 만들지 않는다.
                payment = found;
            } else {
                // booking_id가 unique라 새 행을 만들 수 없다 — FAILED/REFUNDED는 현재 플로우에서
                // 도달 불가하지만, 조용히 재사용하지 않고 방어적으로 명확히 실패시킨다.
                throw new IllegalArgumentException("이 예약은 다시 결제할 수 없는 상태입니다: " + found.getStatus());
            }
        } else {
            // 주문번호는 영숫자만 — 일부 PG(스마트로 등)가 특수문자(하이픈 포함)를 거부한다.
            // merchantUid는 불투명 매칭 키일 뿐 bookingId를 파싱하지 않으므로 형식은 자유.
            String merchantUid = "booking" + bookingId + "t" + System.currentTimeMillis();
            payment = paymentRepository.save(
                    new Payment(bookingId, merchantUid, booking.getTotalPrice(), booking.getCurrency()));
        }

        // 구매자 정보 — PG(스마트로)가 결제창 호출 시 연락처를 필수로 요구한다.
        // 본인(travelerId) 것만 담는다. 연락처 미등록이면 null → 프론트가 입력을 받는다.
        User buyer = userRepository.findById(travelerId).orElse(null);
        return new PreparePaymentResponse(
                payment.getMerchantUid(), payment.getAmount(), payment.getCurrency(),
                buyer == null ? null : buyer.getFullName(),
                buyer == null ? null : buyer.getEmail(),
                buyer == null ? null : PhoneFormat.toPgFormat(buyer.getPhone()),
                buyer == null ? null : String.valueOf(buyer.getId()));
    }

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
        if (!"KRW".equalsIgnoreCase(actual.currency())) {
            log.error("결제 통화 불일치! 실제={} merchantUid={}", actual.currency(), merchantUid);
            throw new IllegalArgumentException("결제 통화가 올바르지 않습니다.");
        }

        payment.markPaid(portoneUid);
    }

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
}
