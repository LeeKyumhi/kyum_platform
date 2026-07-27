package com.guidematch.payment.dto;

/** 브라우저 콜백 본문. paymentId는 PortOne이 발급한 결제 id. */
public record ConfirmPaymentRequest(String paymentId, String merchantUid) {}
