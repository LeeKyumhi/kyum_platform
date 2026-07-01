package com.guidematch.chat.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 설정.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 클라이언트가 연결할 주소(엔드포인트). 프론트는 ws://localhost:8080/ws 로 붙는다.
     * withSockJS() = WebSocket을 못 쓰는 환경을 위한 대체 수단까지 제공.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:3000")
                .withSockJS();
    }

    /**
     * 메시지 라우팅 규칙.
     *  - "/topic" : 서버가 구독자들에게 메시지를 뿌리는(broadcast) 주소 접두사
     *  - "/app"   : 클라이언트가 서버로 메시지를 보낼 때 쓰는 주소 접두사 (@MessageMapping과 연결)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /** 들어오는 메시지에 인증 인터셉터를 끼운다 (CONNECT 시 토큰 검사). */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
