package com.guidematch.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 트랜잭션 이메일 발송 — ResendEmailClient(범용 발송) 위에 템플릿을 얹은 계층.
 * TranslationService가 GoogleTranslateClient를 감싸는 것과 같은 구조.
 *
 * 지금은 인증/재설정 메일만 있지만, 예약 확정 메일 등 향후 알림도
 * 이 클래스에 sendXxx 메서드만 추가하면 같은 인프라(발신자, 인증, 에러 처리)를 재사용할 수 있다.
 */
@Service
public class EmailService {

    private final ResendEmailClient client;
    private final String frontendUrl;

    public EmailService(
            ResendEmailClient client,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl
    ) {
        this.client = client;
        // 끝의 슬래시 제거 — 링크 조합 시 이중 슬래시 방지
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public boolean isEnabled() {
        return client.isEnabled();
    }

    public boolean sendVerificationEmail(String toEmail, String recipientName, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String html = """
                <p>Hi %s,</p>
                <p>Welcome to PeerUp! Please verify your email address to finish setting up your account.</p>
                <p><a href="%s">Verify my email</a></p>
                <p>This link expires in 24 hours. If you didn't create a PeerUp account, you can ignore this email.</p>
                <hr/>
                <p>안녕하세요 %s님,</p>
                <p>PeerUp 가입을 환영합니다. 아래 링크를 눌러 이메일 인증을 완료해주세요.</p>
                <p><a href="%s">이메일 인증하기</a></p>
                <p>링크는 24시간 후 만료됩니다. 본인이 가입하지 않았다면 이 메일을 무시하셔도 됩니다.</p>
                """.formatted(recipientName, link, recipientName, link);
        return client.send(toEmail, "Verify your PeerUp email / 이메일 인증", html);
    }

    public boolean sendPasswordResetEmail(String toEmail, String recipientName, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String html = """
                <p>Hi %s,</p>
                <p>We received a request to reset your PeerUp password. Click the link below to choose a new one.</p>
                <p><a href="%s">Reset my password</a></p>
                <p>This link expires in 1 hour. If you didn't request this, you can ignore this email — your password won't change.</p>
                <hr/>
                <p>안녕하세요 %s님,</p>
                <p>비밀번호 재설정 요청이 접수되었습니다. 아래 링크를 눌러 새 비밀번호를 설정해주세요.</p>
                <p><a href="%s">비밀번호 재설정하기</a></p>
                <p>링크는 1시간 후 만료됩니다. 본인이 요청하지 않았다면 이 메일을 무시하셔도 되며, 비밀번호는 변경되지 않습니다.</p>
                """.formatted(recipientName, link, recipientName, link);
        return client.send(toEmail, "Reset your PeerUp password / 비밀번호 재설정", html);
    }
}
