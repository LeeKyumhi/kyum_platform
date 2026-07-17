package com.guidematch.chat;

/** 인박스 미리보기 truncation — DM(ConversationService)·통합 인박스(InboxService)가 공유하는 단일 규칙. */
final class Previews {

    static final int MAX = 80;

    private Previews() {}

    static String clip(String content) {
        return content.length() > MAX ? content.substring(0, MAX) : content;
    }
}
