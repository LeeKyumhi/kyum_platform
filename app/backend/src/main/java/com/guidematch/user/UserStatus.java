package com.guidematch.user;

/**
 * 계정 상태. 기본 ACTIVE, 운영자가 정지하면 SUSPENDED.
 * null(기존 회원 포함)은 ACTIVE로 취급한다(백필 불필요, ddl-auto 안전).
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
