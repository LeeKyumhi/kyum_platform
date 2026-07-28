package com.guidematch.payment;

/**
 * 저장된 구매자 연락처(E.164, 예 "+821012345678")를 PG 결제창이 받는 형태로 바꾼다.
 *
 * 스마트로 공식문서는 구매자 연락처를 "결제창 호출 시 필수"로만 규정하고 형식 제약은 명시하지
 * 않는다(예시가 "010-0000-1234"). 그래서 국내 번호는 문서 예시와 같은 국내 표기로 내보내
 * 통과 확률을 높이고, 해외 번호는 국가번호가 살아 있어야 의미가 있으므로 E.164 그대로 둔다.
 *
 * 형식이 이상해도 예외를 던지지 않는다 — 연락처 표기 때문에 결제 흐름이 깨지면 안 된다.
 */
public final class PhoneFormat {

    private PhoneFormat() {}

    private static final String KR = "+82";

    public static String toPgFormat(String stored) {
        if (stored == null) return null;
        String compact = stored.replaceAll("[\\s\\-()]", "");
        if (compact.isBlank()) return null;
        if (!compact.startsWith(KR)) return compact;

        // 국내 번호: +82 뒤의 국내 가입자번호에 0을 붙여 국내 표기로 되돌린다.
        String national = "0" + compact.substring(KR.length());
        // 휴대폰(010-XXXX-XXXX)만 문서 예시와 같은 하이픈 표기로. 지역번호는 자릿수가 제각각이라 그대로 둔다.
        if (national.length() == 11 && national.startsWith("01")) {
            return national.substring(0, 3) + "-" + national.substring(3, 7) + "-" + national.substring(7);
        }
        return national;
    }
}
