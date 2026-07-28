package com.guidematch.user;

import com.guidematch.auth.AuthService;
import com.guidematch.auth.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 결제용 연락처 저장. 국가번호를 포함한 E.164로만 받는다 —
 * 한국 번호가 없는 외국인 여행자도 자기 나라 번호를 그대로 쓸 수 있어야 하기 때문이다.
 */
class UserPhoneUpdateTest {

    private UserRepository userRepository;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        controller = new UserController(userRepository, mock(AuthService.class));
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 국가번호가_붙은_번호를_저장한다() {
        UserResponse res = controller.updatePhone(1L, new UserController.PhoneRequest("+821012345678"));
        assertEquals("+821012345678", res.phone());
    }

    @Test
    void 해외번호도_그대로_받는다() {
        assertEquals("+14155551234",
                controller.updatePhone(1L, new UserController.PhoneRequest("+14155551234")).phone());
        assertEquals("+819012345678",
                controller.updatePhone(1L, new UserController.PhoneRequest("+81 90-1234-5678")).phone());
    }

    @Test
    void 하이픈_공백은_지우고_저장한다() {
        assertEquals("+821012345678",
                controller.updatePhone(1L, new UserController.PhoneRequest("+82 10-1234-5678")).phone());
    }

    @Test
    void 국가번호가_없으면_거부한다() {
        // 국가번호 없는 "01012345678"은 어느 나라 번호인지 알 수 없다 → PG에 잘못 나간다.
        assertThrows(IllegalArgumentException.class,
                () -> controller.updatePhone(1L, new UserController.PhoneRequest("01012345678")));
    }

    @Test
    void 숫자가_아닌_값은_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.updatePhone(1L, new UserController.PhoneRequest("+82-abc-def")));
    }

    @Test
    void 빈_값이면_연락처를_지운다() {
        assertNull(controller.updatePhone(1L, new UserController.PhoneRequest("")).phone());
        assertNull(controller.updatePhone(1L, new UserController.PhoneRequest(null)).phone());
    }
}
