package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소 종류 분류 — 정차지 후보를 정하는 유일한 기준.
 *
 * <p>여기 쓰인 category_raw 문자열은 전부 <b>실제 DB에 들어 있는 값</b>이다(2026-08-09 실측).
 * 지어낸 입력으로 테스트하면 실제 데이터가 OTHER로 쏟아져도 초록불이 켜진다.
 */
class PlaceKindsTest {

    // ── Kakao 카테고리 경로 (실측값) ──────────────────────────────

    @Test
    void kakao_관광명소는_ATTRACTION() {
        assertThat(PlaceKinds.classify("여행 > 관광,명소", "덕수궁")).isEqualTo(PlaceKind.ATTRACTION);
        assertThat(PlaceKinds.classify("여행 > 관광,명소 > 문화유적 > 고궁,궁", "덕수궁")).isEqualTo(PlaceKind.ATTRACTION);
        assertThat(PlaceKinds.classify("여행 > 관광,명소 > 케이블카", "남산케이블카")).isEqualTo(PlaceKind.ATTRACTION);
        assertThat(PlaceKinds.classify("여행 > 관광,명소 > 전망대", "N서울타워")).isEqualTo(PlaceKind.ATTRACTION);
    }

    @Test
    void kakao_문화시설은_CULTURE() {
        assertThat(PlaceKinds.classify("문화,예술 > 문화시설 > 박물관", "한국은행 화폐박물관"))
                .isEqualTo(PlaceKind.CULTURE);
    }

    @Test
    void kakao_시장은_MARKET() {
        assertThat(PlaceKinds.classify("가정,생활 > 시장", "남대문시장")).isEqualTo(PlaceKind.MARKET);
    }

    @Test
    void kakao_음식점과_카페를_구분한다() {
        assertThat(PlaceKinds.classify("음식점 > 한식 > 곰탕", "하동관")).isEqualTo(PlaceKind.FOOD);
        assertThat(PlaceKinds.classify("음식점 > 카페 > 커피전문점", "어니언 성수")).isEqualTo(PlaceKind.CAFE);
    }

    /** 먹자골목은 음식이 아니라 거리다 — "음식점" 접두가 없으므로 관광명소로 남아야 한다. */
    @Test
    void kakao_먹자골목은_ATTRACTION() {
        assertThat(PlaceKinds.classify("여행 > 관광,명소 > 테마거리 > 먹자골목", "명동먹자골목"))
                .isEqualTo(PlaceKind.ATTRACTION);
    }

    /** 여행자 무관 장소 배제 — 중구 40건에 실제로 섞여 있던 것들이다. */
    @Test
    void kakao_은행과_학교는_OTHER() {
        assertThat(PlaceKinds.classify("금융,보험 > 은행", "우리은행")).isEqualTo(PlaceKind.OTHER);
        assertThat(PlaceKinds.classify("교육,학문 > 학교 > 대학교", "동국대학교")).isEqualTo(PlaceKind.OTHER);
    }

    // ── TourAPI 분류코드 (실측값) ────────────────────────────────

    @Test
    void tourApi_역사관광지는_ATTRACTION() {
        assertThat(PlaceKinds.classify("A02>A0201>A02010700", "경성 부민관 폭탄 의거지"))
                .isEqualTo(PlaceKind.ATTRACTION);
        assertThat(PlaceKinds.classify("A02>A0201>A02010400", "관훈동 민씨 가옥"))
                .isEqualTo(PlaceKind.ATTRACTION);
    }

    @Test
    void tourApi_문화시설은_CULTURE() {
        assertThat(PlaceKinds.classify("A02>A0206>A02060500", "간송미술관(서울 보화각)"))
                .isEqualTo(PlaceKind.CULTURE);
        assertThat(PlaceKinds.classify("A02>A0206>A02060600", "국립극장")).isEqualTo(PlaceKind.CULTURE);
    }

    /** ★ 축제·공연행사 = EVENT. 내년엔 없어질 것을 코스에 올리지 않기 위한 유일한 방어선. */
    @Test
    void tourApi_축제와_공연행사는_EVENT() {
        assertThat(PlaceKinds.classify("A02>A0207>A02070200", "게임문화축제")).isEqualTo(PlaceKind.EVENT);
        assertThat(PlaceKinds.classify("A02>A0208>A02081300", "가을 , 명동으로")).isEqualTo(PlaceKind.EVENT);
    }

    @Test
    void tourApi_음식점은_FOOD_카페는_CAFE() {
        assertThat(PlaceKinds.classify("A05>A0502>A05020100", "금돼지식당")).isEqualTo(PlaceKind.FOOD);
        assertThat(PlaceKinds.classify("A05>A0502>A05020400", "개화")).isEqualTo(PlaceKind.FOOD);
        assertThat(PlaceKinds.classify("A05>A0502>A05020900", "차 마시는 뜰")).isEqualTo(PlaceKind.CAFE);
    }

    @Test
    void tourApi_쇼핑은_SHOP이지만_이름에_시장이_있으면_MARKET() {
        assertThat(PlaceKinds.classify("A04>A0401>A04010600", "금강제화 명동본점")).isEqualTo(PlaceKind.SHOP);
        assertThat(PlaceKinds.classify("A04>A0401>A04010200", "남대문시장")).isEqualTo(PlaceKind.MARKET);
    }

    @Test
    void tourApi_자연은_NATURE_숙박은_LODGING() {
        assertThat(PlaceKinds.classify("A01>A0101>A01010400", "북한산")).isEqualTo(PlaceKind.NATURE);
        assertThat(PlaceKinds.classify("B02>B0201>B02010100", "신라호텔")).isEqualTo(PlaceKind.LODGING);
    }

    // ── 판정 불가 ────────────────────────────────────────────────

    /** 애매하면 OTHER다. 잘못 분류된 장소가 정차지로 나가는 것보다 안 나가는 게 낫다. */
    @Test
    void 알수없거나_비어있으면_OTHER() {
        assertThat(PlaceKinds.classify(null, "이름만 있는 곳")).isEqualTo(PlaceKind.OTHER);
        assertThat(PlaceKinds.classify("", "빈 문자열")).isEqualTo(PlaceKind.OTHER);
        assertThat(PlaceKinds.classify("Z99>Z9901", "없는 코드")).isEqualTo(PlaceKind.OTHER);
        assertThat(PlaceKinds.classify("부동산 > 아파트", "래미안")).isEqualTo(PlaceKind.OTHER);
    }

    // ── 정차지 후보 자격 ──────────────────────────────────────────

    @Test
    void 정차지_후보는_여섯_종류뿐이다() {
        assertThat(Arrays.stream(PlaceKind.values()).filter(PlaceKind::isStopCandidate).toList())
                .containsExactlyInAnyOrder(PlaceKind.ATTRACTION, PlaceKind.CULTURE,
                        PlaceKind.NATURE, PlaceKind.FOOD, PlaceKind.CAFE, PlaceKind.MARKET);
    }

    @Test
    void 쇼핑_숙박_행사_기타는_정차지가_될_수_없다() {
        assertThat(PlaceKind.SHOP.isStopCandidate()).isFalse();
        assertThat(PlaceKind.LODGING.isStopCandidate()).isFalse();
        assertThat(PlaceKind.EVENT.isStopCandidate()).isFalse();
        assertThat(PlaceKind.OTHER.isStopCandidate()).isFalse();
    }
}
