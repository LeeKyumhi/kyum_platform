package com.guidematch.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 저장된 E.164 번호를 PG(스마트로) 결제창이 받아들이는 형태로 바꾸는 규칙.
 *
 * 스마트로 공식문서는 구매자 연락처를 필수로만 규정하고 형식 제약은 명시하지 않는다
 * (예시가 "010-0000-1234"). 그래서 국내 번호는 문서 예시와 같은 국내 표기로 내보내
 * 통과 확률을 최대화하고, 해외 번호는 국가번호가 살아 있어야 의미가 있으므로 E.164 그대로 둔다.
 */
class PhoneFormatTest {

    @Test
    void 한국_휴대폰은_국내표기_하이픈형식으로_바꾼다() {
        assertEquals("010-1234-5678", PhoneFormat.toPgFormat("+821012345678"));
    }

    @Test
    void 한국_일반전화는_0을_붙인_숫자열로_바꾼다() {
        // 02-1234-5678 → +82 2 1234 5678. 자릿수가 제각각이라 하이픈은 넣지 않는다.
        assertEquals("0212345678", PhoneFormat.toPgFormat("+82212345678"));
    }

    @Test
    void 해외번호는_국가번호를_유지한_E164_그대로_내보낸다() {
        assertEquals("+14155551234", PhoneFormat.toPgFormat("+14155551234"));
        assertEquals("+819012345678", PhoneFormat.toPgFormat("+819012345678"));
    }

    @Test
    void 하이픈_공백이_섞여_저장돼도_정규화한다() {
        assertEquals("010-1234-5678", PhoneFormat.toPgFormat("+82 10-1234-5678"));
        assertEquals("+14155551234", PhoneFormat.toPgFormat("+1 (415) 555-1234"));
    }

    @Test
    void 번호가_없으면_null을_돌려준다() {
        assertNull(PhoneFormat.toPgFormat(null));
        assertNull(PhoneFormat.toPgFormat("   "));
    }

    @Test
    void E164가_아닌_값은_그대로_둔다() {
        // 방어적: 과거 데이터나 수기 입력이 섞여도 던지지 않는다(결제 흐름을 깨뜨리지 않는다).
        assertEquals("01012345678", PhoneFormat.toPgFormat("01012345678"));
    }
}
