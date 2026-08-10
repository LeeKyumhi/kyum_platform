# 레지스트리 기반 코스 추천 — 구현 계획

> ## ✅ 실행 완료 (2026-08-09) — 12개 태스크 전부
>
> **178 tests / 0 failures** · 실기동 스모크 5/5(Kakao 켬·끔) · `ingest.sh` **58.1초 → 12.0초** ·
> `place_kind` NULL 0 · `address_ko` 53/53 · `source="registry"` 정차지 5곳 중 4곳.
>
> **계획이 틀렸던 곳 3가지는 실측이 바로잡았다** — 아래 본문은 착수 시점 그대로 두었고,
> 무엇이 왜 달라졌는지는 **`app/PROGRESS.md` 최상단**에 8가지로 정리했다:
> ① `contentTypeId`는 파이프라인에 없다(분류는 `category_raw` cat 코드로) ·
> ② `address_ko`는 재수집 없이 채워졌다 ·
> ③ `columnDefinition`은 Hibernate enum CHECK 제약을 막지 못한다(계획은 반대로 적었다).
>
> 남은 것: 커밋 판단, 그리고 완료조건 후반부(`detailCommon2` 유래 인사이트)를 증명할 v4 재수집.


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/courses/recommend`의 정차지를 Kakao 실시간 검색이 아니라 우리 `places` 레지스트리가 정하게 만들어, 축적된 인사이트가 실제로 코스에 붙게 한다.

**Architecture:** `places`에 `place_kind`(슬롯 매칭 + 무관 장소 배제)와 `address_ko`를 추가하고, 기존 행을 백필한다. 신규 `CoursePlanner`가 레지스트리에서 먼저 정차지를 뽑고 모자란 슬롯만 Kakao로 채운다. 그 다음 적재 파이프라인을 재수집에 견디게 만든다(왕복 축소·중단 감지·의심 구간·계약 v4).

**Tech Stack:** Spring Boot 3.3.5 / Java 21 / JPA(`ddl-auto: update`) / Supabase Postgres(Sydney) / JUnit 5 + Mockito + AssertJ / Gradle

**설계서:** `docs/superpowers/specs/2026-08-06-registry-driven-course-recommendation-design.md`
**브랜치:** `feat/travel-knowledge-registry` (main에서 분기, +3 커밋)

---

## Global Constraints

- **새 npm 패키지·새 Gradle 의존성 금지.** 이 계획은 의존성을 하나도 추가하지 않는다.
- **커밋은 사용자가 명시적으로 요청할 때만 한다.** 각 태스크 마지막의 커밋 단계는 사용자 승인 후 실행한다(`feedback_no_unsolicited_commits`).
- **`ddl-auto: update`는 additive-only이고 조용히 실패한다.** NOT NULL 컬럼을 추가하면 Hibernate가 DEFAULT 없이 `add column X not null`을 내고 Postgres가 거부하는데, **컬럼이 안 생긴 채 앱은 정상 기동한다.** 이 계획의 새 컬럼 2개는 **반드시 nullable**이다.
- **`@Enumerated(EnumType.STRING)`은 Hibernate 6이 CHECK 제약을 자동 생성하고, 나중에 enum 값이 늘어도 `ddl-auto: update`가 제약을 고쳐주지 않는다.** 그래서 `PlaceKind`는 처음부터 값 10개를 다 넣는다(지금 안 쓰는 `NATURE`도 포함).
- **원격 DB N+1 금지.** Supabase 풀러가 Sydney(왕복 ~250ms). 루프 안 단건 조회 절대 금지 — 배치 조회(`IN (...)`, `findAllById`)만 쓴다.
- **주소는 번역하지 않고 한국어 그대로 보관**한다(CLAUDE.md 규약 — 택시·지도 앱 편의).
- **테스트는 전부 목 기반 단위 테스트다.** 이 리포에는 `@SpringBootTest`가 하나도 없고 이번에도 만들지 않는다.
- **분류 규칙은 코드 한 곳에만 존재한다.** SQL로 백필하지 않는다 — 매핑이 두 곳에 생기면 적재 경로와 백필이 다르게 분류하는 순간 원인 추적이 불가능해진다.
- **잘못된 병합은 되돌릴 수 없고 실패가 조용하다.** 애매하면 항상 미해결 보관함이다.
- **Phase B가 끝나기 전에는 `codex exec` 수집을 돌리지 않는다.** 역방향 시딩이 의심 구간(Task 9) 없이 돌면 레지스트리를 다시 오염시킨다.

---

## 착수 전 실측값 (2026-08-09 확인)

이 값들이 완료 판정의 기준선이다. 기억이 아니라 측정값이다.

| 항목 | 값 |
|---|---|
| `places` | 53 — **전부 `Seoul`/`중구`**. kakao 40 / tour_api 13, **양쪽 ID 보유 0** |
| `place_insights` | 9 — 전부 `insight-v2`, `detailIntro2` 유래 9 / `detailCommon2` 유래 **0** |
| 인사이트 보유 장소 | 6곳 (축제 3곳 6건 + 진짜 장소 3곳 3건) |
| `place_aliases` / `place_candidates_unresolved` / `recommendation_signals` | 0 / 0 / 0 |
| `ingest_runs` | 4건 전부 `COMPLETED` (과거 유실분은 재적재로 복구됨) |
| `places` 컬럼 | `place_kind`·`address_ko` **없음** |

**설계서와 실제가 다른 곳 2개 — 이 계획은 실제를 따른다:**

1. **`contentTypeId`는 파이프라인에 존재하지 않는다.** 설계서 §3.1·§5.1이 `contentTypeId`로 분류·거절하지만, TourAPI 레코드가 싣는 건 `category_raw = "A02>A0206>A02060500"` 형태의 **분류코드**다(JSONL·DB 양쪽 확인). 분류는 `category_raw`로만 한다 — 그래야 기존 13행 백필이 가능하다.
2. **`address_raw`는 JSONL에 이미 전부 들어 있다**(kakao 40/40, tour 13/13). `PlaceClue`에 필드가 없어 버려질 뿐이다. 필드만 추가하면 **재수집 없이 기존 run 디렉터리 재적재만으로 주소가 채워진다**(Task 12).

**사용자 결정 (뒤집지 말 것):**
- 축제는 **삭제하지 않는다.** `place_kind = EVENT`로 분류해 정차지 후보에서만 뺀다. 데이터가 남아 되돌릴 수 있고, 인사이트 9건이 3건으로 줄지 않는다.
- 범위는 **Phase A + Phase B 전부.** Task 7이 중간 체크포인트다.

---

## File Structure

**신규 (backend main)**

| 파일 | 책임 |
|---|---|
| `knowledge/PlaceKind.java` | 장소 종류 enum 10개 |
| `knowledge/PlaceKinds.java` | `category_raw` → `PlaceKind` 분류 **단일 소스**. 적재·백필이 같이 쓴다 |
| `knowledge/PlaceKindBackfill.java` | `place_kind IS NULL` 행 일회성 백필 `ApplicationRunner` |
| `knowledge/RegistrySnapshot.java` | places·aliases·소스해시 인메모리 스냅샷 — **전체**를 올린다 (Task 10) |
| `geo/CoursePlanner.java` | 레지스트리 우선 정차지 선정 + Kakao 폴백 + 중복 제거 + 구 앵커 |

**수정**

| 파일 | 무엇 |
|---|---|
| `knowledge/Place.java` | `placeKind`·`addressKo` 필드, `enrichMissing` 확장 |
| `knowledge/PlaceClue.java` | `addressRaw` 필드 |
| `knowledge/PlaceRepository.java` | `findCandidates`, `findByPlaceKindIsNull` |
| `knowledge/PlaceInsightLookup.java` | `byPlaceIds` 추가 |
| `knowledge/IngestService.java` | `addressRaw` 배선, 분류 호출, `eachLine` reject 배선, STARTED 런 경고 |
| `knowledge/PlaceResolver.java` | 2차 조회(괄호 제거), 의심 구간, 스냅샷 지원 |
| `knowledge/SignalRecorder.java` | `StopRef` 기반 기록 (레지스트리 정차지는 place_id 직접) |
| `geo/CourseRecommendController.java` | `CoursePlanner` 위임, `Stop.source` 추가 |
| `resources/application-ingest.yml` | `suspect-radius-meters`, JDBC 배치 |
| `docs/ingest/CONTRACT.md` · `codex-ingest-prompt.md` · `sources.yml` | 계약 3조항 + 프롬프트 v4 + `searchKeyword2` |

**테스트 (신규/확장)**

`PlaceKindsTest`(신규) · `PlaceKindBackfillTest`(신규) · `CoursePlannerTest`(신규) · `PlaceResolverTest`(확장) · `PlaceInsightLookupTest`(확장) · `IngestServiceTest`(확장)

**공통 실행 명령**

```bash
cd ~/kyum_platform/app/backend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
gradle test --tests 'com.guidematch.knowledge.*'   # 지식 패키지만
gradle test                                        # 전체
```

---

# Phase A — 레지스트리가 정차지를 정한다 (오늘 검증 가능)

---

### Task 1: `PlaceKind` enum + `PlaceKinds` 분류기

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceKind.java`
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceKinds.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceKindsTest.java`

**Interfaces:**
- Produces: `enum PlaceKind { ATTRACTION, CULTURE, NATURE, FOOD, CAFE, MARKET, SHOP, LODGING, EVENT, OTHER }`, `PlaceKind.isStopCandidate()`, `PlaceKinds.classify(String categoryRaw, String nameKo) → PlaceKind` (절대 null을 반환하지 않는다)
- Consumes: 없음

**왜 `sourceKind`를 인자로 받지 않는가:** `Place`에는 `source_kind` 컬럼이 없다. 백필은 DB 행만 보고 분류해야 하므로 분류기의 입력은 `category_raw` + `name_ko`뿐이어야 한다. TourAPI 분류코드는 `^[A-C]\d{2}(>.+)?` 모양이라 문자열만으로 구분된다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`app/backend/src/test/java/com/guidematch/knowledge/PlaceKindsTest.java`:

```java
package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;

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
    void 정차지_후보는_다섯_종류뿐이다() {
        assertThat(java.util.Arrays.stream(PlaceKind.values())
                .filter(PlaceKind::isStopCandidate).toList())
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
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `cd ~/kyum_platform/app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests 'com.guidematch.knowledge.PlaceKindsTest'`
Expected: 컴파일 실패 — `cannot find symbol: class PlaceKind`

- [ ] **Step 3: `PlaceKind`를 만든다**

```java
package com.guidematch.knowledge;

/**
 * 장소 종류 — 코스 슬롯 매칭과 "여행자 무관 장소 배제"를 동시에 한다.
 *
 * <p><b>⚠ 값을 추가할 때 주의</b>: {@code @Enumerated(EnumType.STRING)} 컬럼에 Hibernate 6이
 * CHECK 제약을 자동 생성하는데, {@code ddl-auto: update}는 최초 생성 이후 제약을 <b>고쳐주지 않는다.</b>
 * 나중에 값을 늘리면 적재 도중 제약 위반으로 죽고, 그게 권한 오류나 매핑 오류처럼 보인다.
 * 그래서 지금 안 쓰는 값({@code NATURE})까지 처음부터 넣어 뒀다. 그래도 더 늘려야 한다면
 * Postgres에서 제약을 직접 {@code alter} 해야 한다.
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

    /** 코스 정차지가 될 수 있는가. */
    public boolean isStopCandidate() {
        return switch (this) {
            case ATTRACTION, CULTURE, NATURE, FOOD, CAFE, MARKET -> true;
            case SHOP, LODGING, EVENT, OTHER -> false;
        };
    }
}
```

- [ ] **Step 4: `PlaceKinds` 분류기를 만든다**

```java
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
 */
public final class PlaceKinds {

    private PlaceKinds() {}

    /** TourAPI 대분류 코드: 영문 1자 + 숫자 2자 (A01 자연 · A02 인문 · A03 레포츠 · A04 쇼핑 · A05 음식 · B02 숙박) */
    private static final Pattern TOUR_API_CAT = Pattern.compile("^[A-C]\\d{2}(>.*)?$");

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
```

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.PlaceKindsTest'`
Expected: PASS (전부 초록)

- [ ] **Step 6: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/PlaceKind.java \
        app/backend/src/main/java/com/guidematch/knowledge/PlaceKinds.java \
        app/backend/src/test/java/com/guidematch/knowledge/PlaceKindsTest.java
git commit -m "feat(knowledge): 장소 종류 분류기 — category_raw 결정론적 판정"
```

---

### Task 2: `places`에 `place_kind`·`address_ko` 추가 + 적재 배선

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/Place.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/PlaceClue.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/IngestService.java` (`ingestPlace`)
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceResolverTest.java` (확장)

**Interfaces:**
- Consumes: `PlaceKinds.classify(String, String)` (Task 1)
- Produces:
  - `Place` 생성자에 `addressKo` 인자 추가 → `Place(String nameKo, String city, String district, Double lat, Double lng, String kakaoPlaceId, String tourApiContentId, String category, String addressKo)`
  - `Place.getPlaceKind()`, `Place.getAddressKo()`
  - `Place.enrichMissing(Double lat, Double lng, String kakaoPlaceId, String tourApiContentId, String category, String city, String district, String addressKo)`
  - `PlaceClue`에 `addressRaw` 필드 (기존 필드 뒤, `sourceKind` 앞)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceResolverTest.java` 안에 추가한다. 기존 헬퍼 `clue(...)`가 인자 순서를 정하므로 **헬퍼도 같이 고쳐야 한다** — `PlaceClue` 생성자에 `addressRaw`를 넣으면 기존 호출이 전부 깨진다. 헬퍼 3개(`existing`, `clue` 2종)를 아래처럼 바꾼다:

```java
    private Place existing(String name, Double lat, Double lng, String kakaoId) {
        Place p = new Place(name, "seoul", "성동구", lat, lng, kakaoId, null, "카페",
                "서울 성동구 아차산로9길 8");
        when(placeRepo.findByNameNormalized(PlaceNames.normalize(name))).thenReturn(List.of(p));
        return p;
    }

    private PlaceClue clue(String name, List<String> aliases, Double lat, Double lng, String kakaoId) {
        return new PlaceClue(name, aliases, "seoul", "성동구", lat, lng,
                kakaoId, null, "음식점 > 카페 > 커피전문점", "서울 성동구 아차산로9길 8", "kakao_local");
    }
```

그리고 새 테스트 3개:

```java
    // ── 종류·주소 (Task 2) ──────────────────────────────────────────

    /** 새 노드를 만드는 순간 종류가 정해진다. 나중에 채우면 그 사이의 조회가 전부 후보를 놓친다. */
    @Test
    void 새로_만든_장소는_종류와_주소를_갖는다() {
        noNameMatches();
        when(placeRepo.findByKakaoPlaceId("k1")).thenReturn(Optional.empty());
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        PlaceResolver.Resolution r = resolver.resolve(new PlaceClue(
                "어니언 성수", List.of(), "seoul", "성동구", 37.5444, 127.0374,
                "k1", null, "음식점 > 카페 > 커피전문점", "서울 성동구 아차산로9길 8", "kakao_local"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place().getPlaceKind()).isEqualTo(PlaceKind.CAFE);
        assertThat(r.place().getAddressKo()).isEqualTo("서울 성동구 아차산로9길 8");
    }

    /** 주소는 빈 칸일 때만 채운다 — 나중 소스가 먼저 들어온 권위 있는 값을 흔들면 안 된다. */
    @Test
    void 이미_주소가_있으면_덮어쓰지_않는다() {
        Place p = withId(existing("어니언 성수", 37.5444, 127.0374, "k1"), 7L);
        when(placeRepo.findByKakaoPlaceId("k1")).thenReturn(Optional.of(p));

        resolver.resolve(new PlaceClue("어니언 성수", List.of(), "seoul", "성동구",
                37.5444, 127.0374, "k1", null, "카페", "다른 주소", "tour_api"));

        assertThat(p.getAddressKo()).isEqualTo("서울 성동구 아차산로9길 8");
    }

    /** 주소가 비어 있던 기존 행은 새 단서로 채워진다 — 재적재만으로 주소가 붙는 근거. */
    @Test
    void 주소가_비어있으면_새_단서로_채운다() {
        Place p = withId(new Place("어니언 성수", "seoul", "성동구", 37.5444, 127.0374,
                "k1", null, "카페", null), 7L);
        when(placeRepo.findByKakaoPlaceId("k1")).thenReturn(Optional.of(p));

        resolver.resolve(new PlaceClue("어니언 성수", List.of(), "seoul", "성동구",
                37.5444, 127.0374, "k1", null, "카페", "서울 성동구 아차산로9길 8", "kakao_local"));

        assertThat(p.getAddressKo()).isEqualTo("서울 성동구 아차산로9길 8");
    }
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.PlaceResolverTest'`
Expected: 컴파일 실패 — `PlaceClue` 생성자 인자 수 불일치, `getPlaceKind()` 없음

- [ ] **Step 3: `PlaceClue`에 `addressRaw`를 넣는다**

`PlaceClue.java` — `category` 다음, `sourceKind` 앞에 한 줄:

```java
public record PlaceClue(
        String nameRaw,
        List<String> aliases,
        String city,
        String district,
        Double lat,
        Double lng,
        String kakaoPlaceId,
        String tourApiContentId,
        String category,
        /**
         * 한국어 주소 원문. 계약(place.schema.json)에는 1일차부터 있었는데 이 필드가 없어
         * 조용히 버려져 왔다. 번역하지 않는다 — 택시·지도 앱에 그대로 넣을 수 있어야 한다.
         */
        String addressRaw,
        String sourceKind
) {
```

(나머지 본문은 그대로 둔다.)

- [ ] **Step 4: `Place`에 컬럼 2개를 넣는다**

`Place.java` — `category` 필드 아래에 추가:

```java
    /**
     * 정차지 슬롯 매칭 + 여행자 무관 장소 배제. 적재·백필 모두 {@link PlaceKinds}가 정한다.
     *
     * <p><b>⚠ nullable이어야 한다.</b> {@code ddl-auto: update}로 NOT NULL 컬럼을 추가하면
     * Hibernate가 DEFAULT 없이 {@code add column ... not null}을 내고 Postgres가 거부하는데,
     * <b>컬럼이 안 생긴 채 앱은 정상 기동한다.</b> 기존 행은 {@link PlaceKindBackfill}이 채운다.
     *
     * <p>{@code columnDefinition}을 명시해 Hibernate가 enum CHECK 제약을 자동 생성하지 않게 한다.
     * 자동 생성되면 나중에 enum 값을 늘릴 때 적재가 제약 위반으로 죽는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "place_kind", length = 20, columnDefinition = "varchar(20)")
    private PlaceKind placeKind;

    /** 한국어 주소. 번역하지 않는다 (CLAUDE.md 규약 — 택시·지도 앱 편의). */
    @Column(name = "address_ko", columnDefinition = "TEXT")
    private String addressKo;
```

생성자에 `addressKo`를 추가하고 종류를 그 자리에서 정한다:

```java
    public Place(String nameKo, String city, String district,
                 Double lat, Double lng,
                 String kakaoPlaceId, String tourApiContentId, String category,
                 String addressKo) {
        this.nameKo = nameKo;
        this.nameNormalized = PlaceNames.normalize(nameKo);
        this.city = city;
        this.district = district;
        this.lat = lat;
        this.lng = lng;
        this.kakaoPlaceId = kakaoPlaceId;
        this.tourApiContentId = tourApiContentId;
        this.category = category;
        this.addressKo = addressKo;
        // 장소를 만드는 순간 종류가 정해진다 — 나중에 채우면 그 사이의 후보 조회가 전부 놓친다
        this.placeKind = PlaceKinds.classify(category, nameKo);
    }
```

`enrichMissing`에 인자 하나를 더하고 두 줄을 추가한다:

```java
    public void enrichMissing(Double lat, Double lng, String kakaoPlaceId,
                              String tourApiContentId, String category,
                              String city, String district, String addressKo) {
        boolean changed = false;
        if (this.lat == null && lat != null)                     { this.lat = lat; changed = true; }
        if (this.lng == null && lng != null)                     { this.lng = lng; changed = true; }
        if (isBlank(this.kakaoPlaceId) && notBlank(kakaoPlaceId)) { this.kakaoPlaceId = kakaoPlaceId; changed = true; }
        if (isBlank(this.tourApiContentId) && notBlank(tourApiContentId)) { this.tourApiContentId = tourApiContentId; changed = true; }
        if (isBlank(this.category) && notBlank(category))        { this.category = category; changed = true; }
        if (isBlank(this.city) && notBlank(city))                { this.city = city; changed = true; }
        if (isBlank(this.district) && notBlank(district))        { this.district = district; changed = true; }
        if (isBlank(this.addressKo) && notBlank(addressKo))      { this.addressKo = addressKo; changed = true; }
        // 종류가 비어 있고 이제 카테고리를 알게 됐다면 그때 정한다 (백필과 같은 규칙)
        if (this.placeKind == null && notBlank(this.category)) {
            this.placeKind = PlaceKinds.classify(this.category, this.nameKo);
            changed = true;
        }
        if (changed) this.updatedAt = Instant.now();
    }
```

게터 2개를 추가한다:

```java
    public PlaceKind getPlaceKind()     { return placeKind; }
    public String getAddressKo()        { return addressKo; }
```

- [ ] **Step 5: `PlaceResolver`·`IngestService` 호출부를 맞춘다**

`PlaceResolver.enrich()`:

```java
    private void enrich(Place p, PlaceClue clue) {
        p.enrichMissing(clue.lat(), clue.lng(),
                blankToNull(clue.kakaoPlaceId()), blankToNull(clue.tourApiContentId()),
                clue.category(), clue.city(), clue.district(), clue.addressRaw());
    }
```

`PlaceResolver.maybeCreate()`의 `new Place(...)`:

```java
        Place created = placeRepo.save(new Place(
                clue.nameRaw(), clue.city(), clue.district(),
                clue.lat(), clue.lng(),
                blankToNull(clue.kakaoPlaceId()), blankToNull(clue.tourApiContentId()),
                clue.category(), blankToNull(clue.addressRaw())));
```

`IngestService.ingestPlace()`의 `PlaceClue` 생성 — `category_raw` 다음에 `address_raw`를 읽는다:

```java
        PlaceClue clue = new PlaceClue(
                nameRaw,
                strings(n.path("aliases")),
                text(n.path("city"), null),
                text(n.path("district"), null),
                decimal(n.path("lat")),
                decimal(n.path("lng")),
                text(n.path("external_ids").path("kakao_place_id"), null),
                text(n.path("external_ids").path("tour_api_content_id"), null),
                text(n.path("category_raw"), null),
                text(n.path("address_raw"), null),
                sourceKind);
```

`IngestService.ingestInsight()`의 `place_ref` 단서 — 인사이트에는 주소가 없다:

```java
        PlaceResolver.Resolution r = resolver.resolve(new PlaceClue(
                nameRaw, List.of(),
                text(ref.path("city"), null), text(ref.path("district"), null),
                decimal(ref.path("lat")), decimal(ref.path("lng")),
                text(ref.path("external_ids").path("kakao_place_id"), null),
                text(ref.path("external_ids").path("tour_api_content_id"), null),
                null, null, sourceKind));
```

- [ ] **Step 6: 지식 패키지 전체 테스트를 돌린다**

Run: `gradle test --tests 'com.guidematch.knowledge.*'`
Expected: PASS. 실패하면 대개 `PlaceClue`/`Place` 생성자 인자 수를 안 고친 호출부다 — 컴파일 오류 메시지의 파일을 따라가면 된다.

- [ ] **Step 7: prod용 DDL을 적어 둔다**

dev는 `ddl-auto: update`가 컬럼을 만들지만 **prod는 `ddl-auto: none`이다.** 이 브랜치에는 아직 `docs/deploy/`가 없으므로(결제 브랜치가 만든 것이다) 지금은 계약 문서에 남긴다. `docs/ingest/CONTRACT.md` 끝에 한 절을 추가한다:

```sql
-- 레지스트리 기반 코스 추천 (2026-08-09). prod는 ddl-auto: none이라 손으로 실행해야 한다.
-- ⚠ 반드시 nullable — NOT NULL로 만들면 기존 행 때문에 실패한다.
ALTER TABLE places ADD COLUMN IF NOT EXISTS place_kind varchar(20);
ALTER TABLE places ADD COLUMN IF NOT EXISTS address_ko text;
-- place_kind는 SQL로 채우지 않는다. 앱 기동 시 PlaceKindBackfill이 적재와 같은 규칙으로 채운다.
```

> **머지 시 `docs/deploy/pre-deploy.sql`로 옮길 것.** 결제 브랜치가 STEP 7·8·9를 쓰고 있으므로 번호가 겹치지 않게 붙인다.

- [ ] **Step 8: 실기동으로 DDL을 확인한다 — 이 단계를 건너뛰면 Task 3이 조용히 아무것도 안 한다**

```bash
cd ~/kyum_platform/app/backend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
gradle bootRun
# "Started GuideMatchApplication" 확인 후 Ctrl-C
```

그 다음 컬럼이 **실제로 생겼는지** 확인한다(단위 테스트로는 절대 안 잡힌다):

```sql
SELECT column_name, data_type, is_nullable
  FROM information_schema.columns
 WHERE table_schema='public' AND table_name='places'
   AND column_name IN ('place_kind','address_ko');
```

Expected: 2행, 둘 다 `is_nullable = YES`. **0행이면 NOT NULL 함정을 밟은 것이다** — 엔티티의 `nullable`을 다시 확인할 것.

CHECK 제약이 붙었는지도 본다:

```sql
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
 WHERE conrelid = 'places'::regclass AND contype = 'c';
```

제약이 생겼다면 열거값 10개가 모두 들어 있는지 확인하고, 없으면 그대로 두면 된다(둘 다 정상 — 다만 어느 쪽인지 알아야 나중에 값을 늘릴 때 대응할 수 있다).

- [ ] **Step 9: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/
git commit -m "feat(knowledge): places에 place_kind·address_ko 추가 + 적재 배선"
```

---

### Task 3: 기존 53행 백필

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceKindBackfill.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/PlaceRepository.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceKindBackfillTest.java`

**Interfaces:**
- Consumes: `PlaceKinds.classify(String, String)` (Task 1), `Place.getPlaceKind()` (Task 2)
- Produces: `PlaceRepository.findByPlaceKindIsNull() → List<Place>`, `Place.assignKindIfMissing()`

**★ 이 태스크가 이 계획에서 가장 실패하기 쉬운 지점이다.** 백필을 빠뜨리면 `place_kind`가 전부 NULL → 레지스트리 후보 0건 → Kakao 폴백이 100% 채움 → **엔드포인트는 정상 응답하고 결과가 지금과 완전히 동일하며 "됐다"로 보인다.** 그래서 Task 7의 완료 판정이 "정차지가 있다"가 아니라 `source="registry"` 정차지 ≥ 1이다.

`@Profile("!ingest")`는 `UserFollowBackfill`에서 확립한 규칙이다 — 적재 배치가 앱 마이그레이션을 돌리면 안 되고, 적재 롤에는 권한도 없다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 기존 행 백필.
 *
 * <p>이게 안 돌면 레지스트리 후보가 0건이 되고, 그래도 <b>엔드포인트는 정상 응답한다</b>
 * (Kakao 폴백이 전부 채우므로). 조용한 실패라서 테스트로 못 박아 둔다.
 */
class PlaceKindBackfillTest {

    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final PlaceKindBackfill backfill = new PlaceKindBackfill(placeRepo);

    private static Place place(long id, String name, String category) {
        Place p = new Place(name, "Seoul", "중구", 37.5, 127.0, "k" + id, null, category, null);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "placeKind", null); // 컬럼이 갓 추가된 상태를 재현
        return p;
    }

    @Test
    void 종류가_비어있는_행을_적재와_같은_규칙으로_채운다() {
        when(placeRepo.findByPlaceKindIsNull()).thenReturn(List.of(
                place(1, "덕수궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁"),
                place(2, "남대문시장", "가정,생활 > 시장"),
                place(3, "게임문화축제", "A02>A0207>A02070200")));

        backfill.run(null);

        ArgumentCaptor<List<Place>> saved = ArgumentCaptor.forClass(List.class);
        verify(placeRepo).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Place::getPlaceKind)
                .containsExactly(PlaceKind.ATTRACTION, PlaceKind.MARKET, PlaceKind.EVENT);
    }

    /** 이미 값이 있는 행은 조회 자체에 안 걸린다 — 사람이 손으로 고친 값을 되돌리지 않는다. */
    @Test
    void 이미_종류가_있는_행은_건드리지_않는다() {
        Place already = place(4, "덕수궁", "여행 > 관광,명소");
        ReflectionTestUtils.setField(already, "placeKind", PlaceKind.CULTURE);
        when(placeRepo.findByPlaceKindIsNull()).thenReturn(List.of());

        backfill.run(null);

        verify(placeRepo, never()).saveAll(any());
        assertThat(already.getPlaceKind()).isEqualTo(PlaceKind.CULTURE);
    }

    /** 두 번째 기동에서 할 일이 없으면 쓰기도 없어야 한다 (매 기동 도는 러너다). */
    @Test
    void 채울_행이_없으면_저장하지_않는다() {
        when(placeRepo.findByPlaceKindIsNull()).thenReturn(List.of());
        backfill.run(null);
        verify(placeRepo, never()).saveAll(any());
    }

    /** 카테고리가 없는 행도 OTHER로 확정한다 — NULL로 남으면 매 기동 다시 조회된다. */
    @Test
    void 카테고리가_없으면_OTHER로_확정한다() {
        when(placeRepo.findByPlaceKindIsNull()).thenReturn(List.of(place(5, "이름만 있는 곳", null)));

        backfill.run(null);

        ArgumentCaptor<List<Place>> saved = ArgumentCaptor.forClass(List.class);
        verify(placeRepo).saveAll(saved.capture());
        assertThat(saved.getValue().get(0).getPlaceKind()).isEqualTo(PlaceKind.OTHER);
    }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.PlaceKindBackfillTest'`
Expected: 컴파일 실패 — `PlaceKindBackfill` 없음, `findByPlaceKindIsNull` 없음

- [ ] **Step 3: 리포지토리 메서드를 추가한다**

`PlaceRepository.java`에 추가:

```java
    /** 백필 대상 — 컬럼이 갓 추가돼 종류가 비어 있는 행. 평시에는 0건이라 비용이 없다. */
    List<Place> findByPlaceKindIsNull();
```

- [ ] **Step 4: `Place`에 종류 지정 메서드를 추가한다**

`Place.java`:

```java
    /**
     * 종류가 비어 있을 때만 채운다. 사람이 손으로 고친 값을 백필이 되돌리면 안 된다.
     *
     * @return 실제로 값이 바뀌었으면 true
     */
    public boolean assignKindIfMissing() {
        if (this.placeKind != null) return false;
        this.placeKind = PlaceKinds.classify(this.category, this.nameKo);
        this.updatedAt = Instant.now();
        return true;
    }
```

- [ ] **Step 5: 백필 러너를 만든다**

```java
package com.guidematch.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code place_kind}가 비어 있는 기존 행을 적재와 <b>같은 규칙</b>으로 채운다.
 *
 * <p><b>왜 SQL이 아닌가</b>: 매핑 규칙이 두 곳에 생기면 반드시 어긋나고, 적재 경로와 백필이
 * 다르게 분류하는 순간 어느 쪽이 틀렸는지 알아낼 방법이 없다. {@link PlaceKinds}만 부른다.
 *
 * <p><b>왜 {@code @Profile("!ingest")}인가</b>: 적재 배치가 앱 마이그레이션을 돌리는 건 그
 * 자체로 틀렸고, 적재 롤에는 권한도 없다({@code docs/ingest/db-role.sql}).
 * {@code UserFollowBackfill}이 이 규칙 없이 매 적재마다 돌던 사고가 있었다.
 *
 * <p>매 기동 도는 러너지만 평시 비용은 조회 1회(0건)다. 지우지 않는 이유: 다음 수집이
 * 새 장소를 넣을 때 종류는 적재가 채우므로 이 러너가 할 일은 원래 0건이어야 정상이고,
 * 0건이 아니면 그게 곧 <b>적재 경로에 구멍이 났다는 신호</b>다.
 */
@Component
@Profile("!ingest")
public class PlaceKindBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaceKindBackfill.class);

    private final PlaceRepository placeRepo;

    public PlaceKindBackfill(PlaceRepository placeRepo) {
        this.placeRepo = placeRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Place> pending = placeRepo.findByPlaceKindIsNull();
        if (pending.isEmpty()) return;

        List<Place> changed = pending.stream().filter(Place::assignKindIfMissing).toList();
        if (changed.isEmpty()) return;

        placeRepo.saveAll(changed);
        log.info("place_kind 백필 {}건 — {}", changed.size(),
                changed.stream().collect(java.util.stream.Collectors.groupingBy(
                        Place::getPlaceKind, java.util.stream.Collectors.counting())));
    }
}
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.*'`
Expected: PASS

- [ ] **Step 7: 실기동으로 진짜 백필을 돌린다**

```bash
cd ~/kyum_platform/app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle bootRun
```

로그에 `place_kind 백필 53건 — {ATTRACTION=…, CULTURE=…, MARKET=…, …}`이 뜨는지 본다. 그 다음 DB로 확인한다:

```sql
SELECT coalesce(place_kind::text,'(NULL)') kind, count(*) FROM places GROUP BY 1 ORDER BY 2 DESC;
```

Expected: `(NULL)` **0건**. 실측 53행 기준 대략 `CULTURE≈16 · MARKET≈10 · ATTRACTION≈17 · EVENT=3 · FOOD=3 · SHOP=1 · OTHER=나머지` 근처여야 한다. `OTHER`가 절반을 넘으면 분류기가 실제 문자열과 안 맞는 것이니 Task 1의 표본을 다시 볼 것.

- [ ] **Step 8: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/ app/backend/src/test/java/com/guidematch/knowledge/PlaceKindBackfillTest.java
git commit -m "feat(knowledge): place_kind 백필 러너 (적재와 동일 규칙)"
```

---

### Task 4: 레지스트리 조회 — `findCandidates` + `byPlaceIds`

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/PlaceRepository.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/PlaceInsightLookup.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceInsightLookupTest.java` (확장)

**Interfaces:**
- Consumes: `PlaceKind` (Task 1)
- Produces:
  - `PlaceRepository.findCandidates(String city, String district, Collection<PlaceKind> kinds) → List<Place>`
  - `PlaceInsightLookup.byPlaceIds(Collection<Long> placeIds, String lang) → Map<Long, List<InsightView>>`

**city 대소문자:** DB 실측값은 `Seoul`(= `KoreanCity.key()`와 동일)이지만 계약이 대소문자를 강제하지 않고 과거 스모크 런은 `seoul`을 보냈다. 조회는 대소문자 무시로 한다 — 한 글자 때문에 후보가 0건이 되면 Task 3과 똑같은 조용한 실패가 된다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceInsightLookupTest.java`에 추가:

```java
    /** 레지스트리 정차지는 place_id를 이미 알고 있다 — kakao id를 거칠 이유가 없다. 쿼리 1회. */
    @Test
    void byPlaceIds는_쿼리_한_번으로_인사이트를_붙인다() {
        when(insightRepo.findByPlaceIdIn(anyCollection())).thenReturn(List.of(
                insight(10L, FactKind.VIBE, 0.9, Map.of("ko", "조용하다")),
                insight(10L, FactKind.BEST_TIME, 0.5, Map.of("ko", "아침")),
                insight(11L, FactKind.CAUTION, 0.7, Map.of("ko", "월요일 휴관"))));

        Map<Long, List<PlaceInsightLookup.InsightView>> out =
                lookup.byPlaceIds(List.of(10L, 11L), "ko");

        assertThat(out.keySet()).containsExactlyInAnyOrder(10L, 11L);
        // 신뢰도 내림차순
        assertThat(out.get(10L)).extracting(PlaceInsightLookup.InsightView::kind)
                .containsExactly("vibe", "best_time");
        // 1회차(kakao id → place) 조회가 통째로 빠졌는지 — 이게 "쿼리 1회"의 실질이다
        verify(placeRepo, never()).findAllByKakaoPlaceIdIn(anyCollection());
    }

    @Test
    void byPlaceIds는_빈_입력에_쿼리를_날리지_않는다() {
        assertThat(lookup.byPlaceIds(List.of(), "ko")).isEmpty();
        verify(insightRepo, never()).findByPlaceIdIn(anyCollection());
    }

    /** 요청 언어 → ko → 아무거나. 번역이 아직 없는 사실도 한국어로는 보여준다. */
    @Test
    void byPlaceIds도_언어_폴백을_한다() {
        when(insightRepo.findByPlaceIdIn(anyCollection())).thenReturn(List.of(
                insight(10L, FactKind.VIBE, 0.9, Map.of("ko", "조용하다"))));

        assertThat(lookup.byPlaceIds(List.of(10L), "en").get(10L).get(0).note())
                .isEqualTo("조용하다");
    }
```

> 헬퍼 `insight(...)`와 필드 `lookup`·`placeRepo`·`insightRepo`는 이 파일에 이미 있다. 없으면 기존 `byKakaoPlaceIds` 테스트에서 쓰는 이름을 그대로 따를 것 — **새 헬퍼를 만들지 말 것.**

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.PlaceInsightLookupTest'`
Expected: 컴파일 실패 — `byPlaceIds` 없음

- [ ] **Step 3: `byPlaceIds`를 구현한다**

`PlaceInsightLookup.java`에 추가(기존 `byKakaoPlaceIds`는 **그대로 남긴다** — Kakao 폴백 정차지용):

```java
    /**
     * 우리 place id들로 인사이트를 한 번에 가져온다. <b>쿼리 1회 고정</b>.
     *
     * <p>레지스트리 정차지는 이미 place id를 들고 오므로 {@code byKakaoPlaceIds}의 1회차
     * (kakao id → place) 조회가 통째로 불필요하다.
     *
     * @return placeId → 인사이트 목록 (신뢰도 내림차순). 없는 키는 빈 목록이 아니라 아예 없음.
     */
    public Map<Long, List<InsightView>> byPlaceIds(Collection<Long> placeIds, String lang) {
        List<Long> ids = placeIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        return insightRepo.findByPlaceIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        PlaceInsight::getPlaceId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparingDouble(PlaceInsight::getConfidence).reversed())
                                .map(i -> toView(i, lang))
                                .toList())));
    }
```

- [ ] **Step 4: `findCandidates`를 추가한다**

`PlaceRepository.java`:

```java
    /**
     * 코스 정차지 후보 — 도시(+구)와 종류로 좁힌다. {@code idx_places_city_district}가 이미 있다.
     *
     * <p>좌표가 없는 행은 최근접 이웃 계산에 못 쓰므로 여기서 뺀다 — 호출부에서 필터하면
     * 언젠가 빠뜨린다.
     *
     * <p>city를 대소문자 무시로 비교하는 이유: 계약이 표기를 강제하지 않고 과거 실행이
     * {@code seoul}과 {@code Seoul}을 모두 보낸 적이 있다. 한 글자 때문에 후보가 0건이 되면
     * 응답은 정상이고 결과만 조용히 Kakao로 되돌아간다.
     */
    @Query("""
            SELECT p FROM Place p
             WHERE upper(p.city) = upper(:city)
               AND (:district IS NULL OR p.district = :district)
               AND p.placeKind IN :kinds
               AND p.lat IS NOT NULL AND p.lng IS NOT NULL
            """)
    List<Place> findCandidates(@Param("city") String city,
                               @Param("district") String district,
                               @Param("kinds") Collection<PlaceKind> kinds);
```

import 추가: `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.*'`
Expected: PASS

- [ ] **Step 6: 쿼리가 실제로 도는지 확인한다** — JPQL 오타는 단위 테스트로 안 잡히고 기동 시점에 터진다

```bash
cd ~/kyum_platform/app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle bootRun
```

Expected: `Started GuideMatchApplication` — JPQL 파싱 오류가 있으면 여기서 기동이 실패한다.

- [ ] **Step 7: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/
git commit -m "feat(knowledge): 정차지 후보 조회 + place_id 기반 인사이트 배치 조회"
```

---

### Task 5: `CoursePlanner` — 레지스트리 우선, Kakao는 폴백

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/geo/CoursePlanner.java`
- Test: `app/backend/src/test/java/com/guidematch/geo/CoursePlannerTest.java`

**Interfaces:**
- Consumes: `PlaceRepository.findCandidates(...)` (Task 4), `PlaceKind` (Task 1), 기존 `KakaoLocalClient.searchByCategory/searchByKeyword/geocodeRegion/isEnabled`, `GeoUtils.distanceKm`, `KoreanCity`
- Produces:
  - `CoursePlanner.PlannedStop(Long placeId, String kakaoPlaceId, String name, String category, String address, double lat, double lng, String placeUrl, String source)`
  - `CoursePlanner.Plan(List<PlannedStop> stops, double anchorLat, double anchorLng, String resolvedDistrict)`
  - `CoursePlanner.plan(KoreanCity city, String district, List<String> slots) → Plan`

**설계 요점**

- **facet → PlaceKind**: `attraction → {ATTRACTION, NATURE}` · `culture → {CULTURE}` · `food → {FOOD}` · `cafe → {CAFE}` · `market → {MARKET}`
- **레지스트리 조회는 1회**다. 필요한 종류를 전부 합쳐 한 번에 가져와 메모리에서 facet별로 나눈다(구 단위 수백 행). 슬롯마다 조회하면 Sydney 왕복이 그대로 응답 시간에 얹힌다.
- **Kakao는 게으르게 부른다.** 레지스트리가 채우지 못한 facet에 대해서만 검색한다. 레지스트리가 두꺼워질수록 Kakao 호출이 자연히 0으로 수렴한다.
- **구 앵커**: Kakao가 있으면 기존 지오코딩, 없으면 **해당 구 레지스트리 장소들의 무게중심**. 그래야 Kakao 키 없이도 구 단위가 동작한다.
- **중복 제거 3단**: kakao id 일치 → 정규화 이름 일치 → 100m 이내.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.guidematch.geo;

import com.guidematch.knowledge.Place;
import com.guidematch.knowledge.PlaceKind;
import com.guidematch.knowledge.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 정차지 선정.
 *
 * <p><b>가장 중요한 것</b>: 레지스트리가 채울 수 있으면 Kakao를 <b>부르지 않는다</b>는 것.
 * 이게 깨지면 응답은 멀쩡하고 결과도 그럴듯한데 지식베이스는 아무 일도 안 하게 된다 —
 * 이 기능 전체가 무의미해지는 조용한 실패다.
 */
class CoursePlannerTest {

    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final KakaoLocalClient kakao = mock(KakaoLocalClient.class);
    private final CoursePlanner planner = new CoursePlanner(placeRepo, kakao);

    private static final KoreanCity SEOUL = KoreanCity.LIST.stream()
            .filter(c -> c.key().equals("Seoul")).findFirst().orElseThrow();

    private static Place place(long id, String name, PlaceKind kind, double lat, double lng) {
        Place p = new Place(name, "Seoul", "중구", lat, lng, "k" + id, null, "cat", "서울 중구 어딘가");
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "placeKind", kind);
        return p;
    }

    private static KakaoLocalClient.Place kakaoPlace(String id, String name, double lat, double lng) {
        return new KakaoLocalClient.Place(id, name, "여행 > 관광,명소", "서울 중구", lat, lng,
                "http://place.map.kakao.com/" + id, null);
    }

    @Test
    void 레지스트리가_슬롯을_다_채우면_Kakao를_부르지_않는다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(placeRepo.findCandidates(eq("Seoul"), eq("중구"), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749),
                place(2, "남대문시장", PlaceKind.MARKET, 37.5595, 126.9773)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "market"));

        assertThat(plan.stops()).hasSize(2);
        assertThat(plan.stops()).extracting(CoursePlanner.PlannedStop::source)
                .containsOnly("registry");
        assertThat(plan.stops()).extracting(CoursePlanner.PlannedStop::placeId)
                .containsExactlyInAnyOrder(1L, 2L);
        verify(kakao, never()).searchByCategory(anyString(), anyDouble(), anyDouble(), anyInt());
        verify(kakao, never()).searchByKeyword(anyString(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void 레지스트리에_없는_슬롯만_Kakao로_채운다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));
        when(kakao.searchByCategory(eq("CE7"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(kakaoPlace("kk9", "어느 카페", 37.5660, 126.9750)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "cafe"));

        assertThat(plan.stops()).extracting(CoursePlanner.PlannedStop::source)
                .containsExactly("registry", "kakao");
        // 레지스트리가 채운 facet은 검색하지 않는다
        verify(kakao, never()).searchByCategory(eq("AT4"), anyDouble(), anyDouble(), anyInt());
    }

    /** ★ 같은 장소가 두 출처에서 오면 한 번만 나와야 한다 — kakao id 일치. */
    @Test
    void 중복제거_1단_kakao_id가_같으면_한_번만_나온다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));
        when(kakao.searchByCategory(eq("CE7"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(kakaoPlace("k1", "덕수궁", 37.5656, 126.9749)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "cafe"));

        assertThat(plan.stops()).hasSize(1);
        assertThat(plan.stops().get(0).source()).isEqualTo("registry");
    }

    /** 중복제거 2단 — id가 달라도 정규화 이름이 같으면 같은 장소로 본다. */
    @Test
    void 중복제거_2단_이름이_같으면_한_번만_나온다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "남산골한옥마을", PlaceKind.ATTRACTION, 37.5594, 126.9940)));
        when(kakao.searchByCategory(eq("CE7"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(kakaoPlace("zz", "남산골 한옥마을", 37.5594, 126.9940)));

        assertThat(planner.plan(SEOUL, "중구", List.of("attraction", "cafe")).stops()).hasSize(1);
    }

    /** 중복제거 3단 — 이름이 달라도 100m 안이면 같은 장소로 본다. */
    @Test
    void 중복제거_3단_100m_이내면_한_번만_나온다() {
        when(kakao.isEnabled()).thenReturn(true);
        when(kakao.geocodeRegion(anyString())).thenReturn(new double[]{37.5636, 126.9976});
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));
        when(kakao.searchByCategory(eq("CE7"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(kakaoPlace("zz", "덕수궁 대한문", 37.5657, 126.9750)));

        assertThat(planner.plan(SEOUL, "중구", List.of("attraction", "cafe")).stops()).hasSize(1);
    }

    /** ★ Kakao 키가 없어도 레지스트리는 동작해야 한다. 지금은 무조건 빈 목록이었다. */
    @Test
    void Kakao가_없어도_레지스트리로_코스가_나온다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749),
                place(2, "남대문시장", PlaceKind.MARKET, 37.5595, 126.9773)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "market"));

        assertThat(plan.stops()).hasSize(2);
        verify(kakao, never()).geocodeRegion(anyString());
    }

    /** ★ 구 앵커가 Kakao에 물려 있으면 키 없이 구 단위가 죽는다. 무게중심으로 푼다. */
    @Test
    void Kakao가_없으면_구_앵커는_레지스트리_무게중심이다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "북쪽", PlaceKind.ATTRACTION, 37.60, 127.00),
                place(2, "남쪽", PlaceKind.ATTRACTION, 37.50, 127.00)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction"));

        assertThat(plan.anchorLat()).isEqualTo(37.55, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(plan.anchorLng()).isEqualTo(127.00, org.assertj.core.data.Offset.offset(0.0001));
    }

    /** 구에 레지스트리 장소가 하나도 없으면 도시 중심으로 떨어진다 (Kakao 없음). */
    @Test
    void 레지스트리가_빈_구에서는_도시_중심을_쓴다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of());

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction"));

        assertThat(plan.stops()).isEmpty();
        assertThat(plan.anchorLat()).isEqualTo(SEOUL.lat());
    }

    /** EVENT·SHOP·LODGING·OTHER는 조회 자체에 들어가지 않는다 — 축제가 코스에 오르지 않는 근거. */
    @Test
    void 정차지_후보_종류만_조회한다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of());

        planner.plan(SEOUL, "중구", List.of("attraction", "cafe"));

        org.mockito.ArgumentCaptor<java.util.Collection<PlaceKind>> kinds =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(placeRepo).findCandidates(anyString(), anyString(), kinds.capture());
        assertThat(kinds.getValue())
                .containsExactlyInAnyOrder(PlaceKind.ATTRACTION, PlaceKind.NATURE, PlaceKind.CAFE)
                .doesNotContain(PlaceKind.EVENT, PlaceKind.SHOP, PlaceKind.LODGING, PlaceKind.OTHER);
    }

    /** 같은 장소를 두 슬롯에 넣지 않는다 (기존 동작 유지). */
    @Test
    void 같은_장소는_두_번_나오지_않는다() {
        when(kakao.isEnabled()).thenReturn(false);
        when(placeRepo.findCandidates(anyString(), anyString(), anyCollection())).thenReturn(List.of(
                place(1, "덕수궁", PlaceKind.ATTRACTION, 37.5656, 126.9749)));

        CoursePlanner.Plan plan = planner.plan(SEOUL, "중구", List.of("attraction", "attraction"));

        assertThat(plan.stops()).hasSize(1);
    }
}
```

> ⚠ `KakaoLocalClient.Place`의 생성자 인자 순서·개수는 **반드시 `KakaoLocalClient.java:152`의 record 선언을 열어 확인하고 맞출 것.** 위 헬퍼는 `(id, name, category, address, latitude, longitude, placeUrl, distance)` 순서를 가정한다.

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `gradle test --tests 'com.guidematch.geo.CoursePlannerTest'`
Expected: 컴파일 실패 — `CoursePlanner` 없음

- [ ] **Step 3: `CoursePlanner`를 만든다**

```java
package com.guidematch.geo;

import com.guidematch.knowledge.Place;
import com.guidematch.knowledge.PlaceKind;
import com.guidematch.knowledge.PlaceNames;
import com.guidematch.knowledge.PlaceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 코스 정차지를 고른다 — <b>우리 레지스트리가 먼저, Kakao는 모자란 슬롯만.</b>
 *
 * <p><b>왜 뒤집었나</b>: 정차지를 Kakao 실시간 검색이 정하면 지식베이스는 "Kakao가 반환한 것"만
 * 주석할 수 있다. 경복궁·간송미술관처럼 TourAPI에만 있는 문화재·전시관은 영원히 정차지가 못 되는데,
 * 그게 TourAPI를 쓰는 이유 그 자체다. 인사이트 커버리지 상한이 남의 검색 결과에 갇힌다.
 *
 * <p>얇은 범위(아직 수집이 안 된 구)에서는 Kakao가 채운다. 수집이 늘수록 Kakao 의존이
 * 자연히 줄어든다.
 */
@Service
public class CoursePlanner {

    private static final int CITY_RADIUS_METERS = 10000;
    private static final int DISTRICT_RADIUS_METERS = 4000;

    /** 슬롯당 무작위 선택 폭 — 가까운 후보 상위 N 중 랜덤 (동선 유지 + "다시 추천" 다양성) */
    private static final int PICK_POOL = 3;

    /** 같은 장소로 볼 거리 — 중복 제거 3단. */
    private static final double DEDUPE_METERS = 100;

    /** facet 키 → Kakao 카테고리 그룹 코드 (PlaceController와 동일 규약) */
    private static final Map<String, String> CATEGORY_CODES = Map.of(
            "attraction", "AT4", "food", "FD6", "cafe", "CE7", "culture", "CT1");

    /** facet 키 → keyword 검색어 (카테고리 코드가 없는 경우) */
    private static final Map<String, String> KEYWORDS = Map.of("market", "전통시장");

    /** facet 키 → 레지스트리에서 뽑을 장소 종류. 여기 없는 종류는 정차지가 될 수 없다. */
    private static final Map<String, Set<PlaceKind>> FACET_KINDS = Map.of(
            "attraction", Set.of(PlaceKind.ATTRACTION, PlaceKind.NATURE),
            "culture",    Set.of(PlaceKind.CULTURE),
            "food",       Set.of(PlaceKind.FOOD),
            "cafe",       Set.of(PlaceKind.CAFE),
            "market",     Set.of(PlaceKind.MARKET));

    private final PlaceRepository placeRepo;
    private final KakaoLocalClient kakaoClient;

    public CoursePlanner(PlaceRepository placeRepo, KakaoLocalClient kakaoClient) {
        this.placeRepo = placeRepo;
        this.kakaoClient = kakaoClient;
    }

    /**
     * 정차지 하나. {@code source}가 이 기능이 실제로 일하는지 밖에서 확인하는 유일한 관측 지점이다 —
     * 백필을 빠뜨리면 후보가 0건이 되고 Kakao 폴백이 전부 채워 <b>결과가 예전과 똑같아 보인다.</b>
     */
    public record PlannedStop(
            Long placeId, String kakaoPlaceId,
            String name, String category, String address,
            double lat, double lng, String placeUrl,
            String source) {

        public static PlannedStop ofRegistry(Place p) {
            return new PlannedStop(p.getId(), p.getKakaoPlaceId(), p.getNameKo(),
                    p.getCategory(), p.getAddressKo(), p.getLat(), p.getLng(), null, "registry");
        }

        public static PlannedStop ofKakao(KakaoLocalClient.Place p) {
            return new PlannedStop(null, p.id(), p.name(), p.category(), p.address(),
                    p.latitude(), p.longitude(), p.placeUrl(), "kakao");
        }
    }

    public record Plan(List<PlannedStop> stops, double anchorLat, double anchorLng,
                       String resolvedDistrict) {}

    /**
     * Kakao 키가 설정돼 있는가. 컨트롤러가 응답의 {@code kakaoEnabled}를 채우는 데 쓴다 —
     * 프론트는 이 값으로 지도·장소 링크 노출을 판단하므로 <b>실제 값이어야 한다.</b>
     * (컨트롤러가 {@link KakaoLocalClient}를 직접 주입받지 않게 하려고 여기로 위임한다.)
     */
    public boolean isKakaoEnabled() {
        return kakaoClient.isEnabled();
    }

    public Plan plan(KoreanCity city, String district, List<String> slots) {
        boolean validDistrict = district != null && !district.isBlank()
                && KoreanCity.districtsOf(city.key()).stream().anyMatch(d -> d.ko().equals(district));
        String scopeDistrict = validDistrict ? district : null;

        // 레지스트리 조회는 1회. 필요한 종류를 전부 합쳐 가져와 메모리에서 facet별로 나눈다.
        Set<PlaceKind> wanted = new HashSet<>();
        for (String facet : slots) wanted.addAll(FACET_KINDS.getOrDefault(facet, Set.of()));
        List<Place> registry = wanted.isEmpty() ? List.of()
                : placeRepo.findCandidates(city.key(), scopeDistrict, wanted);

        double[] anchor = anchorOf(city, scopeDistrict, registry);
        int radius = scopeDistrict != null ? DISTRICT_RADIUS_METERS : CITY_RADIUS_METERS;

        Map<String, List<Place>> registryPool = new HashMap<>();
        for (String facet : new HashSet<>(slots)) {
            Set<PlaceKind> kinds = FACET_KINDS.getOrDefault(facet, Set.of());
            registryPool.put(facet, registry.stream()
                    .filter(p -> kinds.contains(p.getPlaceKind())).toList());
        }

        // Kakao 폴백 풀은 게으르게 채운다 — 레지스트리가 채운 facet은 검색조차 하지 않는다
        Map<String, List<KakaoLocalClient.Place>> kakaoPool = new HashMap<>();

        List<PlannedStop> picked = new ArrayList<>();
        Set<String> usedKakaoIds = new HashSet<>();
        Set<Long> usedPlaceIds = new HashSet<>();
        Set<String> usedNames = new LinkedHashSet<>();
        double prevLat = anchor[0], prevLng = anchor[1];

        for (String facet : slots) {
            PlannedStop chosen = pickFromRegistry(registryPool.getOrDefault(facet, List.of()),
                    prevLat, prevLng, picked, usedPlaceIds, usedKakaoIds, usedNames);

            if (chosen == null && kakaoClient.isEnabled()) {
                List<KakaoLocalClient.Place> pool = kakaoPool.computeIfAbsent(facet,
                        f -> searchKakao(f, anchor[0], anchor[1], radius));
                chosen = pickFromKakao(pool, prevLat, prevLng, picked, usedKakaoIds, usedNames);
            }
            if (chosen == null) continue; // 이 슬롯은 건너뜀 (예: 구 안에 전통시장이 없음)

            picked.add(chosen);
            if (chosen.placeId() != null) usedPlaceIds.add(chosen.placeId());
            if (chosen.kakaoPlaceId() != null) usedKakaoIds.add(chosen.kakaoPlaceId());
            usedNames.add(PlaceNames.normalize(chosen.name()));
            prevLat = chosen.lat();
            prevLng = chosen.lng();
        }
        return new Plan(picked, anchor[0], anchor[1], scopeDistrict);
    }

    /**
     * 구 중심 좌표.
     *
     * <p>Kakao 지오코딩에만 의존하면 키가 없을 때 구 단위 최근접 이웃의 출발점이 사라진다.
     * 레지스트리 장소들의 무게중심은 <b>이미 가진 데이터로 풀리고</b>, 실제 수집 분포를
     * 반영해 도리어 정확하다. 레지스트리가 빈 구에서는 지오코딩(있으면) → 도시 중심 순으로 떨어진다.
     */
    private double[] anchorOf(KoreanCity city, String district, List<Place> registry) {
        if (district == null) return new double[]{city.lat(), city.lng()};

        if (kakaoClient.isEnabled()) {
            double[] geo = kakaoClient.geocodeRegion(city.nameKo() + " " + district);
            if (geo != null) return geo;
        }
        if (!registry.isEmpty()) {
            double lat = registry.stream().mapToDouble(Place::getLat).average().orElse(city.lat());
            double lng = registry.stream().mapToDouble(Place::getLng).average().orElse(city.lng());
            return new double[]{lat, lng};
        }
        return new double[]{city.lat(), city.lng()};
    }

    private List<KakaoLocalClient.Place> searchKakao(String facet, double lat, double lng, int radius) {
        String code = CATEGORY_CODES.get(facet);
        List<KakaoLocalClient.Place> found = code != null
                ? kakaoClient.searchByCategory(code, lat, lng, radius)
                : kakaoClient.searchByKeyword(KEYWORDS.getOrDefault(facet, facet), lat, lng, radius);
        return found.stream().filter(p -> p.latitude() != null && p.longitude() != null).toList();
    }

    private PlannedStop pickFromRegistry(List<Place> pool, double fromLat, double fromLng,
                                         List<PlannedStop> already, Set<Long> usedPlaceIds,
                                         Set<String> usedKakaoIds, Set<String> usedNames) {
        List<Place> candidates = pool.stream()
                .filter(p -> !usedPlaceIds.contains(p.getId()))
                .filter(p -> p.getKakaoPlaceId() == null || !usedKakaoIds.contains(p.getKakaoPlaceId()))
                .filter(p -> !usedNames.contains(PlaceNames.normalize(p.getNameKo())))
                .filter(p -> notNear(already, p.getLat(), p.getLng()))
                .sorted((a, b) -> Double.compare(
                        GeoUtils.distanceKm(fromLat, fromLng, a.getLat(), a.getLng()),
                        GeoUtils.distanceKm(fromLat, fromLng, b.getLat(), b.getLng())))
                .limit(PICK_POOL)
                .toList();
        if (candidates.isEmpty()) return null;
        return PlannedStop.ofRegistry(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    private PlannedStop pickFromKakao(List<KakaoLocalClient.Place> pool, double fromLat, double fromLng,
                                      List<PlannedStop> already, Set<String> usedKakaoIds,
                                      Set<String> usedNames) {
        List<KakaoLocalClient.Place> candidates = pool.stream()
                .filter(p -> !usedKakaoIds.contains(p.id()))
                .filter(p -> !usedNames.contains(PlaceNames.normalize(p.name())))
                .filter(p -> notNear(already, p.latitude(), p.longitude()))
                .sorted((a, b) -> Double.compare(
                        GeoUtils.distanceKm(fromLat, fromLng, a.latitude(), a.longitude()),
                        GeoUtils.distanceKm(fromLat, fromLng, b.latitude(), b.longitude())))
                .limit(PICK_POOL)
                .toList();
        if (candidates.isEmpty()) return null;
        return PlannedStop.ofKakao(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    /**
     * 중복 제거 3단 중 마지막 — 이름도 id도 다르지만 사실상 같은 지점.
     * ("덕수궁"과 "덕수궁 대한문"이 각각 다른 출처에서 오는 상황이 실제로 관측됐다.)
     */
    private boolean notNear(List<PlannedStop> already, Double lat, Double lng) {
        if (lat == null || lng == null) return false;
        return already.stream().noneMatch(s ->
                GeoUtils.distanceKm(s.lat(), s.lng(), lat, lng) * 1000.0 <= DEDUPE_METERS);
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

Run: `gradle test --tests 'com.guidematch.geo.CoursePlannerTest'`
Expected: PASS

- [ ] **Step 5: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/geo/CoursePlanner.java \
        app/backend/src/test/java/com/guidematch/geo/CoursePlannerTest.java
git commit -m "feat(geo): CoursePlanner — 레지스트리 우선 정차지 선정 + Kakao 폴백"
```

---

### Task 6: 컨트롤러 배선 + `Stop.source`

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/geo/CourseRecommendController.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/SignalRecorder.java`
- Test: `app/backend/src/test/java/com/guidematch/geo/CoursePlannerTest.java` (변경 없음), 신규 `SignalRecorderTest`는 만들지 않는다

**Interfaces:**
- Consumes: `CoursePlanner.plan(...)` (Task 5), `PlaceInsightLookup.byPlaceIds/byKakaoPlaceIds` (Task 4)
- Produces: `Stop`에 `source` 필드 추가, `SignalRecorder.recordShown(List<SignalRecorder.StopRef>, String, Long)` + `record StopRef(Long placeId, String kakaoPlaceId)`

- [ ] **Step 1: `SignalRecorder`를 정차지 참조 기반으로 바꾼다**

레지스트리 정차지는 `place_id`를 이미 알고 있어 kakao id를 거칠 이유가 없다. 기존 시그니처는 **소비자가 하나뿐**이므로 교체한다.

`SignalRecorder.java` — `recordShown`을 아래로 바꾼다:

```java
    /** 정차지 하나에 대한 참조. 레지스트리 유래는 placeId를, Kakao 폴백은 kakaoPlaceId를 갖는다. */
    public record StopRef(Long placeId, String kakaoPlaceId) {}

    /**
     * 코스 추천이 정차지들을 사용자에게 보여줬다.
     *
     * @param stops     노출된 정차지들
     * @param courseRef 어떤 추천이었는지 (예: "Seoul/중구/cafe")
     */
    public void recordShown(List<StopRef> stops, String courseRef, Long userId) {
        try {
            if (stops.isEmpty()) return;

            // 아직 place_id를 모르는 것만 한 번에 조회한다 (쿼리 최대 1회)
            List<String> unknown = stops.stream()
                    .filter(s -> s.placeId() == null)
                    .map(StopRef::kakaoPlaceId)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct().toList();
            Map<String, Long> resolved = unknown.isEmpty() ? Map.of()
                    : placeRepo.findAllByKakaoPlaceIdIn(unknown).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Place::getKakaoPlaceId, Place::getId, (a, b) -> a));

            List<RecommendationSignal> rows = stops.stream()
                    // 아직 레지스트리에 없는 장소도 기록한다 — place_id만 null.
                    // "추천에는 나왔는데 지식이 없는 장소"가 곧 다음 수집 우선순위다.
                    .map(s -> new RecommendationSignal(
                            RecommendationSignal.EventType.SHOWN,
                            s.placeId() != null ? s.placeId() : resolved.get(s.kakaoPlaceId()),
                            courseRef, userId))
                    .toList();
            repo.saveAll(rows);
        } catch (Exception e) {
            log.warn("추천 노출 신호 기록 실패 — 무시하고 진행: {}", e.toString());
        }
    }
```

import에 `java.util.Map`이 이미 있는지 확인하고 없으면 추가한다.

- [ ] **Step 2: 컨트롤러를 `CoursePlanner`에 위임하도록 바꾼다**

`CourseRecommendController.java` — 상수 `CITY_RADIUS_METERS`·`DISTRICT_RADIUS_METERS`·`CATEGORY_CODES`·`KEYWORDS`·`PICK_POOL`은 `CoursePlanner`로 옮겨갔으므로 **삭제한다.** `THEME_SLOTS`·`MINUTES_PER_STOP`·`WALK_KMH`는 남는다(테마 정의와 소요시간 산식은 컨트롤러의 책임).

생성자·필드:

```java
    private final CoursePlanner coursePlanner;
    private final TranslationService translationService;
    private final PlaceInsightLookup insightLookup;
    private final SignalRecorder signalRecorder;

    public CourseRecommendController(CoursePlanner coursePlanner,
                                     TranslationService translationService,
                                     PlaceInsightLookup insightLookup,
                                     SignalRecorder signalRecorder) {
        this.coursePlanner = coursePlanner;
        this.translationService = translationService;
        this.insightLookup = insightLookup;
        this.signalRecorder = signalRecorder;
    }
```

`recommend` 본문:

```java
        KoreanCity target = KoreanCity.LIST.stream()
                .filter(c -> c.key().equalsIgnoreCase(city))
                .findFirst()
                .orElse(null);
        List<String> slots = THEME_SLOTS.getOrDefault(theme, THEME_SLOTS.get("mixed"));

        if (target == null) {
            return new RecommendResponse(city, null, theme, false, List.of(), 0, 0);
        }

        // ⚠ kakaoEnabled=false여도 더 이상 빈 목록이 아니다 — 레지스트리는 Kakao 없이 동작한다.
        //   그래도 이 필드는 실제 값을 실어야 한다. 프론트가 지도·장소 링크 노출을 이 값으로
        //   판단하므로 true로 못 박으면 키가 없을 때 깨진 지도를 띄우게 된다.
        CoursePlanner.Plan plan = coursePlanner.plan(target, district, slots);
        List<CoursePlanner.PlannedStop> picked = plan.stops();

        List<Integer> legMeters = new ArrayList<>();
        int totalMeters = 0;
        for (int i = 0; i < picked.size(); i++) {
            if (i == 0) { legMeters.add(null); continue; }
            CoursePlanner.PlannedStop prev = picked.get(i - 1), cur = picked.get(i);
            int m = (int) Math.round(GeoUtils.distanceKm(
                    prev.lat(), prev.lng(), cur.lat(), cur.lng()) * 1000);
            legMeters.add(m);
            totalMeters += m;
        }

        int suggestedHours = picked.isEmpty() ? 0 : (int) Math.min(8, Math.max(2, Math.round(
                (picked.size() * MINUTES_PER_STOP + (totalMeters / 1000.0) / WALK_KMH * 60) / 60.0)));

        List<Stop> stops = toStops(picked, legMeters, lang);

        signalRecorder.recordShown(
                picked.stream()
                        .map(p -> new SignalRecorder.StopRef(p.placeId(), p.kakaoPlaceId()))
                        .toList(),
                target.key() + "/" + (plan.resolvedDistrict() == null ? "" : plan.resolvedDistrict())
                        + "/" + theme,
                userId);

        return new RecommendResponse(target.key(), plan.resolvedDistrict(), theme,
                coursePlanner.isKakaoEnabled(), stops, totalMeters, suggestedHours);
```

⚠ 리팩터 후 `import com.guidematch.geo.KakaoLocalClient.Place;`와 `java.util.HashMap`·`HashSet`·`Set`·`ThreadLocalRandom` import가 쓰이지 않게 된다 — 지울 것. 남겨도 컴파일은 되지만 다음 사람이 컨트롤러가 아직 Kakao를 직접 쓴다고 오해한다.

- [ ] **Step 3: `toStops`가 두 출처의 인사이트를 붙이게 한다**

```java
    /**
     * 카테고리 경로는 마지막 segment만 취하고, lang != ko면 번역한다.
     * 인사이트는 <b>정차지 수와 무관하게 최대 쿼리 3회</b>로 붙인다
     * (레지스트리 1회 + Kakao 폴백 2회).
     */
    private List<Stop> toStops(List<CoursePlanner.PlannedStop> picked,
                               List<Integer> legMeters, String lang) {
        List<String> names = picked.stream().map(CoursePlanner.PlannedStop::name).toList();
        List<String> shortCats = picked.stream().map(p -> {
            String c = p.category() != null ? p.category() : "";
            int idx = c.lastIndexOf(" > ");
            return idx >= 0 ? c.substring(idx + 3) : c;
        }).toList();

        String googleLang = GoogleTranslateClient.toGoogleLang(lang);
        List<String> tNames = googleLang != null && !names.isEmpty()
                ? translationService.translate(names, googleLang) : names;
        List<String> tCats = googleLang != null && !shortCats.isEmpty()
                ? translationService.translate(shortCats, googleLang) : shortCats;

        Map<Long, List<PlaceInsightLookup.InsightView>> byPlace = insightLookup.byPlaceIds(
                picked.stream().map(CoursePlanner.PlannedStop::placeId)
                        .filter(java.util.Objects::nonNull).toList(), lang);
        // 폴백 정차지는 우리 레지스트리에 있을 수도, 없을 수도 있다
        Map<String, List<PlaceInsightLookup.InsightView>> byKakao = insightLookup.byKakaoPlaceIds(
                picked.stream().filter(p -> p.placeId() == null)
                        .map(CoursePlanner.PlannedStop::kakaoPlaceId)
                        .filter(java.util.Objects::nonNull).toList(), lang);

        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            CoursePlanner.PlannedStop p = picked.get(i);
            List<PlaceInsightLookup.InsightView> insights = p.placeId() != null
                    ? byPlace.getOrDefault(p.placeId(), List.of())
                    : byKakao.getOrDefault(p.kakaoPlaceId(), List.of());
            stops.add(new Stop(
                    i + 1, tNames.get(i),
                    tCats.get(i).isBlank() ? shortCats.get(i) : tCats.get(i),
                    p.address(), // 주소는 한국어 유지 (택시/지도 편의)
                    p.lat(), p.lng(), p.placeUrl(),
                    legMeters.get(i),
                    p.source(),
                    insights));
        }
        return stops;
    }

    public record Stop(
            int order, String name, String category, String address,
            Double latitude, Double longitude, String placeUrl,
            Integer distanceFromPrevMeters,
            /**
             * "registry" | "kakao". 프론트는 무시해도 되지만 응답에는 반드시 실린다 —
             * 백필 누락 같은 조용한 실패를 밖에서 잡아내는 유일한 관측 지점이다.
             */
            String source,
            /** 아직 수집 안 된 장소는 빈 배열 — 프론트는 있으면 보여주고 없으면 무시하면 된다. */
            List<PlaceInsightLookup.InsightView> insights
    ) {}
```

- [ ] **Step 4: 전체 테스트를 돌린다**

Run: `cd ~/kyum_platform/app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test`
Expected: 전부 PASS (기존 109건 + 이번에 추가한 것들)

- [ ] **Step 5: 프론트 타입은 건드리지 않는다**

`TimetableBuilder.tsx`의 `RecStop` 타입에 `source`·`insights`를 **추가하지 않는다.** TypeScript는 응답의 여분 필드를 무시하므로 깨지지 않고, 인사이트를 화면에 노출하는 일은 설계서 §10에서 하위 프로젝트 3으로 명시적으로 미뤄져 있다. 여기서 타입만 늘리면 쓰지 않는 필드가 남는다.

확인만 한다:

```bash
cd ~/kyum_platform/app/frontend && npx tsc --noEmit
```

Expected: 오류 0

- [ ] **Step 6: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/geo/CourseRecommendController.java \
        app/backend/src/main/java/com/guidematch/knowledge/SignalRecorder.java
git commit -m "feat(geo): 코스 추천이 레지스트리 정차지를 쓰고 source를 응답에 싣는다"
```

---

### Task 7: ★ 체크포인트 — 레지스트리가 실제로 정차지를 정하는지 실증

**Files:** 없음 (검증만)

이 태스크는 코드를 쓰지 않는다. **Phase A가 실제로 동작하는지 사람이 확인하는 지점이고, Phase B로 넘어가기 전 유일한 관문이다.**

- [ ] **Step 1: 백엔드를 띄운다**

```bash
cd ~/kyum_platform/app/backend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
gradle bootRun
```

`place_kind 백필 …` 로그가 이미 지난 기동에서 떴다면 이번엔 안 뜬다(정상 — 할 일이 0건).

- [ ] **Step 2: 토큰을 받는다**

⚠ `signup`은 **user 객체를 반환한다(토큰 아님)**. 이어서 login을 호출해야 하고, 응답 필드명은 `token`이 아니라 **`accessToken`**이다.

```bash
curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"match_test3_...@test.com","password":"test1234"}' | python3 -m json.tool
```

기존 테스트 계정은 `HANDOFF.md` §9에 있다. 없으면 signup → login 순으로 만든다.

- [ ] **Step 3: 레지스트리 정차지가 나오는지 확인한다 (완료조건 1)**

```bash
TOKEN=<위에서 받은 accessToken>
curl -s -H "Authorization: Bearer $TOKEN" \
  'localhost:8080/api/courses/recommend?city=Seoul&district=%EC%A4%91%EA%B5%AC&theme=culture&lang=ko' \
  | python3 -m json.tool
```

**합격 기준:** `stops[].source == "registry"`인 정차지가 **1개 이상**.

> 전부 `"kakao"`면 백필이 안 돌았거나 `findCandidates`가 0건을 준 것이다. "정차지가 나온다"는 합격 신호가 아니다 — Kakao 폴백이 언제나 채워주기 때문이다. 진단 순서: `SELECT count(*) FROM places WHERE place_kind IS NULL;`(0이어야 함) → `SELECT place_kind, count(*) FROM places GROUP BY 1;` → `city` 표기 확인.

**구 없이도 한 번 호출한다** — `findCandidates`의 `(:district IS NULL OR ...)` 분기는 위 요청으로 **한 번도 실행되지 않는다.** Hibernate 6 + Postgres는 null 파라미터를 `IS NULL` 비교에 쓸 때 실행 시점에 "could not determine data type of parameter"로 죽을 수 있고, `bootRun`은 파싱 오류만 잡는다:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  'localhost:8080/api/courses/recommend?city=Seoul&theme=mixed&lang=ko' | python3 -m json.tool
```

Expected: 500이 아니라 정상 응답. 500이면 백엔드 로그에서 SQL 오류를 확인하고 `findCandidates`를 두 메서드(구 있음 / 구 없음)로 나눈다.

- [ ] **Step 4: 인사이트가 붙는지 확인한다 (완료조건 2 — 약식)**

인사이트를 가진 진짜 장소는 실측 3곳(`국립극장 공연예술박물관`·`국토발전전시관`·`관훈동 민씨 가옥`)뿐인데 CULTURE 후보가 ~16곳이고 슬롯마다 상위 3중 무작위 선택이라, **한두 번 호출로는 안 나오는 게 정상이다.** 눈으로 반복하지 말고 돌린다:

```bash
for i in $(seq 1 15); do
  curl -s -H "Authorization: Bearer $TOKEN" \
    'localhost:8080/api/courses/recommend?city=Seoul&district=%EC%A4%91%EA%B5%AC&theme=culture&lang=ko' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); [print(s["order"], s["source"], s["name"], len(s["insights"])) for s in d["stops"] if s["insights"]]'
done
```

**합격 기준:** 15회 안에 인사이트가 붙은 정차지가 **한 줄이라도 출력된다.** 15회 전부 아무것도 안 나오면 실패로 보고 `PlaceInsightLookup.byPlaceIds`와 `place_insights.place_id` 조인을 확인한다.

이게 성립하면 **인사이트가 코스에 붙는 경로가 처음으로 실증된 것이다** — 지금까지 한 번도 증명된 적이 없다(예전엔 `byKakaoPlaceIds`로만 조회해서 tour_api 장소 9건이 구조적으로 도달 불가였다).

⚠ 붙는 인사이트의 **내용**은 아직 신뢰할 수 없다(전부 `insight-v2` 필드 템플릿 산물). 경로가 열린 것과 내용이 좋은 것은 다른 문제이고, 후자는 Phase B의 프롬프트 v4 + 재수집이 해결한다.

- [ ] **Step 5: 축제가 정차지로 안 나오는지 확인한다**

여러 번 호출해도 `가을 , 명동으로`·`겨울, 청계천의 빛`·`게임문화축제`가 정차지에 **한 번도 안 나와야 한다**. 나오면 `place_kind`가 `EVENT`로 안 들어간 것이다.

- [ ] **Step 6: Kakao 키 없이도 동작하는지 확인한다**

⚠ **`.env`를 고치지 않는다** — 에이전트는 `.env*`에 접근할 수 없고, 사람이 고치면 되돌리는 걸 잊기 쉽다. 커맨드라인 인자가 `.env`보다 우선순위가 높으므로 그걸 쓴다:

```bash
cd ~/kyum_platform/app/backend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
gradle bootRun --args='--kakao.rest-api-key='
```

그 상태로 Step 3을 다시 돌린다.

**합격 기준:** `stops`가 비어 있지 않고 전부 `source="registry"`, 응답의 `kakaoEnabled`가 `false`. 예전에는 이 상황에서 무조건 빈 목록이었다.
끝나면 그냥 `gradle bootRun`으로 되돌린다(파일을 안 고쳤으므로 복구할 것이 없다).

- [ ] **Step 7: 결과를 기록한다**

`app/PROGRESS.md`에 한 단락을 덧붙인다(HANDOFF는 §1·§2만 덮어쓰고 기록은 PROGRESS에 쌓는 것이 이 리포의 규칙이다). 적을 것: `source="registry"` 정차지가 나왔는지, 인사이트가 붙은 정차지를 봤는지, Kakao 없이 동작했는지.

---

# Phase B — 수집에 견디는 파이프라인 (재수집 전 필수)

> **Phase B가 끝나기 전에는 `codex exec` 수집을 돌리지 않는다.** 역방향 시딩(Task 11)이 의심 구간(Task 9) 없이 돌면 이름은 같고 좌표가 벌어진 장소가 **조용히 두 번째 노드**가 되어 레지스트리를 다시 오염시킨다. 2026-08-05에 별칭 오염으로 레지스트리를 통째로 지운 것과 같은 부류의 실패다.

---

### Task 8: 조용한 유실 막기 — `eachLine` reject 배선 + 중단 런 감지

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/IngestService.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/IngestRunRepository.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/IngestServiceTest.java` (확장)

**Interfaces:**
- Produces: `IngestRunRepository.findByStatus(IngestRun.Status status) → List<IngestRun>`

**배경:** `eachLine`이 줄 단위 예외를 `log.warn`만 하고 rejects 카운터도 파일도 안 건드린다. 2026-08-05 유실의 **원인은 아니었지만**(그랬다면 warn이 남고 프로세스는 완주했어야 한다) 같은 은폐를 만드는 잠재 결함이다. 진짜 원인은 codex가 적재 JVM을 중간에 죽인 것이고, 그건 Task 10이 다룬다.

중단 감지에 **마커 파일도 exit code도 쓸 수 없다** — 샌드박스가 셸째로 죽어 둘 다 안 남는다. `IngestRun.status`만이 teardown을 견딘다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`IngestServiceTest.java`에 추가:

```java
    /** 줄 하나가 깨져도 나머지는 살아남고, 깨진 줄은 반드시 세어지고 파일에 남는다. */
    @Test
    void 줄_처리_실패는_rejects에_기록된다() throws Exception {
        Path runDir = tempRun("""
                {"record_id":"sha256:aa","record_type":"place","name_raw":"정상 장소",\
                "external_ids":{"kakao_place_id":"k1"},"lat":37.5,"lng":127.0,\
                "source":{"url":"https://example.com/1"}}
                { 이건 JSON이 아니다
                """, null);

        IngestService.Counts counts = service.ingest(runDir);

        assertThat(counts.rejects()).isEqualTo(1);
        assertThat(Files.readString(runDir.resolve("_rejects.jsonl")))
                .contains("줄 처리 실패");
        assertThat(counts.placesResolved()).isEqualTo(1); // 앞 줄은 살아남는다
    }

    /** 죽은 런은 DB에 STARTED로 남는다 — 샌드박스 teardown을 견디는 유일한 신호다. */
    @Test
    void 중단된_런이_있으면_경고를_남긴다() throws Exception {
        IngestRun dead = new IngestRun("2026-08-05T04-37Z-tour_api-seoul-junggu",
                "tour_api", java.util.Map.of(), "insight-v2");
        when(runRepo.findByStatus(IngestRun.Status.STARTED)).thenReturn(List.of(dead));

        service.ingest(tempRun("", null));

        // 경고는 로그로 나가므로 여기서는 "조회했다"만 고정한다.
        // 조회 자체가 사라지면 중단 감지 수단이 통째로 없어진다.
        verify(runRepo).findByStatus(IngestRun.Status.STARTED);
    }
```

> `tempRun(...)`·`service`·`runRepo`는 이 파일에 이미 있는 이름을 그대로 쓴다. 시그니처가 다르면 **기존 테스트의 방식을 따를 것** — 새 헬퍼를 만들지 말 것.

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.IngestServiceTest'`
Expected: FAIL — `rejects` 0 (기대 1), `findByStatus` 없음

- [ ] **Step 3: `eachLine`에서 예외를 reject로 보낸다**

`IngestService.java` — `LineHandler`/`eachLine`이 rejects 경로를 알아야 하므로 인자를 하나 더 받는다:

```java
    private void eachLine(Path file, Path rejects, Counter c, LineHandler handler) throws IOException {
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                try {
                    handler.handle(lineNo, line, mapper.readTree(line));
                } catch (Exception e) {
                    // log.warn만 하면 rejects=0 · _rejects.jsonl 0바이트 · exit 0으로
                    // 유실이 완전히 은폐된다. 실제로 그 상태를 한 번 겪었다.
                    log.warn("{}:{} 줄 처리 실패 — {}", file.getFileName(), lineNo, e.toString());
                    reject(rejects, line, "줄 처리 실패: " + e, c);
                }
            }
        }
    }
```

`ingest()` 안의 호출 두 곳을 맞춘다:

```java
            eachLine(runDir.resolve("places.jsonl"), rejects, c, (lineNo, raw, node) ->
                    ingestPlace(raw, node, runId, sourceKind, scopeKey, rejects, c));

            eachLine(runDir.resolve("insights.jsonl"), rejects, c, (lineNo, raw, node) ->
                    ingestInsight(raw, node, runId, sourceKind, promptVersion, scopeKey, rejects, c));
```

- [ ] **Step 4: 중단된 런을 감지한다**

`IngestRunRepository.java`:

```java
    /** 중단 감지 — 죽은 런은 STARTED로 영원히 남는다. 마커 파일·exit code는 쓸 수 없다. */
    List<IngestRun> findByStatus(IngestRun.Status status);
```

`IngestService.ingest()` — `IngestRun run = ...` 줄 **앞**에 넣는다:

```java
        // 이전 실행이 쓰기 도중에 죽었는지 본다. codex 샌드박스는 셸째로 프로세스를 없애므로
        // 마커 파일도 exit code도 남지 않는다 — DB에 남은 STARTED가 유일한 흔적이다.
        List<IngestRun> stalled = runRepo.findByStatus(IngestRun.Status.STARTED).stream()
                .filter(r -> !r.getRunId().equals(runId))
                .toList();
        if (!stalled.isEmpty()) {
            String ids = stalled.stream().map(IngestRun::getRunId).collect(java.util.stream.Collectors.joining(", "));
            log.warn("⚠ 완료되지 않은 이전 적재가 {}건 있습니다 — 뒷부분이 유실됐을 수 있습니다: {}", stalled.size(), ids);
            System.out.println("⚠ 미완료 적재 " + stalled.size() + "건: " + ids
                    + " — 해당 run 디렉터리로 ingest.sh를 다시 돌리면 멱등하게 복구됩니다");
        }
```

- [ ] **Step 5: 커서에도 미완료 런을 노출한다**

로그 경고는 사람이 볼 때만 쓸모가 있다. **다음 Codex 세션이 스스로 재적재를 고르려면 `state/`에 파일로 있어야 한다** (커서를 파일이 아니라 DB에 두고 매번 내보내는 것이 이 파이프라인의 규약이다 — 파일이 지워져도 DB가 이긴다).

`IngestStateExporter`에 `IngestRunRepository`를 주입하고, `export()` 끝에 한 파일을 더 쓴다:

```java
        exportScopeProgress(stateFile.resolveSibling("scope-progress.jsonl"), byScope);
        exportStalledRuns(stateFile.resolveSibling("stalled-runs.jsonl"));
        return written;
    }

    /**
     * 완료되지 않은 적재 — 다음 세션이 이 파일을 보고 재적재를 고른다(재적재는 멱등이다).
     *
     * <p>codex 샌드박스는 셸째로 프로세스를 없애므로 마커 파일도 exit code도 남지 않는다.
     * DB에 남은 {@code STARTED}가 중단을 알 수 있는 유일한 흔적이고, 이 파일이 그걸 밖으로 옮긴다.
     * 항상 원자적으로 교체한다 — 쓰는 도중에 읽히면 반쪽 파일이 커서가 된다.
     */
    private void exportStalledRuns(Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (IngestRun r : runRepo.findByStatus(IngestRun.Status.STARTED)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("run_id", r.getRunId());
                row.put("source_kind", r.getSourceKind());
                row.put("scope", r.getScope());
                row.put("prompt_version", r.getPromptVersion());
                w.write(mapper.writeValueAsString(row));
                w.newLine();
            }
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
```

⚠ **정상 상태에서 이 파일은 0바이트여야 한다.** 파일이 없는 것과 비어 있는 것은 다르다 — 항상 쓰기 때문에 "비었다"가 "중단된 적재가 없다"는 적극적 신호가 된다.

`CONTRACT.md`에 한 줄을 더한다(Task 11에서 같이 커밋해도 된다):

> `state/stalled-runs.jsonl`에 줄이 있으면 **새 수집을 시작하기 전에** 그 run 디렉터리로 `bin/ingest.sh`를 먼저 다시 돌린다. 적재는 멱등이라 중복이 쌓이지 않는다.

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.*'`
Expected: PASS. `IngestStateExporterTest` 4건이 생성자 인자 추가로 깨진다 — mock을 하나 더 넘기고 `findByStatus`가 빈 목록을 반환하도록 스텁한다.

- [ ] **Step 7: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/ app/backend/src/test/java/com/guidematch/knowledge/
git commit -m "fix(knowledge): 줄 예외를 rejects로 세고 중단된 적재를 감지·노출한다"
```

---

### Task 9: 해결 사다리 — 2차 조회 + 의심 구간

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/PlaceResolver.java`
- Modify: `app/backend/src/main/resources/application-ingest.yml`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceResolverTest.java` (확장)

**Interfaces:**
- Produces: `PlaceResolver` 생성자에 `@Value("${ingest.resolver.suspect-radius-meters:2000}") double suspectRadiusMeters` 추가 (기존 인자 뒤)

**★ 이 태스크가 Phase B에서 가장 중요하다.** 역방향 시딩(Task 11)은 정확 이름 일치를 **최대화**하는 기법이라 아래 경로를 드물게가 아니라 상시로 밟는다:

```
이름 정확 일치 · 좌표 있음 · 두 소스 좌표가 200m 넘게 벌어짐
  → within 이 빈다 → maybeCreate → tour_api는 외부 ID가 있다 → 새 노드 생성
  → 경고 없음 · rejects 없음 · name_normalized가 같은 행이 둘
```

그 다음부터 이 장소에 오는 모든 단서는 후보 2건을 물어와 **영영 ambiguous 거절**이거나, 반경 안에 하나만 걸리면 **오병합**된다. 경복궁은 한 장소가 400m 넘게 퍼져 있고, 역방향 시딩이 닿으려는 대상이 바로 그런 곳이다.

| 이름 일치 + 거리 | 판정 |
|---|---|
| ≤ 200m (`radius-meters`) | 병합 확정 (현행) |
| 200m ~ 2km (`suspect-radius-meters`) | **미해결 보관함** — 추측하지 않는다 |
| > 2km | 새 노드 (체인점·타지역 동명 장소는 진짜로 별개다) |

**`normalize()`는 바꾸지 않는다.** `name_normalized`가 저장 컬럼이라 함수를 바꾸면 기존 53행의 키가 어긋나 매칭이 도리어 나빠지고, 괄호절을 접으면 `스타벅스(명동점)`/`스타벅스(을지로점)`이 안전한 ambiguous 거절 대신 **오병합**된다. 대신 조회를 한 번 더 한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceResolverTest.java`에 추가. 반경 상수를 쓰는 필드 선언도 바꾼다:

```java
    /** 기본 반경 200m / 의심 구간 2km */
    private final PlaceResolver resolver = new PlaceResolver(placeRepo, aliasRepo, 200, 2000);
```

```java
    // ── 의심 구간 (Task 9) ──────────────────────────────────────────

    /** ★ 이름은 같은데 500m 떨어져 있다 = 경복궁형. 새 노드를 만들면 자산이 조용히 쪼개진다. */
    @Test
    void 이름이_같고_반경_밖_의심구간이면_새_노드를_만들지_않는다() {
        Place p = withId(existing("경복궁", 37.5796, 126.9770), 5L);
        // 약 500m 북쪽
        PlaceClue c = tourClue("경복궁", 37.5841, 126.9770, "t9");

        PlaceResolver.Resolution r = resolver.resolve(c);

        assertThat(r.isResolved()).isFalse();
        assertThat(r.unresolvedReason()).contains("suspect");
        verify(placeRepo, never()).save(any(Place.class));
        assertThat(p.getTourApiContentId()).isNull(); // 병합도 하지 않았다
    }

    /** 200m 이내는 지금까지처럼 병합한다. */
    @Test
    void 이름이_같고_반경_이내면_병합한다() {
        Place p = withId(existing("남대문시장", 37.5595, 126.9773), 6L);

        PlaceResolver.Resolution r = resolver.resolve(tourClue("남대문시장", 37.5596, 126.9774, "t10"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
        assertThat(p.getTourApiContentId()).isEqualTo("t10");
    }

    /** 3km 떨어진 동명 장소는 진짜로 별개다 — 이걸 보관함에 보내면 정상 시딩이 전부 샌다. */
    @Test
    void 이름이_같아도_의심구간_밖이면_새_노드다() {
        existing("스타벅스", 37.5000, 127.0000);
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 200L));

        PlaceResolver.Resolution r = resolver.resolve(tourClue("스타벅스", 37.5400, 127.0000, "t11"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place().getId()).isEqualTo(200L);
    }

    // ── 2차 조회 (Task 9) ───────────────────────────────────────────

    /** 괄호절 때문에 안 붙던 것을 붙인다. 저장 컬럼에는 언제나 정확 키가 들어간다. */
    @Test
    void 괄호절을_제거한_키로_2차_조회를_한다() {
        Place p = withId(existing("간송미술관", 37.5921, 126.9990), 8L);
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("간송미술관(서울 보화각)")))
                .thenReturn(List.of());

        PlaceResolver.Resolution r = resolver.resolve(
                tourClue("간송미술관(서울 보화각)", 37.5921, 126.9990, "t12"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
    }

    /** ★ 완화 키가 구분을 없애면 안 된다 — 지점명은 여전히 다른 장소다. */
    @Test
    void 지점명이_다른_체인은_2차_조회로도_합쳐지지_않는다() {
        assertThat(PlaceNames.normalize("스타벅스(명동점)"))
                .isNotEqualTo(PlaceNames.normalize("스타벅스(을지로점)"));

        noNameMatches();
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 300L));

        // 괄호를 제거해도 "스타벅스명동점" ≠ "스타벅스을지로점"이라 서로를 물어오지 않는다
        PlaceResolver.Resolution r = resolver.resolve(
                tourClue("스타벅스(명동점)", 37.5636, 126.9827, "t13"));
        assertThat(r.isResolved()).isTrue();
    }
```

헬퍼 2개를 추가한다:

```java
    private Place existing(String name, Double lat, Double lng) {
        return existing(name, lat, lng, null);
    }

    /** tour_api 단서 — 외부 ID가 있어 새 노드를 만들 자격이 있다(그래서 의심 구간이 필요하다). */
    private PlaceClue tourClue(String name, Double lat, Double lng, String tourId) {
        return new PlaceClue(name, List.of(), "Seoul", "중구", lat, lng,
                null, tourId, "A02>A0201>A02010700", "서울 중구 어딘가", "tour_api");
    }
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.PlaceResolverTest'`
Expected: 컴파일 실패(생성자 인자 4개) 후, 고치면 `이름이_같고_반경_밖_의심구간이면…`이 FAIL

- [ ] **Step 3: 의심 구간을 사다리에 넣는다**

`PlaceResolver.java` — 생성자:

```java
    private final double radiusMeters;

    /**
     * 이름이 같지만 반경 밖일 때, "새 노드를 만들어도 안전한 거리"의 하한(m).
     *
     * <p>이 사이 구간은 병합도 신규 생성도 하지 않고 보관함으로 보낸다. 둘 다 틀릴 수 있고
     * <b>틀린 쪽의 비용이 비대칭</b>이기 때문이다 — 오병합·중복 노드는 되돌릴 수 없지만
     * 미해결은 원본을 그대로 보관하므로 언제든 되살릴 수 있다.
     *
     * <p>2km를 넘으면 새 노드로 둔다. 서로 다른 도시의 동명 장소·체인점은 진짜로 별개이고,
     * 이걸 보관함으로 보내면 정상 시딩이 전부 그쪽으로 새 나간다.
     */
    private final double suspectRadiusMeters;

    public PlaceResolver(PlaceRepository placeRepo,
                         PlaceAliasRepository aliasRepo,
                         @Value("${ingest.resolver.radius-meters:200}") double radiusMeters,
                         @Value("${ingest.resolver.suspect-radius-meters:2000}") double suspectRadiusMeters) {
        this.placeRepo = placeRepo;
        this.aliasRepo = aliasRepo;
        this.radiusMeters = radiusMeters;
        this.suspectRadiusMeters = suspectRadiusMeters;
    }
```

`resolve()`의 2단 마지막 `return maybeCreate(...)` 줄을 아래로 교체한다:

```java
            if (within.size() > 1) {
                // 절대 추측하지 않는다 — 전국에 같은 이름의 체인점이 널려 있다
                return Resolution.unresolved(
                        "ambiguous: " + within.size() + " places share this name within radius");
            }

            // 이름은 맞는데 반경 밖이다. 얼마나 밖인지가 판단을 가른다.
            double nearest = candidates.stream()
                    .filter(p -> p.getLat() != null && p.getLng() != null)
                    .mapToDouble(p -> distanceMeters(clue, p))
                    .min().orElse(Double.MAX_VALUE);

            if (nearest <= suspectRadiusMeters) {
                // 의심 구간 — 같은 장소의 다른 입구일 수도, 다른 장소일 수도 있다.
                // 새 노드를 만들면 name_normalized가 같은 행이 둘이 되어 이후 모든 단서가
                // ambiguous 거절이거나 오병합이 된다. 조용한 중복보다 보이는 미해결이 낫다.
                return Resolution.unresolved(String.format(
                        "suspect: name matches but %.0fm away (merge radius %.0fm, suspect band %.0fm)",
                        nearest, radiusMeters, suspectRadiusMeters));
            }
            return maybeCreate(clue, String.format(
                    "name matched but nearest candidate is beyond radius (%.0fm)", radiusMeters));
```

- [ ] **Step 4: 2차 조회를 넣는다**

`candidatesByName`을 이름만 받는 형태 그대로 두고, `resolve()`에서 두 번 부른다:

```java
        String normalized = PlaceNames.normalize(clue.nameRaw());
        List<Place> candidates = candidatesByName(normalized);
        if (candidates.isEmpty()) {
            // 2차: 괄호절을 뺀 키로 한 번 더. 저장 컬럼에는 언제나 정확 키가 들어가고
            // 완화 키는 조회에만 쓴다 — 매칭을 늘리기만 하고 기존 구분을 없애지 않는다.
            String relaxed = PlaceNames.normalize(stripParenthetical(clue.nameRaw()));
            if (!relaxed.isEmpty() && !relaxed.equals(normalized)) {
                candidates = candidatesByName(relaxed);
            }
        }
```

그리고 헬퍼를 추가한다:

```java
    /**
     * 괄호절 제거 — {@code 간송미술관(서울 보화각)} → {@code 간송미술관}.
     *
     * <p>{@link PlaceNames#normalize}를 고치지 않는 이유: {@code name_normalized}는 저장된
     * 컬럼이라 함수를 바꾸면 기존 행의 키가 전부 어긋나고, 괄호절을 접으면
     * {@code 스타벅스(명동점)}과 {@code 스타벅스(을지로점)}이 같은 키가 되어 안전한 ambiguous
     * 거절 대신 오병합이 난다. 그래서 조회에만 쓰는 별도 함수로 둔다.
     */
    private static String stripParenthetical(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[（(\\[][^）)\\]]*[）)\\]]", " ").trim();
    }
```

- [ ] **Step 5: 설정에 의심 반경을 추가한다**

`application-ingest.yml`의 `ingest.resolver` 아래:

```yaml
  resolver:
    radius-meters: 200
    # 이름이 같지만 반경 밖일 때, 새 노드를 만들어도 안전하다고 보는 거리의 하한(m).
    # 200m~2km 사이는 병합도 신규 생성도 하지 않고 미해결 보관함으로 보낸다.
    #
    # 왜 중간 구간을 두는가: 역방향 시딩(Kakao 장소명으로 TourAPI를 조회)은 정확 이름 일치를
    # 최대화하는 기법이라 "이름은 같은데 좌표가 벌어진" 경우를 상시로 만든다. 경복궁처럼 한
    # 부지가 400m 넘게 퍼진 곳이 딱 그렇다. 여기서 새 노드를 만들면 name_normalized가 같은 행이
    # 둘이 되어 이후 모든 단서가 ambiguous 거절이거나 오병합이 된다 — 조용하고 되돌릴 수 없다.
    # 2km를 넘으면 다른 도시의 동명 장소·체인점이라 새 노드가 맞다.
    suspect-radius-meters: 2000
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.*'`
Expected: PASS. **기존 테스트 중 "반경 밖이면 새 노드를 만든다"류가 깨질 수 있다** — 거리가 의심 구간 안이면 이제 미해결이 정답이므로, 그 테스트의 좌표를 2km 밖으로 옮기거나 기대값을 미해결로 바꾼다. 어느 쪽이 맞는지는 테스트 이름이 말해준다.

- [ ] **Step 7: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/PlaceResolver.java \
        app/backend/src/main/resources/application-ingest.yml \
        app/backend/src/test/java/com/guidematch/knowledge/PlaceResolverTest.java
git commit -m "fix(knowledge): 해결 사다리에 의심 구간 + 괄호절 2차 조회"
```

---

### Task 10: 왕복 축소 — 레지스트리 스냅샷 + 청크 트랜잭션

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/knowledge/RegistrySnapshot.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/PlaceResolver.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/IngestService.java`
- Modify: `app/backend/src/main/resources/application-ingest.yml`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceResolverTest.java` (확장), `IngestServiceTest.java` (생성자 인자 추가로 수정 필요)

**Interfaces:**
- Produces:
  - `RegistrySnapshot.loadAll(PlaceRepository, PlaceAliasRepository, IngestSourceRepository) → RegistrySnapshot`
  - `RegistrySnapshot.of(List<Place>, List<PlaceAlias>, Set<String> sourceUrlHashes)`
  - `RegistrySnapshot.empty()` / `isLoaded()` / `byKakaoId(String)` / `byTourApiId(String)` / `byName(String)` / `add(Place)` / `hasSourceHash(String)` / `rememberSourceHash(String)`
  - `PlaceResolver.resolve(PlaceClue clue, RegistrySnapshot snapshot)` (기존 1인자 오버로드는 `resolve(clue, RegistrySnapshot.empty())`로 위임)

**배경 (실측):** `time ./bin/ingest.sh` = **58.1초**. 장소 1건당 조회 4회 + 저장 ≈ 8왕복 × 250ms(Sydney) ≈ 2초, 40건이면 80초 — 실측과 맞는다. codex 세션이 그 전에 끝나면 JVM이 쓰기 도중 죽고 **유실은 항상 파일 뒷부분**이다.

**★ 왜 "범위"가 아니라 "전체"를 올리는가 — 이걸 틀리면 조용한 데이터 손상이 난다.**

처음 설계는 매니페스트의 `scope.city/district`로 한 범위만 올리는 것이었다. **그건 틀렸다.** `kakao_place_id`·`tour_api_content_id`는 `uk_places_kakao`·`uk_places_tour_api`로 **전역 유니크**인데, 저장된 `district`가 매니페스트 범위와 다른 행이 얼마든지 있을 수 있다(실제로 `간송미술관`은 주소가 성북구인데 `district='중구'`로 들어가 있다 — 역방향 시딩이 이런 행을 본격적으로 만들어낸다).

범위 스냅샷이면: 기존 장소인데 스냅샷에 없음 → 미스 → `maybeCreate` → INSERT → **unique 제약 위반**. Task 8 이후에는 이게 reject로 나와, 어제까지 멱등이던 재적재가 갑자기 실패한다.

그래서 `places`·`place_aliases`를 **통째로** 올린다. 그러면 **스냅샷의 미스가 곧 DB의 미스**이므로 폴백 없이도 사다리 의미가 정확히 보존된다.

> 현재 53행. 전국 수집이 진행돼도 수만 행 수준이며 배치 프로세스라 메모리 부담이 없다. 로드 시 행 수를 로그로 남기고, **10만 행을 넘으면 이 결정을 다시 볼 것**(그때는 범위 스냅샷 + DB 폴백으로 바꾼다 — 절약은 줄지만 안전하다).

**설계 제약 — 이걸 어기면 조용히 망가진다:** 스냅샷은 해결 사다리의 **의미를 바꾸면 안 된다.** 1단→2단→3단 순서와 ambiguous 거절 규칙이 동일해야 하고, **같은 실행에서 새로 만든 장소는 스냅샷에도 즉시 반영**돼야 한다(그래야 같은 파일 안의 중복 장소가 두 노드로 쪼개지지 않는다).

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceResolverTest.java`에 추가:

```java
    // ── 레지스트리 스냅샷 (Task 10) ─────────────────────────────────

    /** 스냅샷이 있으면 읽기 왕복이 0이다 — 이게 적재 시간을 줄이는 지점이다. */
    @Test
    void 스냅샷이_있으면_조회_왕복이_없다() {
        Place known = withId(new Place("덕수궁", "Seoul", "중구", 37.5656, 126.9749,
                "k1", null, "여행 > 관광,명소", "서울 중구"), 1L);
        RegistrySnapshot snap = RegistrySnapshot.of(List.of(known), List.of(), java.util.Set.of());

        PlaceResolver.Resolution r = resolver.resolve(
                new PlaceClue("덕수궁", List.of(), "Seoul", "중구", 37.5656, 126.9749,
                        "k1", null, "여행 > 관광,명소", "서울 중구", "kakao_local"), snap);

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(known);
        verifyNoInteractions(placeRepo);
    }

    /**
     * ★ 스냅샷의 미스는 곧 DB의 미스여야 한다 — 그래야 폴백 없이 사다리 의미가 보존된다.
     *
     * <p>매니페스트 범위(중구)와 저장된 district(성북구)가 달라도 외부 ID로 찾아진다.
     * 범위 스냅샷이었다면 여기서 미스 → 새 노드 생성 → unique 제약 위반이 났다.
     */
    @Test
    void 저장된_구가_매니페스트_범위와_달라도_외부ID로_찾는다() {
        Place known = withId(new Place("간송미술관", "Seoul", "성북구", 37.5921, 126.9990,
                null, "130511", "A02>A0206>A02060500", null), 9L);
        RegistrySnapshot snap = RegistrySnapshot.of(List.of(known), List.of(), java.util.Set.of());

        PlaceResolver.Resolution r = resolver.resolve(
                new PlaceClue("간송미술관", List.of(), "Seoul", "중구", 37.5921, 126.9990,
                        null, "130511", "A02>A0206>A02060500", null, "tour_api"), snap);

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(known);
        verifyNoInteractions(placeRepo);   // 새 노드를 만들지 않았다
    }

    /** ★ 같은 파일 안의 두 번째 등장이 새 노드가 되면 안 된다. */
    @Test
    void 같은_실행에서_만든_장소가_뒷줄에_보인다() {
        RegistrySnapshot snap = RegistrySnapshot.of(List.of(), List.of(), java.util.Set.of());
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 42L));

        PlaceClue first = new PlaceClue("남산골한옥마을", List.of(), "Seoul", "중구",
                37.5594, 126.9940, "k7", null, "여행 > 관광,명소", "서울 중구", "kakao_local");
        PlaceResolver.Resolution a = resolver.resolve(first, snap);
        PlaceResolver.Resolution b = resolver.resolve(first, snap);

        assertThat(a.place()).isSameAs(b.place());
        verify(placeRepo, times(1)).save(any(Place.class)); // 두 번째는 새로 만들지 않는다
    }

    /** 사다리 의미가 스냅샷 유무로 달라지면 안 된다 — ambiguous는 여전히 거절이다. */
    @Test
    void 스냅샷에서도_ambiguous는_거절한다() {
        Place a = withId(new Place("스타벅스", "Seoul", "중구", 37.5636, 126.9827,
                "k1", null, "음식점 > 카페", null), 1L);
        Place b = withId(new Place("스타벅스", "Seoul", "중구", 37.5637, 126.9828,
                "k2", null, "음식점 > 카페", null), 2L);
        RegistrySnapshot snap = RegistrySnapshot.of(List.of(a, b), List.of(), java.util.Set.of());

        PlaceResolver.Resolution r = resolver.resolve(
                new PlaceClue("스타벅스", List.of(), "Seoul", "중구", 37.5636, 126.9827,
                        null, "t1", "A05>A0502>A05020900", null, "tour_api"), snap);

        assertThat(r.isResolved()).isFalse();
        assertThat(r.unresolvedReason()).contains("ambiguous");
    }
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `gradle test --tests 'com.guidematch.knowledge.PlaceResolverTest'`
Expected: 컴파일 실패 — `RegistrySnapshot` 없음

- [ ] **Step 3: `RegistrySnapshot`을 만든다**

```java
package com.guidematch.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 레지스트리 전체(장소·별칭·이미 본 소스 URL)를 메모리에 올린 것.
 *
 * <p><b>왜 필요한가</b>: 적재는 장소 1건당 조회 4회 + 저장으로 ≈8왕복을 쓰는데 Supabase가
 * Sydney라 왕복이 250ms다. 40건에 58초가 걸리고, {@code codex exec}는 그 전에 세션을 끝내
 * <b>JVM을 쓰기 도중에 죽인다.</b> 유실은 항상 파일 뒷부분이고 {@code exit 0}으로 보고된다.
 *
 * <p><b>왜 범위가 아니라 전체인가</b>: {@code kakao_place_id}·{@code tour_api_content_id}는
 * 전역 유니크 제약이 걸려 있는데, 저장된 {@code district}가 매니페스트 범위와 다른 행이 있다
 * (실측: 간송미술관은 주소가 성북구인데 {@code district='중구'}로 들어가 있다). 범위만 올리면
 * 기존 장소가 미스로 나와 새 노드를 만들려다 <b>unique 제약 위반</b>이 난다. 전체를 올리면
 * <b>스냅샷의 미스 = DB의 미스</b>이므로 폴백 없이도 사다리 의미가 정확히 보존된다.
 * 현재 53행이고 수만 행까지 문제없다. 10만 행을 넘으면 이 결정을 다시 볼 것.
 *
 * <p><b>불변 조건</b>: 이 클래스는 해결 사다리의 <b>의미를 바꾸지 않는다.</b> 후보를 어디서
 * 가져오는지만 바꿀 뿐, 순서·거리 판정·ambiguous 거절은 {@link PlaceResolver}가 그대로 한다.
 * 그리고 같은 실행에서 만든 장소는 {@link #add}로 즉시 반영돼야 한다 — 안 그러면 같은 파일
 * 안의 두 번째 등장이 새 노드가 되어 자산이 쪼개진다.
 */
public final class RegistrySnapshot {

    private final Map<String, Place> byKakaoId = new HashMap<>();
    private final Map<String, Place> byTourApiId = new HashMap<>();
    private final Map<String, List<Place>> byName = new LinkedHashMap<>();
    /** 이미 본 소스 URL 해시 — touchSource의 findByUrlHash 왕복을 없앤다. */
    private final Set<String> sourceUrlHashes = new HashSet<>();
    private final boolean loaded;

    private RegistrySnapshot(boolean loaded) {
        this.loaded = loaded;
    }

    /** 스냅샷 없이 동작하는 모드 — 기존처럼 매번 DB를 친다(테스트·단발 호출용). */
    public static RegistrySnapshot empty() {
        return new RegistrySnapshot(false);
    }

    public static RegistrySnapshot of(List<Place> places, List<PlaceAlias> aliases,
                                      Set<String> sourceUrlHashes) {
        RegistrySnapshot s = new RegistrySnapshot(true);
        places.forEach(s::add);
        Map<Long, Place> byId = new HashMap<>();
        places.forEach(p -> byId.put(p.getId(), p));
        for (PlaceAlias a : aliases) {
            Place p = byId.get(a.getPlaceId());
            if (p != null) s.byName.computeIfAbsent(a.getAliasNormalized(), k -> new ArrayList<>()).add(p);
        }
        s.sourceUrlHashes.addAll(sourceUrlHashes);
        return s;
    }

    public static RegistrySnapshot loadAll(PlaceRepository placeRepo,
                                           PlaceAliasRepository aliasRepo,
                                           IngestSourceRepository sourceRepo) {
        List<Place> places = placeRepo.findAll();
        List<PlaceAlias> aliases = aliasRepo.findAll();
        Set<String> hashes = sourceRepo.findAll().stream()
                .map(IngestSource::getUrlHash).collect(java.util.stream.Collectors.toSet());
        return of(places, aliases, hashes);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int placeCount() {
        return byKakaoId.size() + byTourApiId.size();
    }

    /** 같은 실행에서 새로 만든 장소를 즉시 보이게 한다. */
    public void add(Place p) {
        if (p.getKakaoPlaceId() != null) byKakaoId.put(p.getKakaoPlaceId(), p);
        if (p.getTourApiContentId() != null) byTourApiId.put(p.getTourApiContentId(), p);
        byName.computeIfAbsent(p.getNameNormalized(), k -> new ArrayList<>()).add(p);
    }

    public Optional<Place> byKakaoId(String id) {
        return Optional.ofNullable(id == null ? null : byKakaoId.get(id));
    }

    public Optional<Place> byTourApiId(String id) {
        return Optional.ofNullable(id == null ? null : byTourApiId.get(id));
    }

    public List<Place> byName(String normalized) {
        return byName.getOrDefault(normalized, List.of());
    }

    public boolean hasSourceHash(String hash) {
        return sourceUrlHashes.contains(hash);
    }

    public void rememberSourceHash(String hash) {
        sourceUrlHashes.add(hash);
    }
}
```

`PlaceAlias`에 `getAliasNormalized()`가, `IngestSource`에 `getUrlHash()`가 없으면 추가한다(두 필드 모두 이미 있다).

- [ ] **Step 4: `PlaceResolver`가 스냅샷을 먼저 보게 한다**

`resolve(PlaceClue)`는 `resolve(clue, RegistrySnapshot.empty())`로 위임하고, 새 2인자 버전에서 **후보 조회 두 곳만** 스냅샷 우선으로 바꾼다. 사다리 구조는 손대지 않는다.

⚠ **스냅샷이 로드됐으면 미스에도 DB로 폴백하지 않는다.** 전체를 올렸으므로 스냅샷의 미스는 DB의 미스이고, 폴백을 넣으면 신규 장소마다 헛왕복이 생겨 절약이 사라진다. **이 전제는 `loadAll`이 범위 필터 없이 전체를 읽는다는 데 전적으로 의존한다** — 나중에 범위 로딩으로 바꾸려면 반드시 폴백을 같이 넣어야 한다.

```java
    public Resolution resolve(PlaceClue clue) {
        return resolve(clue, RegistrySnapshot.empty());
    }

    public Resolution resolve(PlaceClue clue, RegistrySnapshot snapshot) {
        // ── 1단: 외부 ID는 곧 신원이다 ──────────────────────────────
        Optional<Place> byExternalId = findByExternalId(clue, snapshot);
        ... (이하 기존 흐름 그대로, candidatesByName(x) → candidatesByName(x, snapshot))
    }

    private Optional<Place> findByExternalId(PlaceClue clue, RegistrySnapshot snapshot) {
        if (notBlank(clue.kakaoPlaceId())) {
            Optional<Place> hit = snapshot.isLoaded()
                    ? snapshot.byKakaoId(clue.kakaoPlaceId())
                    : placeRepo.findByKakaoPlaceId(clue.kakaoPlaceId());
            if (hit.isPresent()) return hit;
        }
        if (notBlank(clue.tourApiContentId())) {
            return snapshot.isLoaded()
                    ? snapshot.byTourApiId(clue.tourApiContentId())
                    : placeRepo.findByTourApiContentId(clue.tourApiContentId());
        }
        return Optional.empty();
    }

    private List<Place> candidatesByName(String normalized, RegistrySnapshot snapshot) {
        if (normalized.isEmpty()) return List.of();
        if (snapshot.isLoaded()) {
            // 스냅샷은 장소와 별칭을 이미 같은 맵에 합쳐 두었다 — 중복은 id로 제거한다
            Map<Long, Place> merged = new LinkedHashMap<>();
            for (Place p : snapshot.byName(normalized)) merged.put(keyOf(p), p);
            return new ArrayList<>(merged.values());
        }
        ... (기존 DB 경로 그대로)
    }
```

`maybeCreate`가 장소를 만든 직후 스냅샷에 넣는다:

```java
    private Resolution maybeCreate(PlaceClue clue, String reasonIfNotCreated, RegistrySnapshot snapshot) {
        if (!clue.hasExternalId()) {
            return Resolution.unresolved(reasonIfNotCreated);
        }
        Place created = placeRepo.save(new Place(...));
        recordAliases(created, clue);
        // 같은 파일 안의 두 번째 등장이 새 노드가 되지 않도록 즉시 보이게 한다
        snapshot.add(created);
        return Resolution.resolved(created);
    }
```

- [ ] **Step 5: `IngestService`가 스냅샷을 만들어 넘긴다**

`ingest()`에서 레지스트리 전체를 한 번 올린다:

```java
        // 레지스트리 스냅샷 — 읽기 왕복이 장소당 4회에서 0회가 된다
        RegistrySnapshot snapshot = RegistrySnapshot.loadAll(placeRepo, aliasRepo, sourceRepo);
        log.info("스냅샷 로드 완료 — 장소 {}건", snapshot.placeCount());
```

`IngestService` 생성자에 `PlaceAliasRepository aliasRepo`를 추가하고, `ingestPlace`·`ingestInsight`에 `snapshot`을 인자로 넘겨 `resolver.resolve(clue, snapshot)`을 호출한다.

`touchSource`도 스냅샷을 쓴다 — 이게 남은 왕복의 절반이다:

```java
    private void touchSource(JsonNode sourceNode, String sourceKind, String scopeKey,
                             String runId, RegistrySnapshot snapshot) {
        String url = text(sourceNode.path("url"), null);
        if (url == null) return;
        String hash = sha256(url);
        // 이미 본 URL이면 갱신할 값이 last_seen_run/at뿐이다. 재적재 때마다 조회+수정 왕복을
        // 두 번 쓰느니 건너뛴다 — 커서의 의미(이 URL을 봤다)는 이미 기록돼 있다.
        if (snapshot.isLoaded() && snapshot.hasSourceHash(hash)) return;
        if (!snapshot.isLoaded() && sourceRepo.findByUrlHash(hash).isPresent()) return;
        sourceRepo.save(new IngestSource(hash, url, sourceKind, scopeKey, runId));
        snapshot.rememberSourceHash(hash);
    }
```

⚠ 이 변경으로 **기존 소스 행의 `last_seen_run`이 더 이상 갱신되지 않는다.** 커서(`ingested-sources.jsonl`)는 "이 URL을 봤다"만 쓰고 최신 run을 쓰지 않으므로 동작은 같다. 다르게 쓰고 싶다면 `IngestSourceRepository`에 `@Modifying` 벌크 UPDATE를 하나 두고 실행 끝에 한 번만 부를 것 — 행마다 왕복하는 방식으로 되돌리지 말 것.

- [ ] **Step 6: 쓰기를 청크 트랜잭션으로 묶는다 — 이게 없으면 `batch_size`가 아무 일도 안 한다**

⚠ **주의**: `IngestService.ingest`는 의도적으로 `@Transactional`이 아니다(줄 단위 커밋이라 한 줄이 깨져도 나머지가 살아남는다). 그래서 `placeRepo.save()` 하나하나가 **각자 트랜잭션**이고, `hibernate.jdbc.batch_size`만 켜면 **묶을 대상이 없어 아무 효과가 없다.** 설정만 넣고 넘어가면 Step 8에서 시간이 그대로다.

`TransactionTemplate`으로 N줄씩 묶는다(새 의존성 없음 — `spring-tx`는 이미 있다):

```java
    private static final int CHUNK_LINES = 100;

    private final TransactionTemplate txTemplate;   // 생성자에서 new TransactionTemplate(txManager)

    /**
     * 줄 묶음을 한 트랜잭션에서 처리한다.
     *
     * <p>배치 전체를 한 트랜잭션으로 묶지 않는 이유는 그대로다 — 마지막 줄의 오류가 앞의 성과를
     * 통째로 되돌리면 25분짜리 수집을 버리는 셈이다. 100줄 단위면 최악의 손실이 100줄이고,
     * 재적재는 멱등이라 그마저 복구된다. 대신 이 묶음 덕에 {@code jdbc.batch_size}가 실제로 동작한다.
     */
    private void inChunk(Runnable work) {
        txTemplate.executeWithoutResult(status -> work.run());
    }
```

`eachLine`을 100줄씩 모아 `inChunk(...)` 안에서 핸들러를 호출하도록 바꾼다. 줄 단위 try/catch는 **청크 안에**서 유지한다(한 줄의 예외가 청크를 롤백시키면 안 되므로, 예외는 잡아 reject로 보내고 계속 진행한다).

- [ ] **Step 7: 배치 설정을 켠다**

`application-ingest.yml`의 `spring.jpa` 아래:

```yaml
    properties:
      hibernate:
        # Step 6의 청크 트랜잭션이 있어야 이 값이 의미를 갖는다.
        # 트랜잭션이 줄마다 끊기면 묶을 대상이 없어 아무 효과가 없다.
        jdbc.batch_size: 50
        order_inserts: true
        order_updates: true
```

- [ ] **Step 8: 테스트가 통과하는지 확인한다**

Run: `gradle test`
Expected: 전부 PASS. `IngestServiceTest`가 `TransactionTemplate` 인자 추가로 깨진다 — 목 대신 **실제 동작하는 스텁**을 넘긴다(`new TransactionTemplate(new org.springframework.transaction.support.AbstractPlatformTransactionManager() {...})`는 과하다). 가장 간단한 방법은 `TransactionTemplate`을 mock하고 `executeWithoutResult`가 넘겨받은 콜백을 즉시 실행하도록 `doAnswer`로 스텁하는 것이다 — **안 하면 적재가 아무 일도 안 한 것처럼 보이고 원인 찾기가 오래 걸린다.**

- [ ] **Step 9: 실제 실행 시간을 잰다 — 이 태스크의 합격 기준**

```bash
cd ~/kyum_platform
./scripts/ingest/build-jar.sh          # src/main을 고쳤으므로 필수
cd ~/peerup-ingest
time ./bin/ingest.sh runs/2026-08-05T05-12Z-kakao-seoul-junggu
```

**합격 기준:** **20초 미만** (착수 실측 58.1초). 그리고 **행 수 불변**(멱등성):

```sql
SELECT 'places' t, count(*) FROM places
UNION ALL SELECT 'place_aliases', count(*) FROM place_aliases
UNION ALL SELECT 'place_insights', count(*) FROM place_insights;
```

**측정값을 반드시 기록할 것** — 15초를 목표로 잡되 20초를 게이트로 둔 이유는, 남는 비용이 장소당 `placeRepo.save()`의 merge SELECT이기 때문이다. 20초를 못 넘기면 다음 레버는 이 순서다:

1. 이미 스냅샷에서 찾아온(=변경 없는) 장소는 `save()`를 아예 부르지 않는다 — 스냅샷 엔티티는 detached라 merge가 SELECT를 유발한다
2. 새로 만드는 장소만 모아 `saveAll`로 한 번에 넣는다
3. 그래도 안 되면 SQL 로그를 켜고(`org.hibernate.SQL: DEBUG`) 실제 쿼리 수를 센다 — 추측하지 말 것

⚠ `build-jar.sh`를 빼먹으면 `ingest.sh`의 최신성 가드가 **exit 3**으로 멈춘다(옛 코드로 조용히 적재하는 것보다 낫다).
⚠ `INGEST_DB_*` 사전점검에 걸려 **exit 4**가 나면 `.env`의 적재 롤 자격증명 문제다(`peerup_ingest.<project-ref>` 접미사 · 비밀번호에 `# $ ' "` 금지).

- [ ] **Step 10: 커밋 (사용자 승인 후)**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/ app/backend/src/main/resources/application-ingest.yml
git commit -m "perf(knowledge): 레지스트리 스냅샷 + 청크 트랜잭션으로 적재 왕복 축소"
```

---

### Task 11: 계약 3조항 + 프롬프트 v4 + 역방향 시딩

**Files:**
- Modify: `docs/ingest/CONTRACT.md`
- Modify: `docs/ingest/codex-ingest-prompt.md`
- Modify: `docs/ingest/sources.yml`
- Modify: `docs/ingest/schema/place.schema.json`

**코드 변경 없음.** 이 태스크는 외부 에이전트가 지키는 규칙을 고친다. **지시하지 않은 것은 지켜지지 않는다** — 2026-08-05에 v2 프롬프트에 이름/별칭 규칙이 한 줄도 없어서 에이전트가 스스로 분할 규칙을 발명해 레지스트리를 오염시켰다.

- [ ] **Step 1: `CONTRACT.md`에 `place_kind` 판정 규칙을 싣는다**

새 절을 만들고 Task 1의 매핑표를 그대로 옮긴다. 핵심 문장:

> 추출기는 `category_raw`를 **원문 그대로** 실어 보내기만 한다. 종류 판정은 우리 코드(`PlaceKinds`)가 한다.
> 분류 키를 외부 에이전트가 정하게 두면 프롬프트가 바뀌는 순간 같은 장소가 다른 종류로 들어온다.
> TourAPI는 `cat1>cat2>cat3` 분류코드를, Kakao는 카테고리 경로를 그대로 보낸다. **가공하지 마라.**

- [ ] **Step 2: 축제 조항을 넣는다 (사용자 결정 반영 — 삭제가 아니라 배제)**

> **축제·공연·행사(TourAPI `cat2 = A0207`·`A0208`)는 장소가 아니라 사건이다.**
> 적재를 막지는 않는다 — 막으면 매 실행이 같은 것을 다시 가져와 `_rejects.jsonl`만 부풀린다.
> 대신 `place_kind = EVENT`로 분류되어 **정차지 후보에서 빠진다.**
> ⚠ `best_season` 같은 사실에 **개최일을 넣지 마라.** "행사가 열리는 시기는 11월"은 장소 속성이 아니다.
> 계획을 세울 때는 `A0207`·`A0208`이 아닌 콘텐츠를 우선한다 — 그쪽에만 오래 가는 사실이 있다.

- [ ] **Step 3: 부분 수집 조항을 넣는다**

> 현행 계약은 "0건이면 완료로 기록하지 마라"만 있고 **부분 수집**을 막지 않는다.
> 실제로 `Seoul/중구 · tour_api`가 `areaBasedList2` 제목순 1페이지 13건(가~금)만 훑고 완료로 기록됐고,
> `refresh_after_days: 90`이라 **90일간 재방문하지 않는다.** overview 1264자가 확인된 경복궁은 수집되지도 않았다.
>
> - 커서 항목에 **페이지 소진 여부**를 싣는다. 소진하지 못했으면 완료로 기록하지 않는다.
> - 지금 `Seoul/중구 · tour_api` 줄은 무효로 본다(재수집 대상).

- [ ] **Step 4: 이름·별칭 규칙을 재확인한다**

v3에서 추가된 "`name_raw`를 쪼개지 마라"는 그대로 둔다. 실측 대조표(`서소문성지역사박물관`을 `서소문성지`+`역사박물관`으로 쪼갠 사고)도 그대로 남긴다. **역방향 시딩이 이 규칙을 다시 시험하므로 지우지 말 것.**

- [ ] **Step 5: `sources.yml`에 `searchKeyword2`를 등록한다**

`tour_api.api.endpoints`에 추가한다. 실측 결과(직접 호출로 확인):

| 질의 | 적중 | 비고 |
|---|---|---|
| 남대문시장 | 1 | 제목 정확 일치 → 200m 안이면 즉시 병합 |
| 숭례문 | 2 | 동일 |
| 남산케이블카 | 1 | 동일 |
| 덕수궁 | 2 | `덕수궁 대한문`·`덕수궁 돌담길`만 — 괄호절 2차 조회로도 안 붙는다 |
| 한국은행 화폐박물관 | 0 | 미적중 |

- [ ] **Step 6: 프롬프트를 `insight-v4`로 올린다**

`codex-ingest-prompt.md`의 `prompt_version`을 `insight-v4`로 바꾸고 아래를 추가한다. 버전을 올리는 이유는 기존 9건이 `v2`로 남아 추적 가능해야 하기 때문이다.

**(가) overview를 반드시 읽는다**

> 인사이트 9건 전부 evidence가 `detailIntro2`이고 `detailCommon2`(overview) 유래가 **0건**이었다.
> `note_i18n.ko`가 `"입장료 무료 행사"`로 **5회 바이트 동일**했고, 한옥(관훈동 민씨 가옥)에 `"행사 운영 시간"`이 붙었다.
> 프로즈 독해가 아니라 필드 템플릿이었다는 뜻이다. 13곳을 6초에 처리했고 vibe·photo_spot·caution은 하나도 안 나왔다.
>
> - `detailCommon2`의 `overview`를 **반드시 읽고** 거기서 `VIBE`/`PHOTO_SPOT`/`BEST_TIME`을 뽑는다.
> - **필드 값을 문장 틀에 끼워 넣지 마라.** 같은 문구가 여러 장소에 반복되면 그건 사실이 아니다.
> - overview에서 뽑은 사실은 `evidence.url`에 **`detailCommon2` 호출 URL을 그대로** 싣는다.
>   완료조건이 이 문자열로 질의하므로, 이걸 강제하지 않으면 검증 자체가 불가능하다.

**(나) 역방향 시딩 절차**

> `areaBasedList2` 페이징으로 커버리지를 늘리면 알파벳 표본 문제가 반복되고 Kakao 집합과 겹칠 보장이 없다.
> 대신 **이미 레지스트리에 있는 Kakao 장소명을 질의어로 `searchKeyword2`를 역조회**한다. 겹침이 구조적으로 보장된다.
> 질의어 목록은 `state/`에 내보낸 커서에서 해당 범위의 장소명을 쓴다.

**(다) API 함정 (실측)**

> - `detailCommon2`에 `contentTypeId`를 넣으면 400 — `detailIntro2`에는 필수다
> - `defaultYN`류 YN 플래그는 KorService2에서 폐지됐다
> - 없는 contentId면 `body.items`가 객체가 아니라 **빈 문자열**로 온다
> - 오류코드 `30`/403 = 키 문제 · `10`/400 = 키는 통과, 파라미터 문제
> - 키는 **디코딩 키**를 쓴다(인코딩 키를 쿼리스트링에 그대로 쓰면 이중 인코딩으로 401)

- [ ] **Step 7: `place.schema.json`은 그대로 둔다**

`address_raw`·`category_raw`가 이미 정의돼 있다. 스키마는 처음부터 맞았고 **읽는 쪽(`PlaceClue`)이 빠져 있었을 뿐**이다(Task 2에서 해결). 변경 불필요 — 이 단계는 "확인했고 안 바꿨다"를 기록하기 위한 것이다.

- [ ] **Step 8: 커밋 (사용자 승인 후)**

```bash
git add docs/ingest/
git commit -m "docs(ingest): 계약 3조항(종류·행사·부분수집) + 프롬프트 v4 + 역방향 시딩"
```

---

### Task 12: 기존 run 재적재 + 최종 검증

**Files:** 없음 (검증만)

**설계서는 `address_ko`를 "재수집 때 채워진다"고 했지만, 실측 결과 재수집이 필요 없다.** 기존 run 디렉터리의 JSONL에 `address_raw`가 이미 전부 들어 있고(kakao 40/40, tour 13/13) 적재는 멱등이라, **다시 돌리기만 하면** `enrichMissing`이 주소를 채운다.

- [ ] **Step 1: jar을 다시 빌드한다**

```bash
cd ~/kyum_platform && ./scripts/ingest/build-jar.sh
```

⚠ `cp -p`(mtime 보존)를 쓰면 안 된다 — 설치 후 `touch`로 지금 시각을 찍는 것이 가드가 풀리는 조건이다. 스크립트가 이미 그렇게 하지만, 손으로 복사했다면 확인할 것.

- [ ] **Step 2: 기존 run 두 개를 재적재한다 (codex 밖에서)**

```bash
cd ~/peerup-ingest
time ./bin/ingest.sh runs/2026-08-05T05-12Z-kakao-seoul-junggu
time ./bin/ingest.sh runs/2026-08-05T04-37Z-tour_api-seoul-junggu
```

각각 요약줄(`적재 완료 run=… {…}`)이 나와야 한다. **요약줄이 없으면 완주하지 못한 것이다.**

- [ ] **Step 3: 중간 검증 4가지를 확인한다**

```sql
-- 1) 종류가 비어 있는 행이 없다
SELECT count(*) FROM places WHERE place_kind IS NULL;                      -- 기대: 0

-- 2) 주소가 채워졌다 (착수 시점엔 컬럼 자체가 없었다)
SELECT count(*) FILTER (WHERE address_ko IS NOT NULL) filled, count(*) total FROM places;
                                                                            -- 기대: filled = total = 53

-- 3) 재적재해도 행 수가 그대로다 (멱등성)
SELECT 'places' t, count(*) FROM places
UNION ALL SELECT 'place_aliases', count(*) FROM place_aliases
UNION ALL SELECT 'place_insights', count(*) FROM place_insights;            -- 기대: 53 / 0 / 9

-- 4) 종류 분포에 EVENT 3건이 있다 (축제가 제대로 격리됐다)
SELECT place_kind, count(*) FROM places GROUP BY 1 ORDER BY 2 DESC;
```

- [ ] **Step 4: 중단 감지가 실제로 보이는지 확인한다**

```sql
SELECT run_id, status FROM ingest_runs WHERE status = 'STARTED';
```

기대: 0건(모두 COMPLETED). 그리고 커서 파일도 같이 본다:

```bash
wc -c ~/peerup-ingest/state/stalled-runs.jsonl   # 기대: 0바이트 (파일은 존재)
```

파일이 **없으면** Task 8 Step 5가 안 붙은 것이고, **줄이 있으면** 그 run 디렉터리로 `ingest.sh`를 먼저 다시 돌려야 한다.

- [ ] **Step 5: Phase A 스모크를 다시 돌린다**

Task 7 Step 3을 다시 실행한다. 이번엔 정차지에 **주소가 실려 있어야 한다**(`address` 필드가 `null`이 아님). 착수 시점에는 레지스트리 정차지만 주소가 비어 출처에 따라 응답이 들쭉날쭉했을 것이다.

- [ ] **Step 6: 남은 것을 기록한다**

`app/PROGRESS.md`에 결과를 적고, `HANDOFF.md` §1·§2를 **덮어쓴다**(쌓지 않는다).

**아직 못 한 것 (다음 라운드):**

- **완료조건의 후반부** — `evidence.url`이 `detailCommon2`인 인사이트가 붙은 정차지 ≥ 1 — 은 **현재 데이터로 증명 불가**하다. 인사이트 9건이 전부 `insight-v2`의 필드 템플릿 산물이기 때문이다. 🙋 **사용자가 `codex exec`로 `Seoul/중구`를 v4로 재수집**해야 확인할 수 있다:
  ```
  codex exec --cd ~/peerup-ingest --skip-git-repo-check \
    --sandbox workspace-write -c sandbox_workspace_write.network_access=true \
    < <프롬프트 파일>
  ```
  ⚠ `--cd`가 없으면 쓰기 루트가 앱 리포가 되어 격리 설계가 통째로 무너진다. 프롬프트는 **stdin**으로 준다.
- **Codex [예약된 작업] 등록**은 재수집 1회가 완주하는 것을 눈으로 본 뒤에 한다. Task 10이 실행 시간을 줄였지만, codex 세션 안에서 완주하는지는 실제 수집 실행으로만 확인된다.

---

## 완료조건

**Phase A (Task 7에서 판정, 사람 개입 최소):**

> `GET /api/courses/recommend?city=Seoul&district=중구` 응답에서 **`source="registry"` 정차지 ≥ 1**

"정차지가 나온다"는 합격 신호가 아니다 — Kakao 폴백이 언제나 채워주므로 백필을 통째로 빠뜨려도 응답은 정상이고 결과는 예전과 동일하다.

**Phase B (Task 12에서 판정):**

- `place_kind`가 NULL인 `places` 행 = 0
- `address_ko`가 채워진 행 = 전체
- `ingest.sh` 실행 시간 < 15초 (착수 시 58.1초)
- 재적재 후 행 수 불변

**최종 (사용자 재수집 후 — 이번 라운드 밖):**

> `source="registry"` 정차지 중 **`evidence.url`이 `detailCommon2`인 인사이트가 붙은 정차지 ≥ 1**
> 그리고 `kakao_place_id`와 `tour_api_content_id`를 **둘 다** 가진 `places` 행 > 0 (지금은 0)

앞만 보면 백필 누락(조용한 실패)을 놓치고, 뒤만 보면 필드 템플릿 추출을 놓친다. 둘 다 필요하다.

---

## 범위 밖 (설계서 §10 그대로)

| 항목 | 어디로 |
|---|---|
| 블로그·리뷰 LLM 추출로 vibe·혼잡도 만들기 | 하위 프로젝트 2 |
| 여행자 화면에 인사이트 노출 · SEO · "이 코스 가능한 가이드" | 하위 프로젝트 3 |
| `RecommendationSignal` 소비 랭킹 | 하위 프로젝트 4 |
| Codex [예약된 작업] 등록 | Task 10이 실수집으로 확인된 뒤 |
| Flyway 도입 | 파이프라인 v1에서 기각(변하는 부분을 JSONB로) |
| 축제 3건 물리 삭제 | 사용자 결정 — `EVENT` 격리로 충분, 필요해지면 언제든 |
