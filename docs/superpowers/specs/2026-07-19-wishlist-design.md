# 위시리스트 (찜 저장) — 설계 문서

**날짜**: 2026-07-19
**페이즈**: 1 / N (엔게이지먼트 레이어 로드맵의 첫 조각)
**상태**: 설계 검토 대기

---

## 1. 배경 & 목표

에어비앤비가 검증한 재방문 장치 — 비교·결정형 소비(가이드/코스 고르기)에서 "찜해두고
나중에 비교"는 필수 동선인데 현재 앱에 없다. 저장 목록 페이지가 재방문 이유를 만든다.

**목표**: 여행자가 가이드·코스·장소 카드에 ♡ 로 저장하고, `/saved` 목록에서 다시 본다.
저장수는 소셜 프루프(예: "1,200명이 찜함")로 노출한다.

이 문서는 **페이즈 1(찜 저장 + 저장수 + "이 코스 따라하기")** 만 다룬다.
좋아요/댓글, 팔로우 피드, 시즌 컬렉션, 릴스(영상)는 후속 페이즈다.

---

## 2. 핵심 설계 결정

### 2.1 다형성(polymorphic) 저장 — 한 테이블, 3종 대상
Follow(`follows`)는 `guide_profile_id` 하나만 참조해 단순했다. 위시리스트는 성격이
다른 3종을 담아야 한다:

| 대상 | DB 존재 | 참조 방식 |
|------|---------|-----------|
| **가이드** | `guide_profiles` (id 있음) | id 참조 |
| **코스** | `tour_courses` (id 있음) | id 참조 |
| **장소** | 두 출처 혼재 | 참조 + **스냅샷** |

장소는 정적 명소(`SPOTS`, slug)와 **Kakao 검색결과(우리 DB에 행이 없음)**가 섞인다.
Kakao 장소는 DB 조인으로 렌더할 수 없으므로, **저장 시점에 이름/카테고리/주소/좌표/이미지를
스냅샷**해 목록을 독립적으로 렌더한다. 이 방식이 SPOTS·Kakao 둘 다 동일하게 처리한다.

가이드·코스는 id 참조만 하고, 목록 조회 시 원본 테이블에서 현재 정보를 배치로 조회한다
(N+1 방지 — `FollowService.myFollowing`/`TourCourseService.listAll`의 기존 배치 패턴 준수).
> 근거: 가이드·코스는 우리 소유 데이터라 항상 최신을 보여주는 게 맞다. 장소(특히 Kakao)는
> 소유 데이터가 아니고 재조회 비용/불안정성이 있어 스냅샷이 맞다.

### 2.2 트랙/라이선스 경계
- **코스**는 tour 트랙(VERIFIED 가이드) 콘텐츠다. 하지만 **찜 자체는 여행자 행위**이며
  트랙 게이팅 대상이 아니다 — 여행자는 어느 트랙에서 보든 저장할 수 있다.
- 저장 목록의 코스 항목은 원본 코스가 비활성(`active=false`)/삭제되면 목록에서 숨긴다
  (가이드·코스 공통: 원본이 사라지면 조용히 제외, 스냅샷 없는 대상이라 렌더 불가).
- 장소 찜은 트랙과 무관(순수 콘텐츠).

### 2.3 저장수(소셜 프루프)
가이드·코스에 대해 "N명이 찜함"을 노출한다. `FollowRepository.followerCountsByGuideProfileIds`
와 동일한 GROUP BY 배치 집계로 목록 화면 N+1을 방지한다. 노출은 **공개 배치 카운트
엔드포인트**(`GET /api/saved/counts?type=&ids=`)로 프론트가 화면의 id를 모아 1회 조회 —
기존 `GuideSummaryResponse`/`TourCourseResponse` record를 건드리지 않아 변경 파급이 없다.
장소는 페이즈 1에서 저장수 미노출(스냅샷 기반이라 동일 장소 판별 키가 출처별로 달라
집계 신뢰도 낮음 — 후속 검토).

### 2.4 "이 코스 따라하기" → 내 여행일정 복사
코스 카드/상세에서 한 번에 새 `Itinerary`를 만들어 코스를 담는다.
저장→따라하기→(원하면)예약 루프의 핵심 훅.

**기존 컨벤션 재사용**: `TimetableBuilder`가 이미 코스를 일정에 넣는 방식을 정의했다 —
waypoint를 낱개로 풀지 않고 **`sourceCourseId`를 가진 블록 1개**로 넣는다(placeModuleAt
`kind === "course"` 분기: placeName=코스 제목, 좌표=첫 waypoint, durationHours=코스 소요시간).
따라하기도 동일하게 코스 블록 1개짜리 새 일정을 만든다. 일정 빌더에서 블록 클릭 시
`sourceCourseId`로 코스 상세 모달이 열리므로 전체 동선을 거기서 본다.

**신규 백엔드 불필요**: `POST /api/itineraries/me`(생성) + `PUT /api/itineraries/me/{id}`
(아이템 저장)를 프론트에서 연달아 호출한다. 프론트 전용 헬퍼 함수로 구현.

---

## 3. 데이터 모델

### 신규 엔티티: `SavedItem` (`saved_items`)

```
id             BIGINT PK
user_id        BIGINT   NOT NULL          -- 저장한 여행자
item_type      VARCHAR  NOT NULL          -- GUIDE | COURSE | PLACE (enum)
ref_id         BIGINT   NULL              -- GUIDE=guide_profile_id, COURSE=tour_course_id
place_ref      VARCHAR  NULL              -- PLACE: SPOTS slug 또는 Kakao place_id
-- 장소 스냅샷 (item_type=PLACE 일 때만 채움)
place_name     VARCHAR  NULL
place_category VARCHAR  NULL
place_address  VARCHAR  NULL
place_lat      DOUBLE   NULL
place_lng      DOUBLE   NULL
place_image    VARCHAR  NULL
created_at     TIMESTAMP NOT NULL
```

**유니크 제약** (중복 저장 방지, idempotent):
- GUIDE/COURSE: `(user_id, item_type, ref_id)`
- PLACE: `(user_id, item_type, place_ref)`

> JPA `ddl-auto: update` 환경 — 부분 유니크 인덱스는 이식성이 낮으므로, 단순화를 위해
> **애플리케이션 레벨에서 존재 검사 후 저장**(Follow의 `existsBy...` idempotent 패턴)하고,
> 물리 유니크 제약은 `(user_id, item_type, ref_id, place_ref)` 복합 하나로 건다
> (null 조합이 대상별로 다르므로 실질 중복을 막는다). 컬럼 드롭 없음.

---

## 4. API

모두 인증 필요(`@AuthenticationPrincipal Long userId`). 공개 카운트만 비인증 허용.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/saved` | 저장. 본문: `{itemType, refId?, place?{ref,name,category,address,lat,lng,image}}`. idempotent |
| `DELETE` | `/api/saved` | 저장 해제. 쿼리: `itemType`, `refId` 또는 `placeRef` |
| `GET` | `/api/saved` | 내 저장 목록 (탭용, `?type=` 필터 선택). 가이드/코스는 원본 배치 조회로 현재 정보 채움 |
| `GET` | `/api/saved/ids` | 내가 저장한 id/ref 집합 3종 일괄 (카드 ♡ 상태 초기화용, 경량) |
| `GET` | `/api/saved/counts?type=GUIDE\|COURSE&ids=1,2,3` | 저장수 배치 (공개, GROUP BY 1쿼리) |

### "이 코스 따라하기"
신규 API 없음 — 기존 `POST /api/itineraries/me` + `PUT /api/itineraries/me/{id}` 재사용.
새 일정(제목=코스 제목, city=코스 city)을 만들고 코스 블록 아이템 1개
(`sourceCourseId`, TimetableBuilder의 course 모듈과 동일 형태)를 저장한 뒤 `/trips/{id}`로 이동.

### `/api/saved/ids` 응답 형태
```json
{ "guideIds": [1, 2], "courseIds": [3], "placeRefs": ["gyeongbokgung", "kakao:123456"] }
```
- 한 번의 호출로 3종 모두 반환(카드 ♡ 초기화가 페이지 단위이므로 타입 분리 호출 불필요).
- `placeRefs`는 SPOTS slug 또는 `kakao:{place_id}` 접두 포맷 — 출처 간 충돌 방지.
  저장 시에도 동일 포맷으로 `place_ref`에 기록한다.

---

## 5. 프론트엔드

### 5.1 공용 ♡ 컴포넌트: `SaveButton`
- props: `itemType`, `refId`/`place`, 초기 `saved` 상태, 선택적 `count`
- 낙관적 토글 + 실패 시 롤백(following/page.tsx `onUnfollow` 패턴)
- 비로그인 클릭 시 `/login` 유도
- 카드 위 우상단 오버레이 배치(이미지 위 반투명 원형 버튼)

### 5.2 ♡ 부착 위치
| 위치 | 파일 | 대상 |
|------|------|------|
| 가이드 카드 | `GuideCard.tsx` | GUIDE |
| 코스 카드 | `CourseCard.tsx` | COURSE |
| 장소 카드(탐색) | `explore/page.tsx` | PLACE (Kakao 스냅샷, ref=`kakao:{id}`) |
| 명소 상세 히어로 | `spots/[slug]/page.tsx` | PLACE (ref=SPOTS slug). 랜딩 셸프는 상세 1클릭 거리라 v1 제외 |

### 5.3 저장 목록 페이지: `/saved`
- 탭 3개(가이드 / 코스 / 장소), `following/page.tsx` 레이아웃·스켈레톤·빈 상태 재사용
- 각 탭은 해당 카드 컴포넌트 재사용해 렌더
- 사이드바 여행자 메뉴에 "저장됨" 진입점 추가(`Sidebar.tsx`)

### 5.4 "이 코스 따라하기" 버튼
- 코스 상세/카드에 버튼 → `POST /api/itineraries/from-course/{id}` → 반환된 일정으로 라우팅(`/trips/{id}`)
- 로딩/성공 토스트, 비로그인 시 `/login`

### i18n
`src/lib/i18n.ts`에 ko/en/zh 3개 언어로 키 추가: 저장/해제, 저장됨 탭 라벨, 빈 상태,
"이 코스 따라하기", "N명이 찜함". **모든 키 3개 언어 필수.**

---

## 6. 유닛 경계 (독립 테스트/이해 단위)

- **백엔드**: `SavedItem`(엔티티) · `SavedItemRepository`(존재검사/목록/카운트 배치 쿼리) ·
  `SavedItemService`(idempotent 저장/해제, 원본 배치 조회 조립) · `SavedItemController`(REST).
  Follow 4-파일 구조를 그대로 미러링 → 리뷰·테스트 용이.
- **코스 복사**: 백엔드 변경 없음 — 프론트 헬퍼(`lib/followCourse.ts` 등) 하나.
- **프론트**: `SaveButton`(단일 책임, 어디서든 재사용) · `/saved` 페이지(조립만).

---

## 7. 에러 처리

- 저장/해제는 idempotent — 중복 저장/없는 것 해제는 조용히 성공(Follow 패턴).
- 원본이 사라진 가이드/코스는 목록에서 필터 제외(`myFollowing`의 null-filter 패턴).
- 코스 따라하기: 일정 생성/아이템 저장 중 실패 시 토스트로 안내(생성만 되고 아이템 실패 시
  빈 일정이 남는 건 허용 — 빌더에서 이어서 채울 수 있어 무해).
- 카운트 엔드포인트는 항상 200(공개), 대상 없으면 `{count: 0}`.

---

## 8. 스코프 밖 (후속 페이즈)

- 코스 좋아요/댓글, 팔로우 피드 (페이즈 2)
- 시즌 큐레이션 컬렉션 (페이즈 3)
- 코스 릴스/영상 (페이즈 4 — 영상 인프라 + npm 패키지 승인 별도)
- "이 코스로 예약하기" 유료 CTA (Booking↔Course 신규 연결 — 별도 항목)
- 장소 저장수 집계(출처별 판별 키 정리 후)

---

## 9. 미해결 확인 사항

- **없음** — 검토 후 이견 없으면 구현 계획(writing-plans)으로 진행.
