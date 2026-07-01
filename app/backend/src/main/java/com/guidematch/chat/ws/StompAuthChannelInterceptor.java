package com.guidematch.chat.ws;

import com.guidematch.config.JwtProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * WebSocket(STOMP) 연결 시 인증을 처리하는 인터셉터.
 *
 * HTTP와 달리 WebSocket은 우리가 만든 JWT 필터를 거치지 않는다.
 * 그래서 STOMP "CONNECT" 시점에 Authorization 헤더의 토큰을 직접 검증하고,
 * 통과하면 그 연결에 사용자(Principal)를 심어둔다. 이후 메시지에서 이 사용자를 꺼내 쓴다.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtProvider jwtProvider;

    public StompAuthChannelInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 연결을 처음 맺는 CONNECT 단계에서만 토큰을 검사한다.
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("인증 토큰이 없습니다.");
            }

            String token = authHeader.substring(7);
            Long userId = jwtProvider.getUserId(token); // 토큰이 위조/만료면 예외 발생
            accessor.setUser(new StompPrincipal(userId.toString()));
        }

        return message;
    }
}
