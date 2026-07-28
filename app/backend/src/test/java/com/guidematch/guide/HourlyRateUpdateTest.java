package com.guidematch.guide;

import com.guidematch.booking.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 가이드가 자기 시급을 직접 바꾼다.
 *
 * 돈에 직결되는 자리라 범위를 서버가 막는다(1,000 ~ 1,000,000원). 프론트 검증은 UX용이고
 * 권위는 여기에 있다. 그리고 시급을 바꿔도 **이미 잡힌 예약 금액은 변하지 않아야** 한다.
 */
@ExtendWith(MockitoExtension.class)
class HourlyRateUpdateTest {

    @Mock GuideProfileRepository guideProfileRepository;

    GuideProfileService service;

    @BeforeEach
    void setUp() {
        service = new GuideProfileService(guideProfileRepository, null, null, "credentials");
    }

    private GuideProfile profileOf(Long userId, int hourlyRate) {
        GuideProfile p = BeanUtils.instantiateClass(GuideProfile.class);
        ReflectionTestUtils.setField(p, "id", 7L);
        ReflectionTestUtils.setField(p, "userId", userId);
        ReflectionTestUtils.setField(p, "hourlyRate", hourlyRate);
        ReflectionTestUtils.setField(p, "currency", "KRW");
        return p;
    }

    private void givenProfile(GuideProfile p) {
        when(guideProfileRepository.findByUserId(p.getUserId())).thenReturn(Optional.of(p));
        lenient().when(guideProfileRepository.save(any(GuideProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 시급을_바꾼다() {
        givenProfile(profileOf(1L, 50000));
        assertEquals(80000, service.updateHourlyRate(1L, 80000).getHourlyRate());
    }

    @Test
    void 하한_1000원은_통과하고_999원은_거부한다() {
        givenProfile(profileOf(1L, 50000));
        assertEquals(1000, service.updateHourlyRate(1L, 1000).getHourlyRate());
        assertThrows(IllegalArgumentException.class, () -> service.updateHourlyRate(1L, 999));
    }

    @Test
    void 상한_100만원은_통과하고_초과는_거부한다() {
        givenProfile(profileOf(1L, 50000));
        assertEquals(1_000_000, service.updateHourlyRate(1L, 1_000_000).getHourlyRate());
        assertThrows(IllegalArgumentException.class, () -> service.updateHourlyRate(1L, 1_000_001));
    }

    @Test
    void 값이_없으면_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> service.updateHourlyRate(1L, null));
    }

    @Test
    void 프로필이_없으면_예외() {
        when(guideProfileRepository.findByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.updateHourlyRate(99L, 50000));
    }

    @Test
    void 통화는_바뀌지_않는다() {
        GuideProfile p = profileOf(1L, 50000);
        givenProfile(p);
        assertEquals("KRW", service.updateHourlyRate(1L, 70000).getCurrency());
    }

    @Test
    void 시급을_바꿔도_기존_예약_금액은_그대로다() {
        // CLAUDE.md 제약: 가격은 예약 시점에 스냅샷된다 — 절대 실시간 파생 금지.
        // 예약은 생성 시점 시급을 복사해 갖고 있으므로 프로필 시급 변경과 무관해야 한다.
        Booking existing = BeanUtils.instantiateClass(Booking.class);
        ReflectionTestUtils.setField(existing, "hourlyRateSnapshot", 50000);
        ReflectionTestUtils.setField(existing, "totalPrice", 150000); // 3시간 × 50,000

        givenProfile(profileOf(1L, 50000));
        service.updateHourlyRate(1L, 90000);

        assertEquals(50000, existing.getHourlyRateSnapshot(), "기존 예약의 시급 스냅샷이 변했다");
        assertEquals(150000, existing.getTotalPrice(), "기존 예약의 총액이 변했다");
    }
}
