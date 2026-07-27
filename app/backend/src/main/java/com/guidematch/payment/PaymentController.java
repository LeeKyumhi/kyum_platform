package com.guidematch.payment;

import com.guidematch.payment.dto.ConfirmPaymentRequest;
import com.guidematch.payment.dto.PaymentStatusResponse;
import com.guidematch.payment.dto.PreparePaymentRequest;
import com.guidematch.payment.dto.PreparePaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 결제 API. webhook 외에는 로그인 필요. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

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
        try {
            paymentService.confirm(paymentId, merchantUid);
        } catch (Exception e) {
            // 웹훅은 매칭/검증 실패에도 200으로 응답한다(PortOne 재발화 폭주 방지, 정보 노출 차단).
            // 정상 결제는 브라우저 콜백(/complete)에서도 확정되므로 유실 위험 없음.
            log.warn("webhook confirm 무시 merchantUid={} paymentId={}: {}", merchantUid, paymentId, e.toString());
        }
    }

    /** 예약의 결제 상태 (예약 상세 전용, 단건). */
    @GetMapping("/booking/{bookingId}")
    public PaymentStatusResponse status(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long bookingId) {
        return paymentService.statusForBooking(userId, bookingId);
    }
}
