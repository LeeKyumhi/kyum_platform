package com.guidematch.payment.dto;

/** 예약 상세용 결제 상태. 결제 이력 없으면 status="NONE". */
public record PaymentStatusResponse(String status, Integer amount, String currency) {}
