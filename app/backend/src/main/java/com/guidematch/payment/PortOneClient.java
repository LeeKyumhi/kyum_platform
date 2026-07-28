package com.guidematch.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * PortOne V2 REST 클라이언트. 결제 재조회(서버 검증용)·전액 취소만 감싼다.
 * apiSecret(.env PORTONE_API_SECRET)로 매 호출 시 PortOne 인증 헤더를 만든다.
 * 키가 없으면 isEnabled()==false — 결제 기능 비활성, 앱은 정상 부팅.
 */
@Component
public class PortOneClient {

    private static final Logger log = LoggerFactory.getLogger(PortOneClient.class);
    private static final String BASE = "https://api.portone.io";

    private final RestClient restClient = RestClient.create();
    private final String apiSecret;

    public PortOneClient(@Value("${portone.api-secret:}") String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public boolean isEnabled() {
        return apiSecret != null && !apiSecret.isBlank();
    }

    /** PortOne V2 인증 헤더: "PortOne {apiSecret}". */
    private String authHeader() {
        return "PortOne " + apiSecret;
    }

    /** 결제 단건 재조회. 서버 금액 검증의 유일한 신뢰 소스. 실패/미설정이면 null. */
    public PortOnePayment getPayment(String paymentId) {
        if (!isEnabled()) {
            // 조용히 null을 돌려주면 confirm()이 "PortOne 결제 조회 실패"라는 뭉뚱그린 메시지만 남기고
            // 로그엔 아무 흔적이 없다 — 원인이 '키 미설정'인지 'API 호출 실패'인지 구분되게 남긴다.
            log.warn("PortOne 미설정(PORTONE_API_SECRET 없음) — 결제 조회 불가 paymentId={}", paymentId);
            return null;
        }
        try {
            PaymentBody body = restClient.get()
                    .uri(BASE + "/payments/{paymentId}", paymentId)
                    .header("Authorization", authHeader())
                    .retrieve()
                    .body(PaymentBody.class);
            if (body == null || body.amount == null) return null;
            return new PortOnePayment(body.status, body.amount.total, body.currency);
        } catch (Exception e) {
            log.warn("PortOne getPayment 실패 paymentId={}", paymentId, e);
            return null;
        }
    }

    /** 전액 취소(환불). 실패 시 예외를 던져 호출자가 트랜잭션을 롤백하게 한다. */
    public void cancelPayment(String paymentId, String reason) {
        if (!isEnabled()) throw new IllegalStateException("PortOne 미설정 — 환불 불가");
        restClient.post()
                .uri(BASE + "/payments/{paymentId}/cancel", paymentId)
                .header("Authorization", authHeader())
                .body(Map.of("reason", reason == null ? "예약 취소" : reason))
                .retrieve()
                .toBodilessEntity();
    }

    /** 서버 검증에 필요한 최소 필드만. */
    public record PortOnePayment(String status, long amount, String currency) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PaymentBody {
        public String status;      // "PAID", "CANCELLED", "FAILED", ...
        public String currency;    // "KRW"
        public Amount amount;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Amount {
        public long total;         // 총 결제금액
    }
}
