package com.guidematch.payment.dto;

/**
 * 결제창에 넘길 값. storeId/channelKey는 프론트가 NEXT_PUBLIC env로 이미 가지므로 서버는 주문번호·금액만 준다.
 *
 * buyer* 는 PG(스마트로)가 결제창 호출 시 구매자 연락처를 필수로 요구해서 함께 내려준다.
 * 전부 **요청한 본인의 정보**만 담기며(서버가 토큰의 userId로 조회), 상대방 정보는 절대 넣지 않는다.
 * buyerPhone이 null이면 아직 연락처를 등록하지 않은 것 — 프론트가 입력을 받아야 한다.
 */
public record PreparePaymentResponse(
        String merchantUid,
        Integer amount,
        String currency,
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        /** 구매자 고유 식별번호(우리 user id). 스마트로 간편결제가 결제창 호출 시 필수로 요구한다. */
        String buyerId
) {}
