# 코스 추천 개선 — 설계 (1사이클 확정)

- 날짜: 2026-08-10
- 상태: **1사이클 구현 완료 · 실 DB 스모크 통과** (백엔드 221 tests green · 프론트 tsc/build green ·
  스모크 9/9 — 근거·`courseRef`·ADDED 기록 실증). 커밋 전.
- 선행: `2026-08-06-registry-driven-course-recommendation-design.md` (구현 완료, main 머지 `6c7a199`)

---

## 착수 전 사실 확인 (코드 실측 — 전제가 틀리면 설계가 통째로 어긋난다)

**① 가이드 자격 게이팅은 이미 구현돼 있다.** 새로 만들 것이 아니다.

| 지점 | 내용 |
|---|---|
| `Sidebar.tsx:126` | `!inCompanionWorld && verified` 일 때만 `/guide/courses` 메뉴 노출 |
| `TourCourseService.java:121` | `category.requiresGuideLicense() && !profile.isVerified()` → 거부 |
| `ServiceCategory.java` | `TOUR_GUIDE(true)` — 관광안내는 자격 필수 (관광진흥법 §38) |
| `GuideProfile.verificationStatus` | `NONE / PENDING / VERIFIED / REJECTED` (진실의 원천은 `GuideVerification`, 여기 비정규화 복사) |

→ **진짜 결함은 "막는 것"이 아니라 "안내가 없는 것"**이다. 미인증 가이드에게 메뉴가 설명 없이 사라져
"왜 없지?"가 되고 인증하면 열린다는 걸 알 방법이 없다.

**② ★ 레지스트리 기반 추천을 여행자는 볼 수 없다.**

```
/trips/[id]      mode="trip"   → 팔레트 2번 탭 = 투어 코스(가이드 상품)
/guide/courses   mode="course" → 팔레트 2번 탭 = 코스 추천   ← 여기서만 recommend 호출
```

`/api/courses/recommend`는 **VERIFIED 인증 가이드 전용 화면에서만** 호출된다.
12초로 줄이고 인사이트까지 붙인 기능을 정작 여행자는 접근조차 못 한다. 최대 미개봉 가치.

**③ 용어 충돌** — "코스"는 이미 가이드가 파는 상품(`TourCourse`: 가격·정원·`serviceCategory`)이다.
여행자용을 같은 이름으로 부르면 예약 가능한 상품과 혼동된다.

---

## 승인된 프레임: 플라이휠 (사용자 확인 완료)

이 기능의 정체는 "장소 추천"이 아니라 **예약 퍼널의 입구**다.
지도 앱도 장소는 나열한다. 우리만 할 수 있는 것은 —
**마음에 드는 동선을 만들면 그 동선을 안내해줄 자격증 있는 사람을 붙여주는 것.**

```
   인증 가이드가 코스를 만든다  (자격 게이팅 = 데이터 품질 장치)
              ↓
   tour_course_waypoints = "전문가가 검증한 장소" 신호
              ↓
   여행자 추천에 근거로 표시 ──→ 여행자가 동선을 짠다
              ↓                        ↓
              │      itinerary_items·saved_items = "여행자들이 담은" 신호
              ↓                        ↓
   "이 동선 가능한 가이드" ←──── 근거가 더 강해진다
              ↓
       예약 발생 → 가이드가 코스를 더 만든다 → (반복)
```

**핵심**: 자격 게이팅이 제약이 아니라 무기다. "인증 가이드 3명이 이 장소를 코스에 넣었다"가
믿을 만한 이유는 *가이드만 코스를 만들 수 있기 때문*이다. 법 대응으로 만든 게이팅이
블로그 크롤링으로는 못 만드는 검증된 전문가 데이터의 원천이 된다.

---

## 추천 이유 = 증거 사다리

| 가족 | 근거 데이터 | 표시 예 | 실측 상태 |
|---|---|---|---|
| 🎫 전문가 | `tour_course_waypoints` | "인증 가이드가 코스에 담은 곳" | 모델 O, 데이터 ~0 → **1사이클 구현** |
| 🧳 여행자들 | `itinerary_items` · `saved_items` | "여행자 12명이 일정에 담음" | 모델 O, 데이터 ~0 → **2사이클** |
| 🏛 공공기관 | `place_insights` + `evidence.publisher` | "한국관광공사 자료 · 사진 명소" | **오늘 동작 (11건)** |
| 📍 당신 | 동선 계산 / (관심사는 미연결) | "이전 정차지에서 도보 6분" | **동선만 오늘 동작** — 아래 정정 |

**규칙 3개가 신빙성의 전부다:**
1. 있는 것 중 **가장 강한 것만** 보여준다 (구체적 선택 규칙은 §2).
2. **0은 절대 표시하지 않는다** — "0명이 담았어요"는 역효과. cold-start는 🏛·📍가 버티고,
   데이터가 쌓이면 🎫·🧳로 자연 승급한다.
3. **출처를 항상 밝힌다.** 🏛 배지는 TourAPI `attribution_required: true` 의무와 동시 해결.

### ⚠ 브레인스토밍 원문 정정 — 📍는 절반만 동작한다

원문 표는 📍를 "오늘 동작(관심사 · MatchScore · 동선)"으로 적었으나 실측 결과 **관심사 개인화는 추천에 연결돼 있지 않다.**
`CoursePlanner.plan(KoreanCity, String district, List<String> slots)` (`CoursePlanner.java:125`) — 사용자 인자가 아예 없다.
관심사를 반영하려면 planner 시그니처 + 후보 점수 로직을 손봐야 하므로 **1사이클의 📍는 동선 근거까지**로 한정한다
(`distanceFromPrevMeters`가 이미 응답에 있어 추가 계산 0). 관심사 개인화는 2사이클.

---

## 🔴 1사이클을 시작하기 전에: 오늘 돌아가는 버그

`Stop` 레코드(`CourseRecommendController.java:159-172`)는 **장소 식별자를 하나도 싣지 않는다.**
그래서 프론트가 식별자를 합성한다:

```
TimetableBuilder.tsx:338   recPlaces → id: `rec-${s.order}-${s.name}`     ← 합성된 가짜 id
TimetableBuilder.tsx:243   드래그 시  placeId: p.id
                           → tour_course_waypoints.place_id = "rec-3-경복궁"
```

결과가 둘이다.

1. **오늘 보이는 증상** — `kakaoMapUrl()`(`TimetableBuilder.tsx:102-103`)이
   `https://place.map.kakao.com/rec-3-경복궁`을 만든다. 코스 모드에서 추천 정차지를 담으면 카카오맵 링크가 깨진다.
2. **🎫의 원천 테이블이 오염 중** — 조인 키가 되어야 할 컬럼에 조인 불가능한 문자열이 들어간다.

→ **`Stop`에 식별자를 싣는 것은 신기능 배관이 아니라 선행 버그 픽스다.** 1사이클의 첫 작업.

**기존 오염 행 처리: 정리하지 않는다(명시적 결정).** 코스 데이터가 0~2건이라 정리 비용이 가치보다 크다.
`place_id`가 `rec-`로 시작하는 행은 어떤 조인에도 걸리지 않으므로 조용히 무시된다.

---

## 조인 키 실측 (원문 "확인 필요" 해소)

이름+좌표 매칭 문제가 아니다 — **양쪽 다 kakao place id를 저장한다.**

| 테이블 | 컬럼 | 형식 | 비고 |
|---|---|---|---|
| `places` | `kakao_place_id` | 원시 id | `uk_places_kakao` unique (`Place.java:22,52`) |
| `tour_course_waypoints` | `place_id` | 원시 id | `TourCourseWaypoint.java:28` — 위 버그로 오염 중 |
| `itinerary_items` | `place_id` | 원시 id | `ItineraryItem.java:32` |
| `saved_items` | `place_ref` | **`kakao:{id}` 접두어** | `explore/page.tsx:149` — 2사이클에서 스트립 필요 |

---

## 1사이클 범위 (승인 완료)

### ⓪ 선행 버그 픽스 — `Stop`에 식별자 싣기

`Stop`에 `placeId`(레지스트리 Long, nullable) + `kakaoPlaceId`(String, nullable) 추가 (additive).
`PlannedStop`(`CoursePlanner.java:72-76`)이 이미 둘 다 들고 있으므로 전달만 하면 된다.
프론트 `recPlaces`는 합성 id를 버리고 `kakaoPlaceId`를 쓴다 — 없으면(폴백 정차지) `null`,
`kakaoMapUrl`이 이미 null을 좌표 검색으로 처리한다.

### ① 여행자 개방 — 새 화면 없이 탭 하나

`TimetableBuilder.tsx`의 `paletteTab`을 `"places" | "second"` → 3값으로 확장.
trip 모드에서 3번째 탭 **"✨ 추천 동선"** 노출, course 모드는 지금 그대로.
추천 렌더 블록은 이미 한 컴포넌트 안에 있으므로 조건만 넓힌다(중복 0).
**백엔드 변경 0** — `/api/courses/recommend`는 역할 제한이 없다.
`SecurityConfig`에서 permitAll이 아니지만 `/trips/[id]`는 어차피 로그인 화면이라 **보안 설정 변경도 불필요**.

여행자 쪽 이름은 **"추천 동선"**. "코스"는 가이드 판매 상품으로 남긴다(용어 충돌 회피).

course 모드의 `onFillFormFromRec`(코스 생성 폼 채우기)는 trip 모드에 해당하지 않는다 —
trip 모드에서 추천 정차지는 **드래그로 일정에 담는 경로만** 제공한다.

### ② 추천 이유 (`reasons[]`)

`Stop`에 `reasons: List<Reason>` 추가 (additive). `Reason = (kind, label, count, source)`.

**표시 규칙 — 최대 2개: 사회적 근거 1개 + 📍 1개.**
서로 다른 가족을 섞는다. cold-start에서 🏛+📍, 데이터가 쌓이면 자동으로 🎫+📍가 된다.
사회적 근거 우선순위는 🎫 > 🏛 (🧳는 2사이클에 이 서열 사이로 들어온다).

| 가족 | 1사이클 라벨 | 근거 |
|---|---|---|
| 🎫 | N=1: "인증 가이드가 코스에 담은 곳" (숫자 숨김) · N≥2: "인증 가이드 N명이 코스에 담음" | §🎫 집계 정의 |
| 🏛 | "{publisher} 자료 · {kind 라벨}" | `place_insights` + `evidence.publisher` |
| 📍 | "이전 정차지에서 도보 N분" | `distanceFromPrevMeters` (이미 응답에 있음) |

**⚠ 구현 시 추가한 제한: 📍는 1km 이내에서만 붙인다** (`CourseReasons.WALKABLE_MAX_METERS`).
도보권 밖에서 "도보 22분"은 근거가 아니라 거짓말이기 때문이다. 대신 이 제한은
cold-start를 버티는 두 가족 중 하나를 좁힌다 — 구 반경이 6km라 1km를 넘는 구간이 흔하다.
🎫≈0 + 🏛(발행처 필수) + 📍(1km 이내)가 겹치면 **모든 정차지가 근거 0줄**일 수 있고,
그 화면은 "고장난 것"과 구분되지 않는다. 그래서 스모크 7번은 근거와 함께
`distanceFromPrevMeters`를 같이 출력해 "구간이 다 1km 초과" / "publisher가 null" /
"조립이 깨짐"을 구분할 수 있게 했다. 근거 0이 곧 결함은 아니다.

**첫 정차지는 구조적으로 📍를 못 받는다** — `legMeters`는 `i == 0`에서 null이다
(`CourseRecommendController.java:82`). 규칙2와 겹치면 **1번 카드가 인사이트까지 없을 때 근거 0줄로 렌더된다.**
가장 눈에 띄는 카드가 가장 비어 보이기 쉽다는 뜻이다. 허용하되 UI에서 "깨진 것"처럼 보이지 않게 처리한다
(빈 근거 영역을 자리만 차지하게 두지 말 것).

N=1에서 숫자를 숨기는 이유: "인증 가이드 1명"은 근거로서 약하게 읽히지만 사실 자체는 유효하다.
숫자 없이 진술하면 정직성을 잃지 않으면서 약한 인상을 주지 않는다.

정차지 앞면에는 `VIBE` 한 줄, 상세 모달에 `PHOTO_SPOT`·`CAUTION`·`BEST_TIME` + 출처 배지.
네 `FactKind`는 모두 실재한다(`FactKind.java`).

**`reasons`는 `insights`를 대체하지 않는다** — `insights`는 상세 모달용 원본으로 그대로 남고,
`reasons`는 앞면 요약이다. 둘을 합치려 들면 course 모드의 기존 인사이트 렌더가 깨진다.

**⚠ `InsightView`에 `publisher`를 추가해야 한다 (선행).**
`PlaceInsight`에는 `evidence_publisher`(+`evidence_url`)가 있으나(`PlaceInsight.java:64-68`)
`PlaceInsightLookup.toView()`가 **투영하지 않는다** — 지금 `InsightView(kind, value, note, confidence)`뿐이다.
🏛 라벨은 `"{publisher} 자료 · {kind 라벨}"`이고 규칙3(출처 명시)은 TourAPI `attribution_required: true` 의무와 직결된다.
🏛는 1사이클에 실제로 렌더되는 두 가족 중 하나이므로, 투영이 없으면 **출처 의무를 지킬 수 없다.**
→ `InsightView`에 `publisher`(nullable) 추가 + `toView`에서 `i.getEvidencePublisher()` 전달 (additive, 기존 소비자 무영향).
`publisher`가 null인 인사이트는 🏛 근거로 쓰지 않는다(규칙3: 출처 없으면 배지 없음).

### 🎫 집계 정의 (⚠ 원문 전제 정정)

`TourCourseService.java:121`의 게이팅은 `category.requiresGuideLicense()`일 **때만** verified를 요구한다.
관광 외 카테고리 코스는 미인증 가이드도 만들 수 있다. 따라서 집계는:

```
SELECT w.place_id, COUNT(DISTINCT tc.guide_profile_id)
  FROM tour_course_waypoints w
  JOIN tour_courses tc   ON tc.id = w.tour_course_id AND tc.is_active = true
  JOIN guide_profiles gp ON gp.id = tc.guide_profile_id
 WHERE gp.verification_status = 'VERIFIED'
   AND w.place_id IN (:kakaoIds)
 GROUP BY w.place_id
```

⚠ **FK 컬럼은 `tour_course_id`다** (`TourCourse.java:87` `@JoinColumn(name = "tour_course_id")`).
그리고 이 연관은 **단방향** `@OneToMany`라 `TourCourseWaypoint`에 역참조 필드가 없다 —
JPQL로 쓰려면 `TourCourse tc JOIN tc.waypoints w` 방향으로 짜야 하고, waypoint에서 코스로 거슬러 올라갈 수 없다.
(엔티티에 역참조를 새로 추가하지 말 것 — 컬렉션 방향 조인으로 충분하다.)

- **`COUNT(DISTINCT guide_profile_id)`** — waypoint 수나 코스 수로 세면 한 가이드가 코스 5개를 만들었을 때 "5명"이 된다.
- **`verification_status = 'VERIFIED'` 필터 필수** — 없으면 미인증 가이드가 "인증 가이드"로 둔갑한다.
- **`tc.is_active = true`** — 비활성 코스는 판매 중이 아니다.
  컬럼명은 `is_active`이고 필드는 원시 `boolean`(`TourCourse.java:75-76`, `nullable = false`)이라
  기존 행이 NULL로 남아 조용히 누락되는 함정은 **없다**.

이 세 조건이 플라이휠 주장("자격 게이팅이 데이터 품질 장치")을 참으로 만든다. 하나라도 빠지면 배지가 거짓말이 된다.

**구현 형태**: `PlaceInsightLookup`을 그대로 본뜬 `GuideCourseSignalLookup` — 배치 조회만 노출하고
단건 조회를 아예 만들지 않는다(Supabase 시드니 왕복 250ms를 루프에 넣는 실수를 구조적으로 차단).
**캐시 없음** — 이 규모에서 무가치하고 무효화 비용만 는다.

**⚠ 구현 시 변경: 세 규칙을 SQL이 아니라 Java에 둔다 (쿼리 1회 → 2회).**
위 SQL을 그대로 JPQL에 넣으면 **이 저장소에서는 검증할 수단이 없다** — DB를 띄우는 테스트가 하나도 없고
(H2·testcontainers 미도입, `@DataJpaTest`/`@SpringBootTest` 사용처 0), 코스 데이터가 ~0이라 화면에서도
"집계가 틀린 것"과 "데이터가 없는 것"이 구분되지 않는다. 규칙이 검증 불가능하면 배지가 조용히 거짓이 된다.
→ 리포지토리는 `findByWaypointsPlaceIdIn`(`@EntityGraph("waypoints")`)로 **좁게 배치 조회**만 하고,
DISTINCT·VERIFIED·active 세 규칙은 `GuideCourseSignalLookup`의 Java 코드에서 적용한다.
가이드 인증 상태 조회 1회가 더 붙어 **총 2회 배치** — N+1은 여전히 없다.

**쿼리 예산: 3회 → 5회** (인사이트 3 + 🎫 2). `CourseRecommendController`의 javadoc도 같이 고쳤다 —
안 고치면 다음 사람에게 거짓말이 된다.

### ③ 신호 심기

추천 정차지를 일정/코스에 드래그하면 `ADDED` 기록.

**⚠ 원문 정정: 새 enum 값도, CHECK 제약 손질도 필요 없다.**
`RecommendationSignal.EventType`은 이미 `SHOWN, ADDED, BOOKED`이고(`RecommendationSignal.java:26`),
이 파일은 커밋이 `0a6167b` 하나뿐이라 값이 추가된 적이 없다 — 테이블 생성 시점의 제약에 3개가 다 들어있다.
`places_place_kind_check` 함정은 여기서 재현되지 않는다.

**⚠ 호출 지점이 없다 — 만들어야 한다.** `recordShown`이 쉬웠던 건 추천 응답 자체가 서버 요청이기 때문이다.
드래그는 순수 프론트다: `placeModuleAt`이 로컬 상태만 바꾸고, 백엔드는 일정 전체 PUT
(`/api/itineraries/me/{id}`, `trips/[id]/page.tsx:89`) 때까지 아무것도 모른다. 저장 시점의 아이템에는
"추천에서 왔다"는 표식도, `courseRef`(`city/district/theme` — 컨트롤러 안에서만 만들어지고 응답에 안 실림)도 없다.

**결정: 드롭 시 fire-and-forget POST.** `POST /api/courses/recommend/signals`
(body: `placeId?`, `kakaoPlaceId?`, `courseRef`) → `recordAdded`. 이유:
일정/코스 PUT 계약을 건드리지 않고, `ItineraryItem`에 추천 출처 필드를 추가하지 않아도 된다.
저장 안 하고 담기만 한 것도 기록되지만, "담았다"는 의도 신호로는 그게 오히려 맞다.

**선행 조건**: `RecommendResponse`에 `courseRef` 노출 (현재 `CourseRecommendController.java:101-102`에서
만들어 `recordShown`에만 넘기고 버린다). additive이고 `SHOWN`↔`ADDED`를 같은 키로 짝지을 수 있게 해준다.

같은 장소를 두 번 담으면 두 행이 남는다 — 신호는 이벤트라 정상이며 `SHOWN`도 이미 같은 성질이다.

`SignalRecorder.recordShown`을 본뜬 `recordAdded(StopRef, courseRef, userId)` 추가.
`recordShown`과 동일하게 **실패는 삼키고 진행한다**(신호 기록이 사용자 동작을 막으면 안 된다).
레지스트리에 없는 장소도 `place_id: null`로 기록한다 — 그게 다음 수집 우선순위다.

### ④ 미인증 가이드 안내

`Sidebar.tsx:126`에서 숨기는 대신 **🔒 잠금 표시**로 노출 → `/guide/courses` 진입 시
"관광통역안내사 인증 시 코스 판매 가능(§38)" + 인증 신청 CTA. `PENDING`이면 "심사 중".
**서버 게이팅은 이미 있으므로 설명만 채우는 작업** — 권한 로직은 건드리지 않는다.

---

## 테스트 (명시적 산출물)

**커버리지가 ~0이라 "🎫가 깨진 것"과 "🎫 데이터가 없는 것"이 화면에서 구분되지 않는다.**
규칙2(0은 표시 안 함)가 정확히 그 둘을 같은 화면으로 만든다. 판별기는 테스트뿐이다.

1. **🎫 점등 테스트 (핵심)** — VERIFIED 가이드 + active 코스 + `place_id`가 레지스트리 `kakao_place_id`와
   일치하는 waypoint를 심고, 추천 응답의 해당 `Stop.reasons`에 🎫가 뜨는지 단언.
   이게 없으면 "지금 만들고 나중에 켜진다"가 조용히 "영원히 안 켜진다"가 된다.
2. **🎫 오염 방지 테스트** — ① 미인증 가이드의 코스는 세지 않는다 ② 비활성 코스는 세지 않는다
   ③ 한 가이드의 코스 3개는 "1명"이다.
3. **⓪ 회귀 테스트** — `kakao_place_id`를 가진 레지스트리 장소를 심고, 추천 응답의 `Stop`이 그 값을 그대로 싣는지 단언.
   (레지스트리 장소라고 `kakao_place_id`가 항상 있는 건 아니다 — nullable이고, 없으면 🎫 조인에도 안 걸린다.
   따라서 "레지스트리 유래 = non-null"은 불변식이 아니며 테스트도 그렇게 쓰면 안 된다.)
4. **규칙2 테스트** — 근거가 0인 정차지의 `reasons`는 빈 배열(0을 담은 항목이 아니라 아예 없음).
5. **배치 조회 테스트** — 정차지 수를 늘려도 `GuideCourseSignalLookup`이 **1회, 전체 id를 담은 리스트로** 호출되는지
   (Mockito `verify` + 인자 캡처). 실제 SQL 실행 횟수를 세는 하니스는 이 저장소에 없다
   (Hibernate `Statistics`·프록시 데이터소스 사용처 0) — 만드는 건 이 사이클 범위 밖이므로,
   루프 조회 회귀는 이 수준에서 잡는다.

### 실제 작성된 테스트 (33개 신규, 전체 221 green)

| 파일 | 개수 | 무엇을 고정하나 |
|---|---|---|
| `GuideCourseSignalLookupTest` | 11 | 🎫 세 규칙(DISTINCT 사람·VERIFIED만·active만) · 0은 키 없음 · 오염된 `rec-` id 무시 · 배치 1회 |
| `CourseReasonsTest` | 12 | 근거 선택 규칙 3개 · 출처 없으면 배지 없음 · 첫 정차지 근거 없음 · 도보권 밖 미표시 |
| `CourseRecommendControllerTest` | 8 | **🎫 점등(유일한 판별기)** · `kakaoPlaceId` 회귀 · 집계 실패해도 응답 정상 · `courseRef` 노출 · ADDED 엔드포인트 |
| `SignalRecorderTest` | 7 | ADDED 기록 · 미등록 장소도 기록 · 저장 실패를 삼킴 · 식별자 없으면 빈 행 안 만듦 |
| `PlaceInsightLookupTest`(추가) | 3 | `publisher` 투영 (🏛 출처 의무) |

**실 DB 검증**: `scripts/smoke/registry-course-smoke.sh`에 어서션 5개 추가(5~9번). **9/9 통과.**
실측된 것:
- 근거가 실제로 붙는다 — 🏛 "한국관광공사"(남산케이블카) · 📍 도보 7·10·12분
- `courseRef=Seoul/중구/culture` 응답 탑재 · `POST /signals` 200
- 출처 없는 🏛도, "0명" 🎫도 나오지 않음 (규칙2·3 실증)
- 🎫는 0회 노출 — 인증 가이드 코스가 없어서이며, 설계대로 조용히 안 보인다

**⚠ 실측으로 드러난 데이터 빈칸**: `places.kakao_place_id`가 비어 있는 레지스트리 장소가 있다(예: "개화", placeId=44).
합성 id는 만들어지지 않으므로 결함은 아니지만, 코스 waypoint(카카오 id 스냅샷)와 조인이 안 돼
**그 장소는 가이드가 코스에 넣어도 🎫가 영영 세지 못한다.** 수집 파이프라인의 kakao id 백필 대상 —
2사이클에서 다룬다. 스모크 5번이 이 빈칸을 매번 보고하도록 해뒀다(실패가 아니라 ⓘ 표시).

---

## 범위 밖 (명시)

- **🧳 여행자 집계** — 2사이클. 타인 일정 집계라 프라이버시 판단 + 최소 표시 임계값 결정이 추가로 필요하고,
  `saved_items.place_ref`의 `kakao:` 접두어 스트립도 여기서 다룬다.
- **관심사 개인화(📍 풀버전)** — 2사이클. `CoursePlanner` 시그니처 변경.
- **정차지 단위 교체(🔀) / 고정(📌)** — 2사이클. `PICK_POOL=3` 로직이 이미 있어 후보 노출만 하면 되지만
  1사이클의 척추(개방 + 근거 + 신호)와 독립적이다.
- **"이 동선 가능한 인증 가이드" → 예약 연결** — 3사이클. 플라이휠의 마지막 고리.
- **모달 5개 결함**(`PlacePickerModal`·`PostComposeModal`·`SharePickerModal`·`PhoneCollectModal`·`PlanCard`의
  `items-end` + `vh`) — **별건**. 이 설계와 인과가 없으므로 여기서 다루지 않는다.

---

## 2·3사이클 스케치

- **2사이클**: 증거 사다리 🧳 추가 · 관심사 개인화 · 정차지 단위 교체(🔀)/고정(📌) ·
  가이드용 "요즘 많이 담기는 장소" 수요 패널(③이 심은 `ADDED` 신호의 첫 소비처)
- **3사이클**: "이 동선 가능한 인증 가이드" → 예약 연결 · 랭킹 · 블로그 추출

**커버리지 정직성**(전 사이클 공통): 수집 안 된 지역은 "이 지역은 아직 검증 데이터 수집 중" 한 줄.
근거 없는 정차지에 억지 근거를 붙이지 않는다.
