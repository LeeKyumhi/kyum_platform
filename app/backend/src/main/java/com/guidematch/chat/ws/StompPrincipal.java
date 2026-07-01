package com.guidematch.chat.ws;

import java.security.Principal;

/**
 * WebSocket 연결의 "사용자"를 나타내는 간단한 Principal.
 * 이름(name)에 사용자 id를 담아둔다 → 핸들러에서 누가 보낸 메시지인지 식별.
 */
public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
