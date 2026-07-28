package com.guidematch.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidematch.payment.dto.ConfirmPaymentRequest;
import com.guidematch.payment.dto.PaymentStatusResponse;
import com.guidematch.payment.dto.PreparePaymentRequest;
import com.guidematch.payment.dto.PreparePaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 결제 API. webhook 외에는 로그인 필요. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final WebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService,
                            WebhookVerifier webhookVerifier,
                            ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.webhookVerifier = webhookVerifier;
        this.objectMapper = objectMapper;
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
     * PortOne 웹훅(public). 서명(Standard Webhooks) 검증 후 처리한다.
     * 서명 검증은 반드시 원본 바디 문자열로 해야 하므로 String으로 받는다(재직렬화 금지 — 바이트가 달라짐).
     * 금액·상태는 confirm 안에서 PortOne 재조회로 검증한다. 멱등.
     * PortOne V2 웹훅 data는 { storeId, paymentId, transactionId }만 담는다.
     * V2에선 data.paymentId가 곧 우리가 발급한 주문번호(merchantUid)이므로
     * 매칭 키와 PortOne 재조회 키가 동일한 값이다 — 별도 merchantId 필드는 없다.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp,
            @RequestHeader(value = "webhook-signature", required = false) String webhookSignature) {

        if (!webhookVerifier.verify(rawBody, webhookId, webhookTimestamp, webhookSignature)) {
            // 서명 불일치 = 위조 가능성 → 401. (검증 비활성 시엔 verify가 true라 여기 안 옴)
            return ResponseEntity.status(401).build();
        }

        String paymentId = null;
        try {
            JsonNode data = objectMapper.readTree(rawBody).path("data");
            JsonNode pid = data.path("paymentId");
            if (pid.isTextual()) paymentId = pid.asText();
        } catch (Exception e) {
            log.warn("webhook 본문 파싱 실패: {}", e.toString());
        }
        if (paymentId == null) return ResponseEntity.ok().build(); // 매칭 불가 → 200으로 무시(재발화 대비)
        String merchantUid = paymentId; // V2: paymentId == 주문번호(merchantUid)
        try {
            paymentService.confirm(paymentId, merchantUid);
        } catch (Exception e) {
            // 서명은 통과했으나 confirm 실패 → 200 유지(PortOne 재발화 폭주 방지, 정보 노출 차단).
            // 정상 결제는 브라우저 콜백(/complete)에서도 확정되므로 유실 위험 없음.
            log.warn("webhook confirm 무시 merchantUid={} paymentId={}: {}", merchantUid, paymentId, e.toString());
        }
        return ResponseEntity.ok().build();
    }

    /** 예약의 결제 상태 (예약 상세 전용, 단건). */
    @GetMapping("/booking/{bookingId}")
    public PaymentStatusResponse status(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long bookingId) {
        return paymentService.statusForBooking(userId, bookingId);
    }
}
