# 레지스트리 기반 코스 추천 — 설계

- 날짜: 2026-08-06
- 브랜치: `feat/travel-knowledge-registry` (main에서 분기)
- 선행 자산: 여행 지식 파이프라인 v1 (`app/backend/src/main/java/com/guidematch/knowledge/`, `docs/ingest/`, `scripts/ingest/`) — 전부 미커밋 상태로 이 브랜치에 함께 올린다
- 관련 계획서: `~/.claude/plans/eager-tumbling-micali.md` (파이프라인 v1 원계획)

---

## 1. 무엇을 고치는가

`GET /api/courses/recommend`는 Kakao 실시간 검색 결과를 최근접 이웃으로 이어 정차지를 만든다.
지식 파이프라인은 그 위에 "왜 거기가 좋은지"를 덧붙이라고 만들었는데, **지금 단 한 건도 붙지 않는다.**

2026-08-05 실측:

| 사실 | 수치 |
|---|---|
| `places` | 53건 (kakao 40 + tour_api 13) |
| `place_insights` | 9건 (전부 tour_api 장소에 붙음) |
| 인사이트 붙은 장소 중 `kakao_place_id` 보유 | **0 / 9** |
| `place_aliases` | 0건 |

`PlaceInsightLookup.byKakaoPlaceIds()`가 `kakao_place_id`로만 조회하는데 인사이트를 가진
13건은 전부 `kakao_place_id = null`이다. 두 집합이 완전히 분리돼 있어 **서버를 띄우지 않아도
조인이 영원히 실패한다는 것이 확정된다.**

그런데 병합을 완벽히 해도 문제가 남는다. 정차지는 언제나 Kakao 검색이 정하므로 지식베이스는
**Kakao가 반환한 것만 주석**할 수 있다. 경복궁·간송미술관처럼 TourAPI에만 있는 문화재·전시관은
영원히 정차지가 못 된다 — TourAPI를 쓰는 이유 그 자체가 소비 경로에서 배제된다.

## 2. 결정: 레지스트리가 정차지를 정한다

**정차지는 우리 `places` 레지스트리에서 뽑는다. Kakao는 여러 시더 중 하나로 강등되고,
레지스트리가 얇은 범위에서는 부족분을 채우는 폴백으로만 쓴다.**

| | 채택안 (레지스트리 주도) | 기각안 (Kakao 주도) |
|---|---|---|
| 정차지 출처 | `places` 최근접 이웃 | Kakao 실시간 검색 |
| 인사이트 커버리지 상한 | 레지스트리 크기 = 우리가 통제 | Kakao 검색 결과 = 영구 상한 |
| TourAPI 고유 장소 | 정차지가 될 수 있다 | 영원히 불가 |
| 얇은 범위 | Kakao 폴백 | 해당 없음 |
| 비용 | `toStops` 재작성, 정차지 출처 이중화 | 병합만 하면 됨 |

기각안은 작업량이 작지만 인사이트 커버리지 상한이 Kakao에 영구히 갇힌다.
지식 DB를 만드는 목적 자체가 그 상한을 넘는 것이므로 채택하지 않는다.

**얇은 범위 정책**: 레지스트리에서 먼저 뽑고, 슬롯이 모자라면 Kakao 실시간 검색으로 채운다.
지금 UX가 안 깨지고, 수집이 늘어날수록 Kakao 의존이 자연히 줄어든다.
비용은 정차지 출처가 둘이라는 것 — 중복제거와 출처 표시가 필요하다.

---

## 3. 스키마 변경 2개

`places`에 nullable 컬럼 2개를 추가한다. 새 *테이블*이 아니므로 `db-role.sql`의 GRANT
재실행은 불필요하다(GRANT는 테이블 단위라 컬럼을 자동으로 덮는다).
단 ingest 프로파일은 `ddl-auto: none`이므로 **개발 앱을 한 번 먼저 띄워 컬럼을 만들어야 한다.**

### 3.1 `place_kind`

```
ATTRACTION | CULTURE | FOOD | CAFE | MARKET | SHOP | LODGING | OTHER
```

이중 역할이다.

1. **코스 슬롯 매칭** — `THEME_SLOTS`의 facet(`attraction`/`food`/`cafe`/`culture`/`market`)에
   그대로 대응한다. 이게 없으면 레지스트리에서 "카페 슬롯에 넣을 장소"를 고를 수단이 없다.
2. **여행자 무관 장소 배제** — 중구 40건에 우리은행·동국대·한국금융사가 섞여 있다.
   `SHOP`/`LODGING`/`OTHER`는 정차지 후보에서 뺀다.

**판정은 적재 시점에 `category_raw`로 결정론적으로 한다.** LLM 판단을 쓰지 않는다.

| 입력 | 판정 |
|---|---|
| Kakao `여행 > 관광,명소` | `ATTRACTION` |
| Kakao `문화,예술 > …` | `CULTURE` |
| Kakao `음식점 > 카페 > …` | `CAFE` |
| Kakao `음식점 > …` | `FOOD` |
| Kakao `…시장…` | `MARKET` |
| Kakao `금융,보험 > 은행`, `교육,학문 > 학교` | `OTHER` |
| TourAPI `contentTypeId=12`(관광지) | `ATTRACTION` |
| TourAPI `14`(문화시설) | `CULTURE` |
| TourAPI `39`(음식점) | `FOOD` |
| TourAPI `38`(쇼핑) | `SHOP`, 단 이름에 `시장` 포함 시 `MARKET` |
| TourAPI `32`(숙박) | `LODGING` |
| 그 외 · 판정 불가 | `OTHER` |

애매하면 `OTHER`다. 잘못 분류된 장소가 정차지로 나가는 것보다 안 나가는 게 낫다.

**`OTHER`도 레지스트리에는 남긴다.** 지우면 다음 수집이 그대로 다시 넣는다.
정차지 후보에서만 빠진다.

### 3.2 `address_ko`

계약(`docs/ingest/schema/place.schema.json`)에는 `address_raw`가 이미 있는데
`PlaceClue`에 해당 필드가 없고 `IngestService.ingestPlace`가 읽지 않는다.
**1일차부터 조용히 버려져 왔다.**

이게 없으면 레지스트리 정차지만 `Stop.address`가 `null`이고 Kakao 폴백 정차지는 주소가 있어,
응답이 출처에 따라 들쭉날쭉해진다 — 프론트 버그처럼 보이는 데이터 결함이다.
CLAUDE.md 규약대로 **주소는 번역하지 않고 한국어 그대로 보관**한다(택시·지도 앱 편의).

`PlaceClue`에 `addressRaw` 추가 → `Place.addressKo` → `enrichMissing`이 비어 있을 때만 채운다.

### 3.3 ★ 기존 53행 백필

**이 절에서 가장 실패하기 쉬운 지점이다.**

`ddl-auto: update`는 컬럼을 nullable로 추가하므로 기존 53행의 `place_kind`가 전부 NULL이 된다.
레지스트리 조회가 `place_kind IN (...)`으로 거르면 **후보 0건 → Kakao 폴백이 100% 채움 →
엔드포인트는 정상 응답하고, 결과는 지금과 완전히 동일하며, "됐다"로 보인다.**

따라서 백필은 선택이 아니라 필수 단계다:

- 일회성 `ApplicationRunner`(`@Profile("!ingest")`)로 `place_kind IS NULL`인 행을
  3.1 매핑표로 채운다. **SQL로 하지 않는다** — 매핑 규칙이 두 곳에 생기면 반드시 어긋나고,
  적재 경로와 백필이 다르게 분류하는 순간 원인 찾기가 불가능해진다.
  `@Profile("!ingest")`는 `UserFollowBackfill`에서 확립한 규칙이다(적재 배치가 앱
  마이그레이션을 돌리면 안 되고, 적재 롤에는 권한도 없다)
- `address_ko`는 백필하지 않는다 — 원본 주소가 DB에 없다. 재수집 때 채워진다
- 완료 검증은 "응답에 정차지가 있다"가 아니라 **`source="registry"` 정차지 ≥ 1**이다

---

## 4. 적재 신뢰성

### 4.1 왕복 축소 — 58초를 5초로

측정된 사실: `time ./bin/ingest.sh` = **58.1초**. `codex exec` 아래에서 실행하면 끝나기 전에
종료돼 JVM이 쓰기 도중 죽는다. 유실은 항상 파일 뒷부분이고 `exit 0`으로 보고된다.

- tour_api 런: 인사이트 9건 중 **뒤 3건** 유실
- kakao 재시딩 런: 장소 40건 중 **뒤 20건** 유실
- 두 로그 모두 정확히 25줄, 요약줄(`적재 완료 …`) 없음
- codex 밖에서 같은 `run_id`로 돌리면 매번 완주

원인은 왕복이다. 장소 1건당:

```
findByKakaoPlaceId · findByTourApiContentId · findByNameNormalized
· findByAliasNormalized · placeRepo.save
· existsByPlaceIdAndAliasNormalized(별칭당) · aliasRepo.save(별칭당)
· sourceRepo.findByUrlHash · sourceRepo.save
```

≈ 8회 × 250ms(Supabase 시드니) ≈ 2초. 40건이면 80초 — 실측 58초와 맞는다.

**대책 두 겹:**

1. **범위 스냅샷** — 적재 시작 시 해당 `scope_key`의 `places`·`place_aliases`를
   1~2 쿼리로 메모리에 올린다. 구 단위 수백 행이라 메모리 부담이 없다.
   `PlaceResolver`가 이 스냅샷을 먼저 보고, 없을 때만 DB를 친다.
   조회 왕복이 장소당 4회 → 0회에 수렴한다.
2. **쓰기 배치** — `hibernate.jdbc.batch_size`를 켜고 `saveAll`로 모은다.

**설계 제약**: 스냅샷은 `PlaceResolver`의 해결 사다리 의미를 바꾸면 안 된다.
1단(외부 ID) → 2단(이름+반경) → 3단(미해결) 순서와 ambiguous 거절 규칙이 동일해야 하고,
같은 실행 안에서 새로 만든 장소는 스냅샷에도 즉시 반영돼야 한다(그래야 같은 파일 안의
중복 장소가 두 노드로 쪼개지지 않는다).

### 4.2 중단 감지

**마커 파일도 exit code도 쓸 수 없다.** 샌드박스가 셸째로 죽으므로 둘 다 안 남는다.
실제로 `_rejects.jsonl` 0바이트 · `exit 0` · warn 한 줄 없음으로 유실이 완전히 은폐됐다.

대신 **`IngestRun.status`를 쓴다.** 죽은 런은 DB에 `STARTED`로 영원히 남는다 —
샌드박스 teardown을 견디는 유일한 신호다.

- 적재 시작 시 오래된 `STARTED` 런을 조회해 로그·표준출력에 경고
- 커서 익스포터(`IngestStateExporter`)가 `state/`에 미완료 런을 노출해
  다음 Codex 세션이 그것을 보고 재적재하게 한다(재적재는 멱등이다)

### 4.3 줄 예외 스왈로

`IngestService.eachLine`이 줄 단위 예외를 `log.warn`만 하고 rejects 카운터도 파일도
건드리지 않는다. **이번 유실의 원인은 아니었지만**(그랬다면 warn이 남고 프로세스는 완주했어야 한다)
같은 은폐를 만드는 잠재 결함이다. `reject()` 호출로 교체한다.

---

## 5. 계약 3조항 + 프롬프트 v4

### 5.1 축제를 장소로 만들지 마라

인사이트 9건 중 6건이 `가을,명동으로`·`게임문화축제`·`겨울,청계천의빛` —
내년엔 없어질 이벤트다. `best_season`이 "행사가 열리는 시기는 11월"로 들어가 있는데
이건 장소 속성이 아니라 개최일이다.

- `CONTRACT.md`에 조항 신설: TourAPI `contentTypeId=15`(축제·공연·행사)는 `places`에 넣지 않는다
- 적재 가드: 해당 단서가 오면 `_rejects.jsonl`에 사유와 함께 남긴다
- 기존 6건(장소 + 붙은 인사이트) 정리. 적재 롤에 DELETE 권한이 없으므로 `postgres` 롤로 한다

### 5.2 `place_kind` 판정 규칙을 계약에 명시

3.1 매핑표를 `CONTRACT.md`에 싣고, 추출기는 `category_raw`를 **원문 그대로** 실어 보내기만 한다.
판정은 우리 코드가 한다 — 매칭·분류 키를 외부 에이전트가 정하게 두면 프롬프트가 바뀌는
순간 같은 장소가 다른 키로 들어온다(`name_normalized`에서 이미 확립한 원칙).

### 5.3 부분 수집을 완료로 기록하지 마라

`scope-progress.jsonl`에 `Seoul/중구 · tour_api · urls=19`가 완료로 기록됐는데
실제로는 `areaBasedList2` 기본 정렬(제목순) 1페이지 13건이 전부다. 가~금까지만 훑었고
overview 1264자가 확인된 **경복궁은 수집되지도 않았다.** `refresh_after_days: 90`이라
90일간 재방문하지 않는다.

- 현행 계약은 "0건이면 완료로 기록 말라"만 있다. **부분 수집**을 막는 조항이 없다
- 커서 항목에 페이지 소진 여부를 싣고, 소진하지 못했으면 완료로 기록하지 않는다
- 지금 중구 줄을 무효화한다

### 5.4 프롬프트 `insight-v3` → `insight-v4`

9건 전부 evidence가 `detailIntro2`이고 `detailCommon2`(overview) 유래가 **0건**이다.
`note_i18n.ko`가 `"입장료 무료 행사"`로 **5회 바이트 동일**하고, 한옥(관훈동 민씨 가옥)에
`"행사 운영 시간"`이 붙었다 — 프로즈 독해가 아니라 필드 템플릿이다.
13곳을 6초에 처리했고 vibe·photo_spot·caution은 하나도 안 나왔다.

v4에 추가하는 두 줄:

- `detailCommon2`의 `overview`를 **반드시 읽고** 거기서 `VIBE`/`PHOTO_SPOT`/`BEST_TIME`을 뽑는다
- 필드 값을 문장 틀에 끼워 넣지 마라. 같은 문구가 여러 장소에 반복되면 그건 사실이 아니다

버전을 올리는 이유는 기존 9건이 v2로 남아 추적 가능해야 하기 때문이다.
(v3는 이름·별칭 분할 금지 규칙을 추가한 버전으로, `kakao_local` 재시딩에만 쓰였다 —
kakao는 설명 텍스트를 안 주므로 **v3로 만들어진 인사이트는 0건**이고 추출 규칙은 미검증이다.)

**범위 밖**: 블로그·리뷰 LLM 추출로 vibe를 제대로 만드는 일은 하위 프로젝트 2다.
여기서는 이미 approved인 TourAPI overview를 읽게 하는 데까지만 한다.

---

## 6. 병합 — tour_api ↔ kakao

### 6.1 역방향 시딩

`areaBasedList2` 페이징으로 커버리지를 늘리면 알파벳 표본 문제가 반복되고
Kakao 집합과 겹칠 보장이 없다. 대신 **이미 레지스트리에 있는 Kakao 장소명으로
TourAPI `searchKeyword2`를 역조회**한다. 겹침이 구조적으로 보장된다.

실측(sources.yml 미등록 엔드포인트로 직접 확인):

| 질의 | TourAPI 적중 | 결과 |
|---|---|---|
| 남대문시장 | 1건 | 제목 정확 일치 → 200m 안이면 즉시 병합 |
| 숭례문 | 2건 | 동일 |
| 남산케이블카 | 1건 | 동일 |
| 덕수궁 | 2건 | `덕수궁 대한문`·`덕수궁 돌담길`만 → 6.2에 걸림 |
| 한국은행 화폐박물관 | 0건 | 미적중 |

- `sources.yml`의 `tour_api.api.endpoints`에 `searchKeyword2` 추가
- 프롬프트에 역방향 시딩 절차 추가: 대상 범위의 기존 Kakao 장소명 목록을 질의어로 쓴다

### 6.2 이름 정규화 — 함수는 바꾸지 않고 2차 조회를 넣는다

`PlaceNames.normalize()`는 비영숫자만 제거한 완전일치다.
`간송미술관(서울 보화각)` → `간송미술관서울보화각` ≠ `간송미술관`.

**`normalize()` 자체는 바꾸지 않는다.** 두 가지 이유가 있다.

1. `name_normalized`는 **저장된 컬럼**이다. `findByNameNormalized()`가 갓 계산한 키를
   저장된 값과 대조하므로, 함수를 바꾸면 기존 53행의 키가 전부 어긋나 오히려 매칭이 나빠진다.
2. 괄호절을 접으면 `스타벅스(명동점)`과 `스타벅스(을지로점)`이 같은 키가 된다.
   200m 안에 하나만 있으면 안전한 ambiguous 거절 대신 **오병합**이 난다.
   오병합은 되돌릴 수 없고 실패가 조용하다.

대신 `PlaceResolver.candidatesByName()`에 **2차 조회**를 넣는다:

```
1차: normalize(nameRaw)            로 조회 → 있으면 그것으로 진행
2차: 1차가 비면, 괄호절을 제거한 키로 한 번 더 조회
```

- 200m 반경 검증과 ambiguous 거절 규칙은 **동일하게** 적용된다
- 저장 컬럼에는 언제나 정확 키를 넣는다 — 완화 키는 조회에만 쓴다
- 매칭을 늘리기만 하고 기존 구분을 없애지 않는다

지점명 접미사(`금강제화 명동본점` vs `금강제화`)는 손대지 않는다. 서로 다른 장소일 수 있고
결정론적 규칙을 세울 수 없다. 별칭이 쌓이면서 자연히 해결될 문제로 남긴다.

### 6.3 검증

`SELECT count(*) FROM places WHERE kakao_place_id IS NOT NULL AND tour_api_content_id IS NOT NULL`
이 0보다 커야 한다. 지금은 0이다.

---

## 7. 소비 경로

### 7.1 구성

```
GET /api/courses/recommend
        │
        ▼
  CoursePlanner  (신규, geo 패키지)
        │
        ├─ 1) 레지스트리 후보 조회
        │     PlaceRepository.findCandidates(city, district, kinds)
        │     → place_kind 로 슬롯 매칭, OTHER/SHOP/LODGING 제외
        │
        ├─ 2) greedy nearest-neighbor  (기존 알고리즘 그대로)
        │     구 단위 수백 행이라 인메모리 계산. PostGIS 불필요
        │
        ├─ 3) 슬롯이 모자라면 Kakao 폴백
        │     KakaoLocalClient.searchByCategory / searchByKeyword
        │
        └─ 4) 중복 제거 3단
              kakao_place_id 일치 → 정규화 이름 일치 → 100m 이내
        │
        ▼
  toStops — 인사이트 부착
        레지스트리 정차지: PlaceInsightLookup.byPlaceIds()   (신규)
        Kakao 폴백 정차지: PlaceInsightLookup.byKakaoPlaceIds() (기존 유지)
```

### 7.2 인터페이스

**`PlaceInsightLookup.byPlaceIds(Collection<Long>, String lang)`** 신규.
기존 `byKakaoPlaceIds`가 이미 "장소 조회 → 인사이트 조회" 2단계인데, 앞 단계가 불필요해질 뿐이다.
쿼리 1회로 끝난다. 기존 메서드는 폴백 정차지용으로 남긴다.
**루프 안 단건 조회 금지 원칙은 그대로다** — 배치 조회만 노출한다.

**`PlaceRepository.findCandidates(city, district, Collection<PlaceKind>)`** 신규.
`idx_places_city_district`가 이미 있다.

**`Stop`에 `source` 필드 추가** (`"registry"` / `"kakao"`).
이게 없으면 레지스트리가 실제로 일하는지 밖에서 확인할 방법이 없다 — 3.3의 조용한 실패를
잡아내는 유일한 관측 지점이다. 프론트는 무시해도 되지만 응답에는 반드시 실린다.

### 7.3 기존 동작 유지

- Kakao 키가 없으면 `kakaoEnabled=false` — 단, 레지스트리는 여전히 동작해야 한다
  (지금은 Kakao 없으면 무조건 빈 목록이다. 레지스트리 주도로 바뀌면 그럴 이유가 없다)
- `PICK_POOL=3` 무작위 선택("다시 추천")·거리 계산·`suggestedHours` 산식 그대로
- 장소명/카테고리 번역(`TranslationService`)·주소 한국어 유지 그대로
- `SignalRecorder.recordShown` 그대로. 단 레지스트리 정차지는 `place_id`를 직접 실을 수 있다

---

## 8. 테스트

이 리포에는 `@SpringBootTest`가 없다. 전부 목 기반 단위 테스트이고 이번에도 그 방식을 따른다.

| 대상 | 고정할 것 |
|---|---|
| `PlaceKind` 매핑 | 3.1 표의 각 행. 특히 은행·대학교 → `OTHER`, 판정 불가 → `OTHER` |
| 백필 | `place_kind IS NULL`인 행만 채우고 기존 값은 안 건드린다 |
| 범위 스냅샷 resolver | 사다리 순서·ambiguous 거절이 스냅샷 유무와 무관하게 동일. 같은 실행에서 만든 장소가 뒤 줄에 보인다 |
| 2차 조회 | `간송미술관(서울 보화각)` → `간송미술관` 매칭. `스타벅스(명동점)`/`스타벅스(을지로점)`은 **여전히 다른 키** |
| 축제 가드 | `contentTypeId=15` → place 생성 안 함 + rejects에 기록 |
| `eachLine` | 줄 예외가 rejects 카운터·파일에 남는다 |
| `CoursePlanner` | 레지스트리만으로 채워질 때 Kakao 미호출 / 모자랄 때만 폴백 / 중복 제거 3단 각각 |
| `Stop.source` | 레지스트리 유래는 `"registry"`, 폴백은 `"kakao"` |
| `byPlaceIds` | 쿼리 1회. 인사이트 신뢰도 내림차순. 언어 폴백(요청 언어 → ko → 아무거나) |

---

## 9. 완료조건 — 지금 데이터로는 증명 불가하다

인사이트 9건 중 6건이 5.1의 정리 대상이고, 남는 3건은 5.4가 지적한 같은 템플릿 런의 산물이다.
**따라서 3~7을 다 구현해도 "정차지에 진짜 설명이 붙는다"를 현재 행으로는 보일 수 없다.**

순서:

1. 3~6 구현 (스키마 + 백필 + 적재 신뢰성 + 계약 + 프롬프트 v4 + 병합)
2. **사용자가 `codex exec`로 `Seoul/중구` 재수집** — v4 프롬프트, 역방향 시딩 포함
   ```
   codex exec --cd ~/peerup-ingest --skip-git-repo-check \
     --sandbox workspace-write -c sandbox_workspace_write.network_access=true \
     < <프롬프트 파일>
   ```
   ⚠ `app/backend/src/main`을 고쳤으므로 `scripts/ingest/build-jar.sh` 재실행이 선행돼야 한다
   (안 하면 ingest.sh 최신성 가드가 exit 3으로 멈춘다)
3. 7 검증

**완료조건:**

> `GET /api/courses/recommend?city=Seoul&district=중구` 응답에서
> **`source="registry"` 정차지 ≥ 1** **AND**
> 그중 **`evidence.url`이 `detailCommon2`인 인사이트가 붙은 정차지 ≥ 1**

두 조건이 다 필요하다. 앞만 보면 3.3의 조용한 실패를 놓치고, 뒤만 보면 5.4의 템플릿 추출을 놓친다.

**중간 검증(사람 없이 가능):**

- `place_kind`가 NULL인 `places` 행 = 0
- `kakao_place_id`와 `tour_api_content_id`를 **둘 다** 가진 행 > 0
- `ingest.sh` 실행 시간 < 15초 (현재 58초)
- 재적재 후 행 수 불변 (멱등성 — 기존 검증 방식 유지)

---

## 10. 범위 밖

| 항목 | 어디로 |
|---|---|
| 블로그·리뷰 LLM 추출로 vibe·혼잡도 만들기 | 하위 프로젝트 2 |
| 여행자 화면·SEO·"이 코스 가능한 가이드" | 하위 프로젝트 3 |
| `RecommendationSignal` 소비 랭킹 | 하위 프로젝트 4 |
| Codex [예약된 작업] 등록 | 4.1이 실측으로 확인된 뒤 |
| Flyway 도입 | 파이프라인 v1에서 이미 기각(변하는 부분을 JSONB로) |
| 암호화 키 로테이션 | 결제 트랙 |

**Codex 예약 등록을 아직 하지 않는 이유**: 4.1을 고치기 전에 3시간마다 돌리면
매 실행이 조용히 뒷부분을 잃고, 5.1을 고치기 전이면 사라질 축제의 개최일이 계속 쌓인다.
