package com.guidematch.payment.dto;

/** 결제창에 넘길 값. storeId/channelKey는 프론트가 NEXT_PUBLIC env로 이미 가지므로 서버는 주문번호·금액만 준다. */
public record PreparePaymentResponse(String merchantUid, Integer amount, String currency) {}
