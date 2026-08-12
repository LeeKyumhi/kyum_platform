# 장소 사진·정보 축적 — 설계

- 날짜: 2026-08-11
- 상태: **설계 승인 완료 · 구현 전**
- 선행: `2026-08-10-course-planner-flywheel-design.md` (1사이클 구현 완료, 미커밋)
- 이미 반영된 것: `net.coobird:thumbnailator:0.4.20` 의존성 추가 (`app/backend/build.gradle:46`,
  `gradle dependencies` 해소 확인 · `compileJava` BUILD SUCCESSFUL)

---

## 목표

장소에 사진과 정보가 **쌓이고**, 그게 사용자에게 보이게 한다. 네이버 플레이스에서 사진과
리뷰가 축적되어 참고 자산이 되는 것과 같은 구조.

**승인된 소싱 모델: 혼합** — 공공 데이터로 시드를 깔고, 그 위에 사용자 기여가 쌓인다.

| | 담당 구역 | 이유 |
|---|---|---|
| 시드 (TourAPI) | 관광지 | 첫날부터 화면이 비지 않는다 |
| UGC (사진+한줄팁) | 음식점·카페 | TourAPI가 구조적으로 못 덮는 곳 |

서로의 빈칸을 정확히 보완한다. 이것이 A안(양쪽을 1사이클에)을 고른 유일한 이유다 —
어느 한쪽만 하면 화면의 절반이 계속 비어 있다.

**기여 자격**: 로그인 사용자 누구나 (여행자+가이드). 사후 신고 대응으로 시작한다.

---

## 착수 전 사실 확인 (코드 실측)

**① 파이프라인은 이미 `detailCommon2`를 호출한다.** `sources.yml:43`이 overview를 뽑으려고
부르고 있고, `firstimage`는 **같은 응답 안에 있다.** API 호출 추가 0, 비용 추가 0.
계약(`place.schema.json`)이 그 필드를 안 받고 있을 뿐이다.

→ `address_raw` 때와 같은 구조다. 다만 **그때와 다르게 기존 run 디렉터리 JSONL에도 없다** —
필드만 추가하면 되는 게 아니라 **재수집이 필요하다.**

**② `SignalRecorder`는 이중 키를 쓰지 않는다.** (브레인스토밍 중 정정된 오해)

```java
// SignalRecorder.java:67
s.placeId() != null ? s.placeId() : resolved.get(s.kakaoPlaceId())
```

kakao id를 `place_id`로 **해소하고 버린다.** 해소 실패면 `place_id=null`로 남는다.
통계용이라 신원을 잃어도 되지만 — **노트는 신원을 잃으면 영영 못 찾는다.**
따라서 이 패턴을 노트에 그대로 쓰면 안 된다. 아래 §1이 이 문제를 다룬다.

**③ 재사용 가능한 자산** (새로 만들 인프라 0)

| 자산 | 용도 |
|---|---|
| `SupabaseStorageClient.uploadPublic()` | 업로드 — `GuidePostService:55-58` 패턴 |
| `Report` (`targetType`이 **String**, 검증은 서비스) | 신고 — DB 마이그레이션 불필요 |
| `PlaceInsightLookup` / `PlaceController.counts()` | 배치 조회 + 실패 격리 패턴 |
| `PlaceKindBackfill` | 백필 러너 구조 (`TransactionTemplate`·예외 삼킴) |
| 전역 `max-file-size: 10MB` | 업로드 상한 (`application.yml:11`) |

**④ `GET /api/places`는 permitAll** (`SecurityConfig.java:79`). 단 **정확히 일치**라
새 경로는 명시 등록이 필요하다.

---

## §1. 데이터 모델 — 장소를 무엇으로 식별할 것인가

이 기능의 진짜 난제는 업로드가 아니라 **사진을 어디에 붙일 것인가**다.
화면의 장소는 두 출신이 섞여 있고, 화면이 손에 쥔 신분증이 서로 다르다.

| 출신 | 아는 식별자 | 규모 |
|---|---|---|
| 레지스트리 | `places.id` | 53건. `kakao_place_id`가 **비어 있는 것도 있다**(예: "개화", placeId=44) |
| Kakao 실시간 검색 | `kakao_place_id`만 | `/explore`·팔레트 대부분 |

**결정: 두 키를 다 저장한다. 해소해서 하나로 접지 않는다.**

접었을 때 무너지는 지점이 명확하다 — 레지스트리에 없는 장소(대다수)에 올린 사진은
`place_id=null`이 되어 **어느 장소 사진인지 영영 알 수 없다.**

```
place_notes
  id
  place_id             FK→places, nullable    ← 레지스트리 유래면 채움
  kakao_place_id       varchar,   nullable    ← Kakao 유래면 채움 (버리지 않는다)
  place_name_snapshot  varchar,   not null    ← 둘 다 못 믿을 때의 최후 표시용
  user_id              FK→users,  not null
  photo_url            text,      nullable    ← 1600px (_full)
  photo_thumb_url      text,      nullable    ← 400px  (_thumb). photo_url과 항상 함께 채워진다
  tip                  varchar(140), nullable
       ※ 제약: photo_url 또는 tip 중 최소 하나 (서비스에서 검증)
  status               varchar(20) not null   VISIBLE / HIDDEN
  created_at           timestamptz not null
```

**규칙 3개**

1. **식별자가 하나도 없으면 저장을 거부한다.** `SignalRecorder:96`이 빈 행을 안 만드는 것과
   같은 판단 — 나중에 어떤 장소인지 알 수 없는 사진은 자산이 아니라 쓰레기다.
2. **조회는 두 키의 합집합.** 갈라져 쌓인 것을 **읽는 시점에** 합친다 (§4).
3. **나중에 흡수된다.** Kakao 장소가 뒤늦게 레지스트리에 수집되면 `kakao_place_id`로 매칭해
   `place_id`를 채우는 백필을 돌린다. 그동안 쌓인 노트가 그대로 딸려 온다.

**⚠ `ddl-auto` 함정**: `status`를 Java enum으로 두면 Hibernate가 CHECK 제약을 **생성 시점의
값으로 고정하고 나중에 안 고쳐준다** (레지스트리 사이클에서 실측됨 — `places_place_kind_check`).
그래서 `Report.targetType`과 같이 **String으로 저장하고 검증은 서비스에서** 한다.
새 테이블이므로 additive이고 `ddl-auto: update`로 안전하다.

---

## §2. 시드 — 공공 사진

**바꿀 곳 3군데**

| 위치 | 변경 |
|---|---|
| `docs/ingest/schema/place.schema.json` | `image_url`, `image_source_url` (둘 다 nullable) |
| `docs/ingest/codex-ingest-prompt.md` | insight-v4 → **v5**: `detailCommon2`의 `firstimage`를 `image_url`에 실어라 |
| `PlaceClue` + `Place` | `imageUrl` · `imagePublisher` 필드 / `places.image_url` · `places.image_publisher` 컬럼 (전부 nullable, additive) |

`image_publisher`를 **같이** 저장하는 이유: 출처를 못 밝히는 사진은 띄우지 않기로 했는데,
`image_url`만 저장하면 표시 시점에 발행처를 되찾을 방법이 없다. 인사이트가 `evidence.publisher`를
데이터에 실어 나르는 것과 같은 이유다 — `sources.yml`의 주석대로 "나중에 출처를 되찾을 방법이
없으면 표시할 수도 없다".

**출처 표시는 선택이 아니라 의무다.** `sources.yml`의 `attribution_required: true`이고
한국관광공사 사진은 공공누리 조건부다. 인사이트 🏛 배지와 **같은 규칙**을 적용한다 —
**출처를 달 수 없으면 그 사진은 띄우지 않는다.** `CourseReasons`가 `publisher` 없는 인사이트를
버리는 것과 동일한 판단이다.

**한계(명시)**: TourAPI 사진은 관광지 위주다. 음식점·카페에는 시드가 거의 안 붙는다.
거기는 UGC가 메우는 구역이고, 그것이 혼합 모델의 설계 의도다.

**⚠ 의존**: 시드가 실제로 채워지려면 **v5 재수집 실행이 필요하고 그건 사용자 작업이다**
(HANDOFF의 v4 재수집 항목에 v5로 얹힌다). §3·§4(UGC)는 이것과 **무관하게** 완성·검증된다.

---

## §3. 쓰기 경로

**엔드포인트 2개**

```
POST   /api/places/notes        인증 필요, multipart
DELETE /api/places/notes/{id}   인증 필요, 작성자 본인만
```

`POST`는 permitAll 규칙(GET 한정)에 안 걸려 자동으로 authenticated다.

**요청**

| 필드 | 타입 | 규칙 |
|---|---|---|
| `placeId` | Long? | ┐ 최소 하나 필수 |
| `kakaoPlaceId` | String? | ┘ |
| `placeName` | String | 필수 — 스냅샷 |
| `photo` | file? | ┐ 최소 하나 필수 |
| `tip` | String? | ┘ trim 후 140자 |

**검증 (서비스 계층 한 곳)**

1. 식별자 0개 → 400 · 사진·팁 둘 다 없음 → 400
2. `tip` 140자 초과 → 400
3. content-type 화이트리스트 `image/jpeg | image/png` → 아니면 400
4. **한 사용자가 한 장소에 최대 3개** — 한 명이 한 장소를 도배하는 걸 막는 최소 장치.
   이게 없으면 첫 스팸에 화면이 무너진다.
   **세는 기준은 §4의 합집합과 같다** — 같은 장소를 `place_id`로 한 번, `kakao_place_id`로 한 번
   올려 상한을 우회하는 걸 막아야 한다. 키 하나만 세면 실질 상한이 6개가 된다.

**이미지 전처리** (저장 직전, Thumbnailator)

```
받은 파일
  → EXIF Orientation 읽어 회전 적용
  → 긴 변 1600px 축소(_full) + 400px 축소(_thumb)   ※ 원본이 더 작으면 그대로
  → JPEG 품질 0.82로 재인코딩  ← 이 시점에 EXIF·GPS·기기정보 소멸
  → Supabase 업로드 2개
```

효과 3가지:

1. **위치·기기정보 유출 차단** — 사용자가 자기 촬영 이력을 모르고 공개하는 일이 없다.
2. **아이폰 사진이 눕지 않는다.** ImageIO 재인코딩만 하면 EXIF가 통째로 사라지면서
   Orientation 태그까지 잃어 사진이 옆으로 눕는다. 라이브러리를 도입한 이유가 이것이다.
3. **용량**: 폰 원본 4~8MB → 합쳐 300KB 수준. 목록 전송량이 10배 이상 줄어든다.

**두 크기를 저장하는 이유**: 목록에 1600px을 15장 깔면 3~6MB라 오히려 느려진다.
목록은 `_thumb`, 모달은 `_full`. Supabase 저장은 2배지만 전송량 이득이 압도적이다.

**WebP를 받지 않는 이유**: stock ImageIO에 WebP 리더가 없어 디코딩이 실패한다. 받으려면
디코더 의존성이 하나 더 필요한데, 폰 카메라 업로드는 사실상 전부 JPEG이고
(iOS Safari가 HEIC를 JPEG로 변환해 올린다) 지금 값어치가 없다.

**저장 경로**: 기존 public 버킷, `place-notes/{userId}/{uuid}_full.jpg` · `_thumb.jpg`.

**디코딩 실패 → 400.** 이것이 확장자만 바꾼 비이미지 파일의 실질적 관문이다 —
content-type 헤더는 클라이언트가 마음대로 보낼 수 있지만 **실제로 디코딩되는지는 속일 수 없다.**

**검수**: `Report.targetType`에 `"PLACE_NOTE"` 추가 (String이라 마이그레이션 불필요).
관리자 포털 숨김 → `status="HIDDEN"`. 작성자 본인 삭제는 별도 엔드포인트.

**삭제·숨김은 DB 행만 건드리고 Storage 객체는 지우지 않는다.** 이유: 삭제 실패가 요청을
깨뜨리면 안 되는데 Storage 삭제는 네트워크 왕복이라 실패할 수 있고, 고아 객체는 공개 URL을
아는 사람만 볼 수 있어 피해가 제한적이다. **다만 이건 부채다** — 신고당한 사진의 URL이 유출된
경우 파일 자체는 남는다. 정리 러너는 범위 밖(아래)에 명시한다.

---

## §4. 읽기 경로

`PlaceController.recommendFirst()`의 확립된 패턴에 그대로 얹는다 — **장소 수와 무관하게
고정 횟수 배치**, 각 조회는 실패해도 목록이 나가도록 격리(`PlaceController:164`, `counts()`).

**`PlaceMediaLookup`** (`PlaceInsightLookup`과 같은 모양)

| 경로 | 무엇 | 쿼리 |
|---|---|---|
| 목록 `/api/places` | 장소별 대표 사진 1장 + 사진 개수 | 배치 1회 (기존 5 → 6) |
| 상세 (모달 열 때) | 그 장소의 전체 사진 + 팁 | 1회 |

**§1의 이중 키를 합치는 곳이 여기다.** 목록은 kakao id만 쥐고 있으므로:

```sql
from place_notes n left join places p on p.id = n.place_id
where n.status = 'VISIBLE'
  and (n.kakao_place_id in :ids or p.kakao_place_id in :ids)
```

두 출신의 노트가 한 kakao id로 모인다. 조인이 하나 늘 뿐 **쿼리는 여전히 1회**다.

**모달 표시 순서**

```
[사진 스트립]   공식(🏛 한국관광공사) → UGC 최신순
[💡 수집된 정보] 기존 인사이트 (그대로)
[✍️ 여행자 팁]  최신 5개 + 더보기, 각 줄에 @핸들
```

**규칙 3개 — 플라이휠 스펙에서 그대로 승계**

1. **0은 표시하지 않는다.** 사진도 팁도 없으면 그 영역 자체를 렌더하지 않는다
   ("아직 사진이 없어요"를 띄우지 않는다).
2. **출처를 항상 밝힌다.** 공식은 "한국관광공사", UGC는 `User.getHandle()`.
3. **집계가 죽어도 화면은 나간다.** `counts()`와 같은 try/catch 격리.
   **비로그인 공개 경로라** 예외가 새면 탐색 화면이 통째로 깨진다.

**프론트**: `PlaceDetailModal`에 사진 스트립 + 팁 섹션 + 업로드 버튼, 목록 카드에 `_thumb`.
`i18n.ts`에 `placeNotes.*` ko/en/zh **전부**.

---

## §5. 테스트 (명시적 산출물)

**이 기능은 "안 보이는 것"이 두 가지 원인을 가진다** — 데이터가 없어서인지, 조립이 깨져서인지.
§4의 규칙1(0은 표시 안 함)이 정확히 그 둘을 같은 화면으로 만든다. 판별기는 테스트뿐이다.

| 무엇을 고정하나 | 없으면 |
|---|---|
| **이중 키 합류** — `place_id`로 올린 노트와 `kakao_place_id`로 올린 노트가 같은 장소 조회에서 함께 나온다 | §1 판단 전체의 유일한 판별기. 깨져도 화면엔 "사진이 좀 적네"로만 보인다 |
| **EXIF 제거 실증** — GPS를 심은 JPEG를 넣고 저장된 바이트에 EXIF가 **없음**을 단언 | 프라이버시 약속의 유일한 증거. 라이브러리를 믿는 것과 확인한 것은 다르다 |
| **Orientation 회전** — 회전 태그가 붙은 이미지가 실제로 회전돼 나온다 | 라이브러리를 도입한 이유 자체 |
| 식별자 0개 → 거부 · 사진·팁 둘 다 없음 → 거부 | 되찾을 수 없는 쓰레기 행 |
| 사용자당 장소별 3개 상한 | 첫 스팸에 화면이 무너진다 |
| 디코딩 실패 → 400 | 확장자만 바꾼 비이미지 파일의 실질 관문 |
| `HIDDEN` 노트는 조회에 안 나온다 | 신고·숨김의 효력 |
| **배치 1회** — 장소 수를 늘려도 `PlaceMediaLookup` 호출 1회 (Mockito verify + 인자 캡처) | 시드니 250ms 왕복. 루프 조회는 로컬에선 안 보인다 |
| 조회 실패해도 목록 200 | 비로그인 공개 경로 |
| 백필: `kakao_place_id` 매칭으로 `place_id`가 채워진다 | "나중에 흡수된다"는 약속의 판별기 |

실제 SQL 실행 횟수를 세는 하니스는 이 저장소에 없다(Hibernate `Statistics`·프록시 데이터소스
사용처 0). 만드는 것은 이 사이클 범위 밖이므로 루프 조회 회귀는 Mockito 수준에서 잡는다 —
플라이휠 사이클과 같은 판단이다.

**실 DB 스모크**: `scripts/smoke/`에 추가 — 업로드 → 조회에 등장 → 목록 썸네일 → 숨김 →
사라짐까지 왕복.

---

## 범위 밖 (명시)

- **사전 검수 큐** — 사후 신고 대응으로 시작. 볼륨 0인데 큐부터 만들면 안 쓰이는 화면만 는다.
- **노트 좋아요·정렬·신고 자동차단 임계값** — 데이터가 쌓인 뒤 판단.
- **리뷰(별점) 승격** — 사진+팁이 쌓인 뒤 별도 판단. 방문 검증 없는 별점은 조작에 취약하고
  구현 무게가 크다는 것이 브레인스토밍의 결론이었다.
- **WebP 입력** — §3.
- **고아 Storage 객체 정리 러너** — §3의 부채. 삭제·숨김된 노트의 파일을 실제로 지우는 배치.
  피해가 제한적이라 미루지만, 신고 대응의 완결성을 원하면 이게 다음 후보다.
- **외부 UGC 수집(블로그)** — `sources.yml`이 `naver_blog: terms_status: blocked`.
- **Google Places Details** — `IDEAS.md` 19번. Google 사진은 저장·캐시가 약관 위반이라
  "축적"이라는 이 설계의 목표와 애초에 맞지 않는다.

## 다음 사이클 스케치

- 노트가 쌓이면 **증거 사다리에 편입** — "여행자 N명이 사진을 올린 곳"이 🧳 가족의 자연스러운 강화.
- `place_notes`가 **다음 수집 우선순위 신호**가 된다. 사진은 많은데 레지스트리 지식이 없는
  장소가 곧 수집 대상이다 (`RecommendationSignal`의 `place_id=null` 행과 같은 용도).
