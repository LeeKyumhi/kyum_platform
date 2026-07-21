package com.guidematch.auth;

/** 로컬 계정이 이메일 인증 전이라 로그인이 거부될 때 던진다. 자격증명 오류와 구분하기 위한 별도 타입. */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
