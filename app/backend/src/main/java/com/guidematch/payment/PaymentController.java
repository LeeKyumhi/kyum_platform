package com.guidematch.payment;

import com.guidematch.payment.dto.PreparePaymentRequest;
import com.guidematch.payment.dto.PreparePaymentResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 결제 API. webhook 외에는 로그인 필요. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

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
}
