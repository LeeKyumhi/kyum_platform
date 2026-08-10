package com.guidematch.knowledge;

import java.text.Normalizer;

/**
 * 장소명 정규화 — 해결 사다리 2단(이름 일치)의 기준.
 *
 * <p>Codex가 보내는 {@code name_normalized}는 <b>믿지 않는다</b>. 매칭 키를 외부 에이전트가
 * 정하게 두면 프롬프트가 바뀌는 순간 같은 장소가 다른 키로 들어와 자산이 조용히 쪼개진다.
 * 항상 원본 {@code name_raw}를 여기서 다시 정규화해 쓴다.
 */
public final class PlaceNames {

    private PlaceNames() {}

    /**
     * 소문자화 + 유니코드 정규화 후 한글·영숫자만 남긴다.
     * "어니언(성수점)" · "어니언 성수점" · "Onion (Seongsu)" 같은 표기 흔들림을 흡수한다.
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase();
        StringBuilder sb = new StringBuilder(nfkc.length());
        for (int i = 0; i < nfkc.length(); i++) {
            char c = nfkc.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }
}
