package com.guidematch.user;

/**
 * 사용자 권한. 기본은 USER, 운영자만 ADMIN.
 * ADMIN 승격은 셀프서비스가 아니라 DB에서 직접 1회 수행한다(창업자 계정 부트스트랩).
 * null(기존 회원 포함)은 USER로 취급한다.
 */
public enum UserRole {
    USER,
    ADMIN
}
