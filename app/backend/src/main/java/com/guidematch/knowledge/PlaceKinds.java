package com.guidematch.knowledge;

import java.util.regex.Pattern;

/**
 * {@code category_raw} → {@link PlaceKind} 판정. <b>이 규칙이 존재하는 유일한 곳이다.</b>
 *
 * <p>적재({@link IngestService})와 백필({@link PlaceKindBackfill})이 같은 메서드를 부른다.
 * SQL로 백필하면 규칙이 두 곳에 생기고, 두 경로가 다르게 분류하는 순간 어느 쪽이 틀렸는지
 * 알아낼 방법이 없다.
 *
 * <p><b>판정은 결정론적이다 — LLM을 쓰지 않는다.</b> 추출기는 {@code category_raw}를 원문
 * 그대로 실어 보내기만 한다. 분류 키를 외부 에이전트가 정하게 두면 프롬프트가 바뀌는 순간
 * 같은 장소가 다른 종류로 들어온다({@link PlaceNames}에서 이미 확립한 원칙).
 *
 * <p><b>입력이 {@code category_raw} + {@code name_ko}뿐인 이유</b>: {@code places}에는
 * {@code source_kind} 컬럼이 없다. 백필은 DB 행만 보고 분류해야 하므로 소스를 인자로 받을 수 없다.
 * TourAPI 분류코드는 {@code A02>A0206>A02060500} 모양이라 문자열만으로 Kakao 경로와 구분된다.
 *
 * <p><b>⚠ TourAPI는 {@code contentTypeId}를 보내지 않는다.</b> 계약·스키마·실제 JSONL 어디에도
 * 없고 {@code places}에도 저장되지 않는다. 분류를 그 필드에 걸면 기존 행 백필이 원리상 불가능해진다.
 * 실제로 실려 오는 것은 {@code cat1>cat2>cat3} 분류코드이며 이 클래스는 그것만 본다.
 */
public final class PlaceKinds {

    private PlaceKinds() {}

    /** TourAPI 대분류 코드: 영문 1자 + 숫자 2자 (A01 자연 · A02 인문 · A03 레포츠 · A04 쇼핑 · A05 음식 · B02 숙박) */
    private static final Pattern TOUR_API_CAT = Pattern.compile("^[A-C]\\d{2}(>.*)?$");

    /**
     * TourAPI 분류코드인가({@code A02>A0206>A02060500}).
     *
     * <p>표시용으로 쓸 수 없는 문자열이라는 뜻이다 — 소비 쪽에서 이걸 그대로 내보내면
     * 사용자에게 코드가 보이고 번역 API도 코드를 번역하려 든다.
     */
    public static boolean isTourApiCategoryCode(String categoryRaw) {
        return categoryRaw != null && TOUR_API_CAT.matcher(categoryRaw.trim()).matches();
    }

    public static PlaceKind classify(String categoryRaw, String nameKo) {
        String c = categoryRaw == null ? "" : categoryRaw.trim();
        String n = nameKo == null ? "" : nameKo;
        if (c.isEmpty()) return PlaceKind.OTHER;
        return TOUR_API_CAT.matcher(c).matches() ? fromTourApi(c, n) : fromKakao(c, n);
    }

    /**
     * TourAPI cat1&gt;cat2&gt;cat3. cat2까지만 보면 충분하고, 카페만 cat3(A05020900)로 갈린다.
     */
    private static PlaceKind fromTourApi(String c, String nameKo) {
        String cat1 = c.length() >= 3 ? c.substring(0, 3) : c;
        String cat2 = cat2Of(c);

        if (cat1.equals("A01")) return PlaceKind.NATURE;
        if (cat1.equals("B02")) return PlaceKind.LODGING;

        if (cat1.equals("A02")) {
            return switch (cat2) {
                // 축제(A0207)·공연/행사(A0208)는 장소가 아니라 사건이다
                case "A0207", "A0208" -> PlaceKind.EVENT;
                case "A0206"          -> PlaceKind.CULTURE;   // 문화시설
                // 역사(A0201)·휴양(A0202)·체험(A0203)·건축조형물(A0205)
                case "A0201", "A0202", "A0203", "A0205" -> PlaceKind.ATTRACTION;
                default -> PlaceKind.OTHER;                   // 산업관광지(A0204) 등
            };
        }
        if (cat1.equals("A04")) {
            // 쇼핑이지만 이름이 시장이면 시장이다 — 남대문·광장시장이 여기로 들어온다
            return nameKo.contains("시장") ? PlaceKind.MARKET : PlaceKind.SHOP;
        }
        if (cat1.equals("A05")) {
            return cat3Of(c).equals("A05020900") ? PlaceKind.CAFE : PlaceKind.FOOD; // 카페/전통찻집
        }
        return PlaceKind.OTHER; // A03 레포츠 등 — 애매하면 OTHER
    }

    /**
     * Kakao 카테고리 경로("음식점 &gt; 카페 &gt; 커피전문점").
     * <b>순서가 규칙이다</b> — 카페를 음식점보다 먼저 보지 않으면 모든 카페가 FOOD가 된다.
     */
    private static PlaceKind fromKakao(String c, String nameKo) {
        if (c.contains("카페") || c.contains("커피")) return PlaceKind.CAFE;
        if (c.startsWith("음식점"))                   return PlaceKind.FOOD;
        if (c.contains("시장"))                       return PlaceKind.MARKET;
        if (c.contains("문화,예술"))                   return PlaceKind.CULTURE;
        if (c.startsWith("여행"))                     return PlaceKind.ATTRACTION;
        if (c.contains("숙박"))                       return PlaceKind.LODGING;
        return PlaceKind.OTHER;
    }

    private static String cat2Of(String c) {
        String[] parts = c.split(">");
        return parts.length >= 2 ? parts[1].trim() : "";
    }

    private static String cat3Of(String c) {
        String[] parts = c.split(">");
        return parts.length >= 3 ? parts[2].trim() : "";
    }
}
