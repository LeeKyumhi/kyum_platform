package com.guidematch.payment;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class WebhookVerifierTest {

    private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";

    /**
     * 테스트 측 독립 서명기 — Standard Webhooks 규격대로 서명 문자열을 만든다.
     * 아래 known-vector 테스트로 이 헬퍼가 규격과 정확히 일치함을 먼저 증명한다.
     */
    private static String sign(String id, String ts, String body) {
        try {
            byte[] key = Base64.getDecoder().decode(SECRET.substring("whsec_".length()));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((id + "." + ts + "." + body).getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 공식 테스트 벡터(standard-webhooks 스펙)로 서명 헬퍼가 규격과 일치함을 증명. */
    @Test
    void signHelper_matchesStandardWebhooksOfficialVector() {
        String sig = sign("msg_p5jXN8AQM9LWM0D4loKWxJek", "1614265330", "{\"test\": 2432232314}");
        assertEquals("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=", sig);
    }

    private String now() {
        return String.valueOf(System.currentTimeMillis() / 1000L);
    }

    @Test
    void verify_acceptsValidSignature() {
        WebhookVerifier v = new WebhookVerifier(SECRET);
        String body = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"booking-1-123\"}}";
        String ts = now();
        String sig = sign("msg_abc", ts, body);
        assertTrue(v.verify(body, "msg_abc", ts, sig));
    }

    @Test
    void verify_acceptsWhenSignatureIsOneOfSpaceDelimitedList() {
        WebhookVerifier v = new WebhookVerifier(SECRET);
        String body = "{\"data\":{\"paymentId\":\"x\"}}";
        String ts = now();
        String good = sign("msg_abc", ts, body);
        String header = "v1,bogusSignatureValueAAAAAAAAAAAAAAAAAAAAAAAA= " + good;
        assertTrue(v.verify(body, "msg_abc", ts, header));
    }

    @Test
    void verify_rejectsTamperedBody() {
        WebhookVerifier v = new WebhookVerifier(SECRET);
        String ts = now();
        String sig = sign("msg_abc", ts, "{\"data\":{\"paymentId\":\"real\"}}");
        String tampered = "{\"data\":{\"paymentId\":\"HACKED\"}}";
        assertFalse(v.verify(tampered, "msg_abc", ts, sig));
    }

    @Test
    void verify_rejectsExpiredTimestamp() {
        WebhookVerifier v = new WebhookVerifier(SECRET);
        String body = "{\"data\":{\"paymentId\":\"x\"}}";
        String oldTs = String.valueOf(System.currentTimeMillis() / 1000L - 3600); // 1시간 전
        String sig = sign("msg_abc", oldTs, body);
        assertFalse(v.verify(body, "msg_abc", oldTs, sig));
    }

    @Test
    void verify_rejectsMissingHeaders() {
        WebhookVerifier v = new WebhookVerifier(SECRET);
        assertFalse(v.verify("{}", null, null, null));
    }

    @Test
    void verify_rejectsWrongSecret() {
        WebhookVerifier v = new WebhookVerifier("whsec_" + Base64.getEncoder()
                .encodeToString("some-other-32byte-key-padding-xx".getBytes(StandardCharsets.UTF_8)));
        String body = "{\"data\":{\"paymentId\":\"x\"}}";
        String ts = now();
        String sig = sign("msg_abc", ts, body); // 원래(SECRET) 키로 서명
        assertFalse(v.verify(body, "msg_abc", ts, sig));
    }

    @Test
    void verify_skipsWhenSecretNotConfigured() {
        WebhookVerifier v = new WebhookVerifier("");
        assertTrue(v.verify("{}", null, null, null)); // 미설정 → 건너뜀(true)
        assertFalse(v.isEnabled());
    }
}
