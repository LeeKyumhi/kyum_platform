# 수집 계약 (Codex ↔ 적재기)

> 이 문서가 형식의 **정본**이다. 프롬프트와 어긋나면 이 문서를 따른다.
> 스키마 버전: `1.0`

---

## 1. 이 계약이 존재하는 이유

Codex는 수집만 하고, 적재기는 판정만 한다. 둘 사이에 파일 계약을 두면 추출 방식이 바뀌어도
적재 코드가 안 바뀌고, 적재 로직이 바뀌어도 프롬프트를 안 고쳐도 된다.

**파일은 데이터베이스가 아니라 적재 큐다.** 진실의 원천은 Postgres다.

---

## 2. 형식 — JSON Lines

한 줄에 레코드 하나. 큰 JSON 배열이 아니다.

- **이어붙이기 가능** — 중단돼도 그 지점부터 재개. 배열은 닫는 괄호까지 써야 유효하다
- **부분 실패 격리** — 한 줄이 깨져도 나머지는 적재된다. 배열은 문법오류 하나로 전체 폐기
- **스트리밍 적재** — 파일 전체를 메모리에 안 올린다

⚠ **마지막에 몰아서 쓰지 말고 줄 단위로 append 하라.** 25분짜리 수집이 마지막에 죽으면
그때까지의 성과가 통째로 사라진다.

---

## 3. 디렉터리

```
~/peerup-ingest/
  contract/          ← 이 문서와 스키마 (읽기 전용)
  runs/<run_id>/
    manifest.json
    places.jsonl
    insights.jsonl
    _rejects.jsonl   ← 스키마 위반 줄 + 사유. 버리지 말 것
  staging/           ← 원문 캐시, 14일 후 삭제
  state/
    scope-progress.jsonl     ← (소스 × 범위)당 1줄. 다음 실행을 계획하는 커서
    ingested-sources.jsonl   ← URL당 1줄. 범위 안 중복 회피용
  lib/ingest.jar     ← 적재 배치 실행 파일 (build-jar.sh가 설치)
  tmp/               ← Tomcat 임시 디렉터리. 샌드박스 쓰기 범위 안에 못박은 것
  logs/
```

두 커서 파일 모두 매 적재 후 DB에서 다시 내보낸다 — 파일과 DB가 어긋나면 **DB가 이긴다.**
계획에는 `scope-progress.jsonl`만 쓴다. URL 파일은 커버리지가 늘수록 수천 줄이 되어,
계획에 쓰면 에이전트가 예산을 읽는 데 쓰거나 잘라 읽고 이미 한 범위를 다시 고른다.

```json
{"scope_key":"Seoul/중구","source_kind":"kakao_local","urls":40,
 "last_seen_run":"2026-07-31T05-37Z-kakao-seoul-junggu","last_seen_at":"2026-07-31T05:47:48Z"}
```

### `scope_key` 규칙 (적재기가 만드는 값 — 정확히 이대로 조회할 것)

```
district가 있으면  →  "<city>/<district>"     예: "Seoul/중구"
district가 없으면  →  "<city>"                예: "Jeju"
```

`city`는 `targets.yml`의 값을 **대소문자까지 그대로** 쓴다(`Seoul`이지 `seoul`이 아니다).
`district`가 `~`·`null`·빈 문자열이면 **구분자 `/`도 붙이지 않는다.**

⚠ 이 규칙이 어긋나면 조용히 망가진다. `Jeju/null`이나 `Jeju/`로 찾으면 제주·경주·강릉·
전주·대구·대전·광주·속초 8개 범위가 **영원히 "안 한 것"으로 남아** 매번 다시 수집되고,
그 사이 우선순위 낮은 범위는 차례가 오지 않는다. 실패가 눈에 띄지 않으므로 규칙을 지켜라.

`run_id` 형식: `YYYY-MM-DDTHH-mmZ-<source_kind>-<city>-<district>`
예: `2026-07-31T03-00Z-kakao-seoul-seongdong`

---

## 4. `manifest.json`

실행 1건의 신원. `prompt_version`이 있어야 "이 버전으로 뽑은 것만 재추출"이 가능하다.

```json
{
  "schema_version": "1.0",
  "run_id": "2026-07-31T03-00Z-kakao-seoul-seongdong",
  "started_at": "2026-07-31T03:00:00Z",
  "finished_at": "2026-07-31T03:24:11Z",
  "source":    { "kind": "kakao_local", "publisher": "Kakao", "terms_status": "approved" },
  "extractor": { "agent": "codex-cli", "model": "…", "prompt_version": "insight-v1" },
  "scope":     { "city": "seoul", "district": "성동구" },
  "counts":    { "places": 412, "insights": 1830, "rejects": 7 }
}
```

---

## 5. `places.jsonl` — 장소 **후보**

canonical 장소가 아니다. 관측된 단서일 뿐이다.

```json
{"record_id":"sha256:a3f…","record_type":"place","schema_version":"1.0",
 "name_raw":"어니언 성수",
 "aliases":["Onion Seongsu","어니언(성수점)"],
 "address_raw":"서울 성동구 아차산로9길 8","city":"seoul","district":"성동구",
 "lat":37.5445,"lng":127.0557,
 "external_ids":{"kakao_place_id":"1234567","tour_api_content_id":null},
 "category_raw":"음식점 > 카페",
 "source":{"url":"https://…","publisher":"…","fetched_at":"2026-07-31T03:12:00Z"},
 "confidence":0.92}
```

| 필드 | 필수 | 비고 |
|---|---|---|
| `record_id` | ✔ | 결정론적 해시 (§7) |
| `name_raw` | ✔ | **소스가 준 이름 문자열을 그대로 복사.** 쪼개지 마라 — 아래 ⚠ |
| `aliases` | | **별칭의 유일한 출처.** 여기 실린 표기가 다음 실행부터 매칭에 쓰인다 |

### ⚠ `name_raw`를 쪼개지 마라 — 2026-08-05에 실제로 레지스트리를 망가뜨렸다

Kakao 시딩 실행이 한글 복합명을 **형태소 단위로 잘라** 앞토막만 `name_raw`에 넣고
뒷토막을 `aliases`에 넣었다. 실측 대조:

| 저장된 것 | Kakao가 실제로 준 `place_name` |
|---|---|
| `name_raw:"한국은행"`, `aliases:["화폐박물관"]` | `한국은행 화폐박물관` |
| `name_raw:"서소문성지"`, `aliases:["역사박물관"]` | **`서소문성지역사박물관`** (공백 없음) |
| `name_raw:"남산골"`, `aliases:["한옥마을"]` | **`남산골한옥마을`** (공백 없음) |

아래 둘은 원본에 공백이 아예 없다. 즉 **어느 소스에도 없는 문자열을 만들어낸 것**이다.
피해가 두 겹이다:

1. `name_normalized`(해결 사다리 2단의 유일한 키)가 아무것과도 안 맞는 파편이 된다.
   40개 장소가 통째로 매칭 불능이 됐고, TourAPI 13건과 겹치는 이름이 **0쌍**이 나왔다.
2. `박물관`·`역사박물관` 같은 **일반 명사가 별칭으로 퍼진다.** 실제로 `박물관`이 서로 다른
   4개 장소에 붙었다. 별칭은 "돌수록 매칭이 좋아지는" 장치인데, 오염되면 반대로
   `ambiguous` 거절을 양산하고 최악의 경우 오병합을 만든다.

규칙:

- `name_raw`는 소스 응답의 이름 필드(`place_name` / `title`)를 **한 글자도 바꾸지 않고** 넣는다.
  토큰 분리·괄호 제거·접미사 정리 전부 금지다. 정규화는 적재기의 `PlaceNames.normalize`가 한다.
- `aliases`에는 **소스가 별도 필드로 제공한 다른 표기**만 넣는다.
  `name_raw`에서 잘라낸 조각은 별칭이 아니다. 확신이 없으면 `aliases`는 빈 배열로 둬라 —
  비어 있는 건 나중에 채울 수 있지만, 오염된 별칭은 어느 장소 것이었는지 복원이 안 된다.
| `lat` / `lng` | | 없으면 이름만으로는 해결 불가 → 미해결 처리된다 |
| `external_ids` | | **하나라도 있어야 새 장소 노드를 만들 수 있다** (§8) |
| `source.url` | | 커서 갱신용. 없으면 "이미 수집함" 판단이 안 된다 |

---

## 6. `insights.jsonl` — 사실 카드

```json
{"record_id":"sha256:b71…","record_type":"insight","schema_version":"1.0",
 "place_ref":{"name_raw":"어니언 성수","lat":37.5445,"lng":127.0557,
              "external_ids":{"kakao_place_id":"1234567"}},
 "fact_kind":"wait_time",
 "value":{"minutes":30,"when":"weekend_afternoon"},
 "note_i18n":{"ko":"주말 오후 대기 30분 내외"},
 "evidence":{"url":"https://…","publisher":"…","published_at":"2026-04-12"},
 "confidence":0.7,
 "extracted_by":{"agent":"codex-cli","prompt_version":"insight-v1","run_id":"…"}}
```

### `fact_kind` — 열거형. 새 값을 발명하지 마라

자유 텍스트로 두면 "대기시간"·"waitTime"·"wait_time"이 뒤섞여 **어떤 조회도 불가능**해지고,
자산이 아니라 메모장이 된다. 맞는 게 없으면 그 사실은 **버려라**.

| 키 | 뜻 | `value` 예시 |
|---|---|---|
| `best_season` | 가기 좋은 시기 | `{"months":[4],"why":"cherry_blossom"}` |
| `best_time` | 가기 좋은 시간대 | `{"from":"09:00","to":"11:00"}` |
| `typical_duration_min` | 평균 체류 시간 | `{"minutes":90}` |
| `wait_time` | 대기 | `{"minutes":30,"when":"weekend_afternoon"}` |
| `price` | 요금 | `{"adult":3000,"currency":"KRW"}` |
| `photo_spot` | 사진 포인트 | `{"where":"2층 창가"}` |
| `reservation_required` | 예약 필요 여부 | `{"required":true}` |
| `access` | 접근 방법 | `{"subway":"수인분당선 서울숲역 3번 출구"}` |
| `caution` | 주의사항 | `{"what":"월요일 휴무"}` |
| `crowd_level` | 혼잡도 | `{"level":"high","when":"weekend"}` |
| `vibe` | 분위기 | `{"tags":["quiet","industrial"]}` |
| `pairs_with` | 같이 가기 좋은 곳 | `{"place":"서울숲"}` |
| `english_menu` | 영어 메뉴 유무 | `{"available":true}` |
| `foreigner_friendly` | 외국인 편의 | `{"level":"high","why":"english_staff"}` |

마지막 둘은 다른 여행앱엔 없지만 **우리 사용자(방한 외국인)에겐 결정적**이다.

### 소스별 힌트 — TourAPI에서 어느 필드가 어느 `fact_kind`가 되나

`areaBasedList2`만 훑으면 장소 목록만 나오고 **인사이트는 0건**이다. 사실은 상세 엔드포인트에 있다.
장소마다 `contentid`로 아래를 한 번 더 부르고, 값이 실제로 있는 것만 사실로 만든다.

| 엔드포인트 · 필드 | `fact_kind` | 비고 |
|---|---|---|
| `detailIntro2` · `usetime` | `best_time` | 운영시간. 파싱 안 되면 버려라 |
| `detailIntro2` · `restdate` | `caution` | 휴무일 |
| `detailIntro2` · `parking` | `access` | 주차 |
| `detailIntro2` · 이용요금 | `price` | 통화는 `KRW` |
| `detailCommon2` · `overview` | `vibe` · `best_season` · `photo_spot` | **요약해서** 넣는다 |

#### 범위(scope) → TourAPI 지역코드 — **추측 금지**

`targets.yml`은 이름만 갖고 있고 TourAPI는 숫자 코드를 요구한다. **코드를 잘못 넣으면 오류가
아니라 빈 목록이 온다** — "그 동네에 장소가 없다"와 구분이 안 되는 조용한 0건이다.
아래 표는 `areaCode2`로 실제 조회해 확인한 값이다 (2026-08-05).

⚠ Jeju·Gyeongju·Gangneung·Sokcho·Jeonju는 **시도가 아니라 시군구다.** 이걸 `areaCode`
자리에 넣으면 빈 응답이 온다.

| `scope_key` | `areaCode` | `sigunguCode` |
|---|---|---|
| `Seoul/중구` | 1 | 24 |
| `Seoul/종로구` | 1 | 23 |
| `Seoul/마포구` | 1 | 13 |
| `Seoul/성동구` | 1 | 16 |
| `Seoul/강남구` | 1 | 1 |
| `Seoul/용산구` | 1 | 21 |
| `Seoul/서대문구` | 1 | 14 |
| `Seoul/송파구` | 1 | 18 |
| `Seoul/영등포구` | 1 | 20 |
| `Busan/해운대구` | 6 | 16 |
| `Busan/중구` | 6 | 15 |
| `Incheon/중구` | 2 | 10 |
| `Jeju` | 39 | (비움 — 섬 전체) |
| `Gyeongju` | 35 | 2 |
| `Gangneung` | 32 | 1 |
| `Sokcho` | 32 | 5 |
| `Jeonju` | 37 | 12 |
| `Daegu` | 4 | (비움) |
| `Daejeon` | 3 | (비움) |
| `Gwangju` | 5 | (비움) |

표에 없는 범위가 생기면 `areaCode2`로 조회해라 (파라미터 없으면 시도 목록,
`areaCode=N`을 주면 그 시도의 시군구 목록):
`GET KorService2/areaCode2?serviceKey=…&MobileOS=ETC&MobileApp=peerup&_type=json&numOfRows=40&pageNo=1[&areaCode=N]`

**`areaBasedList2`가 0건을 반환하면 그 범위를 "완료"로 기록하지 마라.** `_rejects.jsonl`에
`reason: empty_result`와 사용한 코드를 남겨라. 코드 오류와 진짜 빈 지역을 나중에 구분해야 한다.

#### 호출 규칙 — 실호출로 확인함 (2026-08-05). 여기서 벗어나면 400이 난다

| 규칙 | 근거 |
|---|---|
| `detailCommon2`에 **`contentTypeId`를 넣지 마라** | 넣으면 `INVALID_REQUEST_PARAMETER_ERROR(contentTypeId)` |
| `detailIntro2`에는 **`contentTypeId`가 필수다** | 타입별로 응답 필드가 다르다 |
| `defaultYN`·`overviewYN` 등 **YN 플래그는 KorService2에서 폐지** | 넣으면 `INVALID_REQUEST_PARAMETER_ERROR(defaultYN)` |
| 공통 필수: `MobileOS`·`MobileApp`·`_type=json` | |
| 없는 `contentId`면 `body.items`가 객체가 아니라 **빈 문자열 `""`** 로 온다 | 바로 `['item']` 하면 터진다. 반드시 방어할 것 |

**오류코드 해독** — 이 둘을 구분 못 하면 엉뚱한 데를 판다:

| 응답 | 뜻 | 대응 |
|---|---|---|
| `resultCode 30` / HTTP 403 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR` | 키 문제 | 키를 고쳐라 |
| `resultCode 10` / HTTP 400 `INVALID_REQUEST_PARAMETER_ERROR` | **키는 통과했다.** 파라미터 문제 | 키를 건드리지 마라 |

`overview`는 서술형 문단이다. **그대로 옮기면 계약 위반**이다(§8 원문 복사). 읽고 사실만
증류해 `value`에 구조화하고, `note_i18n`에는 120자 이하 요약만 남겨라.
근거가 약하면 `confidence`를 낮춰라 — 0.5 미만은 적재기가 버리고, **그래도 된다.**

`evidence.publisher`는 반드시 `"한국관광공사"`다. 출처표시가 이용 조건이고,
데이터에 안 실어두면 나중에 되찾을 방법이 없어 표시 자체가 불가능해진다.

**서비스키**는 `secrets/tour-api.key` 한 줄이다. 파일이 없거나 **`#`로 시작하지 않는 줄이
하나도 없으면** 아직 발급 전이라는 뜻이다 — **그 소스를 건너뛰고** `_rejects.jsonl`에
`reason: credential_missing`을 남겨라. 다른 소스로 대체하거나 추측으로 채우지 마라.

⚠ 이 파일은 `setup-workspace.sh`가 **주석만 든 자리표시자**로 만들어둔다. 존재한다고 해서
키가 있는 게 아니다. 주석 줄을 키로 착각해 보내면 401이 나고, 그건 "키가 틀렸다"로 보이지만
사실은 "키가 아직 없다"이다 — 두 상황의 대응이 완전히 다르므로 반드시 구분해서 기록하라.

**올바른 키의 모양** (2026-08-05에 실제로 둘 다 틀려서 반나절을 썼다):

- base64 88자, `A-Za-z0-9+/` 와 끝의 `==` 로만 이루어진다
- **`%`가 있으면 인코딩 키다** — data.go.kr이 주는 두 값 중 **Decoding** 쪽을 써라.
  인코딩 키를 쓰면 요청 시 한 번 더 인코딩돼(`%2F`→`%252F`) 30번 오류가 난다
- **`<` `>` 가 있으면 안내문의 꺾쇠를 같이 붙여넣은 것이다.** 이 경우 인증이 아니라
  **10번(파라미터) 오류**로 나와서 키를 의심하지 않게 만든다 — 실제로 그렇게 헤맸다

한 줄 점검: `head -1 secrets/tour-api.key | grep -qE '^[A-Za-z0-9+/]+={0,2}$' && echo OK`

### `note_i18n`

언어 코드 → 요약. **각 120자 이하.** 원문 문장을 그대로 옮기지 마라 — 요약이지 인용이 아니다.
한국어만 있어도 된다. 소비 측이 요청 언어 → `ko` → 아무 언어 순으로 폴백한다.

---

## 7. `record_id` — 결정론적 해시

같은 소스를 다시 돌리면 **같은 값**이 나와야 한다. 그래야 적재가 UPSERT가 되고, 재실행마다
중복이 쌓이지 않는다.

```
insight → sha256("insight|" + evidence.url + "|" + place_ref_key + "|" + fact_kind)
place   → sha256("place|"   + source.url   + "|" + name_normalized)
```

`place_ref_key`는 `external_ids.kakao_place_id`가 있으면 그 값, 없으면 `name_raw`.

**재추출 정책**: 해시에 `run_id`가 들어가지 않으므로, 같은 URL을 다시 추출하면 같은
`record_id`가 나와 **최신 값으로 덮어쓴다. 이력은 남기지 않는다.** 의도된 선택이다 — 블로그가
수정되면 최신 사실이 이기는 게 맞고, 이력을 남기면 모든 조회에 "키별 최신 1건"이 붙는다.

---

## 8. 추출기가 **하면 안 되는 것**

### 장소 병합 판정

이름·좌표·외부ID 같은 **단서만** 낸다. 어떤 장소가 같은 장소인지는 적재기의 해결 사다리가
정한다.

```
1단  외부 ID 일치                        → 확정
2단  이름(또는 별칭) 일치 AND 반경 이내   → 확정 + 별칭 기록
3단  그 외                               → 미해결 보관함
```

**잘못된 병합은 되돌릴 수 없다.** 두 장소의 인사이트가 한 노드에 섞이면 어느 사실이 어느
장소 것이었는지 복원할 방법이 없다. 반대로 미해결은 원본을 보관하므로 나중에 언제든 해결된다.

또한 **외부 ID가 없는 소스는 새 장소 노드를 만들지 못한다.** 이름만 있는 블로그가 노드를
만들 수 있게 두면 같은 장소가 표기마다 쪼개져 자산이 영원히 복리가 안 된다.

### 추측

모르면 `null`. 빈 문자열이나 그럴듯한 값으로 채우지 마라.

### 원문 복사

`note_i18n`은 요약이다. 문단을 그대로 옮기면 저작권 문제가 되고, 그러면 이 파이프라인 전체를
못 쓰게 된다.

---

## 9. 적재기가 거절하는 것

| 조건 | 처리 |
|---|---|
| `record_id` 없음 | `_rejects.jsonl` — 멱등성 보장 불가 |
| 모르는 `fact_kind` | `_rejects.jsonl` |
| `confidence < 0.5` | 조용히 건너뜀 (거절 아님) |
| `place_ref` 해결 실패 | 미해결 보관함 — 버리지 않는다 |
| JSON 파싱 실패 | 로그 경고 후 다음 줄 진행 |

`confidence`는 **정직하게** 매겨라. 낮게 매겨서 버려지는 편이, 틀린 사실이 자산에 들어가
이후 모든 추천을 오염시키는 것보다 훨씬 낫다.
