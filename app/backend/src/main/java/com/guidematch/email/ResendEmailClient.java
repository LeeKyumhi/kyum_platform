package com.guidematch.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Resend(https://resend.com) 트랜잭션 이메일 REST API 클라이언트.
 * GoogleTranslateClient와 같은 모양: 키 없으면 isEnabled()==false → 발송 스킵, 앱은 정상 동작.
 *
 * 범용 발송 메서드(send)만 제공한다 — 인증/재설정 메일뿐 아니라 향후 예약 확정 메일 등도
 * 이 클라이언트 위에 템플릿만 추가해서 재사용할 수 있게 하기 위함(EmailService가 그 역할).
 */
@Component
public class ResendEmailClient {

    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final RestClient restClient = RestClient.create();
    private final String apiKey;
    private final String fromAddress;

    public ResendEmailClient(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from-address:onboarding@resend.dev}") String fromAddress
    ) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 이메일 한 통 발송. 실패해도 예외를 던지지 않고 false를 반환한다 —
     * 이메일 발송 실패가 가입/비번재설정 같은 핵심 흐름을 막아서는 안 된다.
     */
    public boolean send(String to, String subject, String html) {
        if (!isEnabled()) return false;
        try {
            EmailRequest body = new EmailRequest(fromAddress, List.of(to), subject, html);
            restClient.post()
                    .uri(ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private record EmailRequest(
            @JsonProperty("from") String from,
            @JsonProperty("to") List<String> to,
            @JsonProperty("subject") String subject,
            @JsonProperty("html") String html
    ) {}
}
