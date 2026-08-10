package com.guidematch.knowledge;

/**
 * 장소 종류 — 코스 슬롯 매칭과 "여행자 무관 장소 배제"를 동시에 한다.
 *
 * <p><b>⚠ 값을 추가하려면 DB 제약을 먼저 고쳐야 한다 — 실측 확인(2026-08-09).</b>
 * Hibernate 6이 {@code @Enumerated(EnumType.STRING)} 컬럼에 CHECK 제약을 자동 생성하고
 * ({@code places_place_kind_check}), {@code columnDefinition}을 줘도 막히지 않는다.
 * {@code ddl-auto: update}는 최초 생성 이후 그 제약을 <b>절대 고쳐주지 않는다.</b>
 * 값을 늘린 채 적재하면 제약 위반으로 죽는데, 그게 권한 오류나 매핑 오류처럼 보인다.
 *
 * <pre>
 *   ALTER TABLE places DROP CONSTRAINT places_place_kind_check;
 *   -- 앱을 다시 띄우면 Hibernate가 새 값 목록으로 다시 만든다
 * </pre>
 *
 * 그래서 지금 쓰지 않는 {@code NATURE}까지 처음부터 넣어 뒀다.
 */
public enum PlaceKind {

    ATTRACTION,
    CULTURE,
    NATURE,
    FOOD,
    CAFE,
    MARKET,

    /** 쇼핑 — 여행 코스의 정차지로는 넣지 않는다. */
    SHOP,
    /** 숙박 — 코스는 하루 동선이라 숙소가 정차지가 될 이유가 없다. */
    LODGING,
    /**
     * 축제·공연·행사. <b>장소가 아니라 사건이다.</b>
     * 내년엔 없어질 것을 코스에 올리면 추천 자체가 거짓이 된다.
     * 레지스트리에는 남기되(지우면 다음 수집이 그대로 다시 넣는다) 정차지 후보에서만 뺀다.
     */
    EVENT,
    /** 은행·학교·병원 등 여행자 무관, 그리고 판정 불가. */
    OTHER;

    /**
     * 화면에 보일 한국어 라벨.
     *
     * <p>TourAPI 장소의 {@code category_raw}는 {@code A02>A0206>A02060500} 같은 <b>분류코드</b>라
     * 그대로 내보내면 사용자에게 코드가 보이고, 번역 API까지 그 코드를 번역하려 든다.
     * 코드를 쓰는 소스에 대해서는 이 라벨이 표시용 카테고리가 된다.
     * (Kakao 장소는 {@code 음식점 > 카페 > 커피전문점}처럼 읽을 수 있는 경로라 원문을 쓴다.)
     */
    public String koLabel() {
        return switch (this) {
            case ATTRACTION -> "관광명소";
            case CULTURE    -> "문화시설";
            case NATURE     -> "자연";
            case FOOD       -> "음식점";
            case CAFE       -> "카페";
            case MARKET     -> "시장";
            case SHOP       -> "쇼핑";
            case LODGING    -> "숙박";
            case EVENT      -> "행사";
            case OTHER      -> "기타";
        };
    }

    /** 코스 정차지가 될 수 있는가. */
    public boolean isStopCandidate() {
        return switch (this) {
            case ATTRACTION, CULTURE, NATURE, FOOD, CAFE, MARKET -> true;
            case SHOP, LODGING, EVENT, OTHER -> false;
        };
    }
}
