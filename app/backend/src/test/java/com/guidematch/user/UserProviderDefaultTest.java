package com.guidematch.user;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserProviderDefaultTest {

    @Test
    void getProvider_nullField_defaultsToLocal() {
        User u = new User("a@b.com", "hash", "홍길동", "KR");
        // provider 미설정(기존 행 시뮬레이션) → LOCAL로 취급
        assertThat(u.getProvider()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    void getProvider_setGoogle_returnsGoogle() {
        User u = new User("a@b.com", null, "홍길동", "KR");
        u.setProvider(AuthProvider.GOOGLE);
        assertThat(u.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    }
}
