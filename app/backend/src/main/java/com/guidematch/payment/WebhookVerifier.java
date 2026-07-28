package com.guidematch.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * PortOne V2 웹훅 서명 검증(Standard Webhooks 규격).
 *
 * 서명 대상 = "{webhook-id}.{webhook-timestamp}.{raw body}"
 * 서명      = base64(HMAC-SHA256(key, 서명대상)),  key = base64decode(시크릿에서 "whsec_" 제거)
 * webhook-signature 헤더 = 공백 구분 "v1,<base64sig>" 목록 — 하나라도 일치하면 통과.
 *
 * 시크릿(portone.webhook-secret)이 비어 있으면 검증을 건너뛴다(개발/미설정 대비).
 * — 이 경우 confirm()의 PortOne 재조회가 여전히 위조 결제를 차단하므로 정합성은 유지된다.
 */
@Component
public class WebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookVerifier.class);
    private static final long TOLERANCE_SECONDS = 300; // ±5분

    private final byte[] key;      // 디코딩된 서명 키. 미설정이면 null.
    private final boolean enabled;

    public WebhookVerifier(@Value("${portone.webhook-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            this.key = null;
            this.enabled = false;
        } else {
            String base64 = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
            this.key = Base64.getDecoder().decode(base64);
            this.enabled = true;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 서명 검증. 미설정이면 true(건너뜀 + WARN). 설정됐으면 헤더·타임스탬프·서명이 모두 맞아야 true.
     */
    public boolean verify(String rawBody, String webhookId, String webhookTimestamp, String webhookSignature) {
        if (!enabled) {
            log.warn("PortOne 웹훅 서명 검증 비활성(PORTONE_WEBHOOK_SECRET 미설정) — 서명 미검증으로 처리");
            return true;
        }
        if (webhookId == null || webhookTimestamp == null || webhookSignature == null) {
            log.warn("웹훅 서명 헤더 누락 id={} ts={} sig={}",
                    webhookId, webhookTimestamp, webhookSignature != null);
            return false;
        }
        if (!timestampWithinTolerance(webhookTimestamp)) {
            log.warn("웹훅 타임스탬프 허용범위 초과 ts={}", webhookTimestamp);
            return false;
        }

        String signedContent = webhookId + "." + webhookTimestamp + "." + rawBody;
        String expected = hmacBase64(signedContent);
        if (expected == null) return false;

        // 헤더는 "v1,<sig> v1,<sig2> ..." 형태 — v1 서명 중 하나라도 상수시간 일치하면 통과.
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        for (String part : webhookSignature.split(" ")) {
            int comma = part.indexOf(',');
            if (comma < 0) continue;
            String version = part.substring(0, comma);
            if (!"v1".equals(version)) continue;
            String sig = part.substring(comma + 1);
            if (MessageDigest.isEqual(expectedBytes, sig.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        log.warn("웹훅 서명 불일치 id={}", webhookId);
        return false;
    }

    private boolean timestampWithinTolerance(String webhookTimestamp) {
        try {
            long ts = Long.parseLong(webhookTimestamp.trim());
            long now = System.currentTimeMillis() / 1000L;
            return Math.abs(now - ts) <= TOLERANCE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String hmacBase64(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            log.error("웹훅 HMAC 계산 실패", e);
            return null;
        }
    }
}
