# PeerUp 인수인계서 (2026-07-06 기준, Wave 3 콘텐츠까지 반영)

> 다음 세션이 바로 이어서 작업할 수 있게 정리한 문서.
> 상세 히스토리는 `app/PROGRESS.md`, 아이디어 백로그는 `IDEAS.md`, 아키텍처·구조는 `CLAUDE.md` 참고.
> **재개 시 읽는 순서**: 이 문서 → `CLAUDE.md`(구조/패턴) → `app/PROGRESS.md`(최근 완료분).
> ⚠️ **문서 위치**: HANDOFF.md·IDEAS.md는 리포 **루트**, PROGRESS.md는 `app/`에 있음. (Wave 2 designer가 실수로 `app/HANDOFF.md`·`app/IDEAS.md`를 만들었으나 코디네이터가 루트로 병합 후 삭제함.)

## 0. 최신 (2026-07-07) — 통합 인박스 + 안읽음 배지 + 가이드 코스 드래그 빌더

**세션 순서로 완료 (전부 tsc·compileJava·브라우저 E2E 통과)**:
1. **A2 통합 인박스** — `GET /api/inbox`(DM+예약채팅 한 목록). §6 참고, 상세 `app/PROGRESS.md`.
2. **예약 채팅 안읽음 배지** — `Booking.traveler/guide_last_read_at`, `GET /api/inbox/unread-count`(DM+예약 합산), 사이드바 폴링.
3. **가이드 코스 빌더 = 드래그 타임테이블** — 여행자 일정과 동일 UX. `components/TimetableBuilder.tsx`(신규 공유 컴포넌트, 트립+코스 공용, `mode`/`singleDay` prop). `/trips/[id]`·`/guide/courses` 둘 다 이걸 씀. 코스 추천은 팔레트 ✨탭으로 통합(정차지 드래그 + 폼채우기). `TourCourseWaypoint`에 시간표 필드(start_hour 등, 편집전용) additive. 상세 `app/PROGRESS.md` 최상단.
4. **가이드 사이드바 정리** — 데스크탑 레일에서 지역둘러보기·여행일정 제거(가이드 무관).
5. **UX 배치 ①** (4건): 채팅 장소버튼 라벨, 인박스 채팅별 안읽음 점(`InboxThreadResponse.unread`), 슬롯 다중선택 예약, 예약 거절 배지(`Booking.rejection_seen`, `GET /api/bookings/traveler/rejected-count`).
6. **UX 배치 ②** (2건, 프론트만): **다중일 시간범위 예약**(수동 폼 날짜별 행 편집기, 행별 예약+부분실패 요약), **채팅 일정/코스 공유**(`PEERUP::PLAN::` 스냅샷 규약, `lib/placeCard.ts` 통합 `parseCard`/`cardPreview`, `SharePickerModal`+`PlanCard.tsx`, ChatRoom "+" 첨부시트, DM·예약채팅 양쪽). 상세 `app/PROGRESS.md` 최상단.

**✅ T1/T2/T3 (만남장소/예약상세/위치공유) — 백엔드+프론트 완료, 브라우저 E2E 검증** (2026-07-07). 채팅 장소카드(Kakao 검색+내위치)·만남장소 지정(`Booking.meeting_place_*` PATCH)·예약 상세 페이지 `/bookings/[id]`. 장소카드는 프론트 인코딩 규약(`lib/placeCard.ts`, 백엔드 불투명). 상세 `app/PROGRESS.md` 최상단.

---

## 0. Wave 6 (2026-07-06, 드래그 타임테이블 일정 빌더) 요약 — 백엔드+프론트 완료, tsc·compileJava·**브라우저 드래그 E2E** 통과
`/trips/[id]` 일정 빌더를 ▲▼ 리스트에서 **시간표(캘린더 데이) 드래그앤드롭**으로 전면 개편.
- **개념**: 하루 = 세로축 1시간 단위 시간표(06:00~24:00). 장소·투어코스 모듈을 팔레트/미배치 트레이에서 **끌어다 시간 슬롯에 놓음**. 블록의 **깊이(소요시간)·넓이(레인 수)를 `+/−`로 조절**, 같은 시간대 여러 개는 옆 레인으로 나란히. 블록 탭 → 상세(장소=PlaceDetailModal, 코스=정차지 미리보기). 지도 동선은 **시간→레인 순서로 자동 정렬**해 폴리라인.
- **새 npm 패키지 `@dnd-kit/core` + `@dnd-kit/utilities`** (사용자 승인받음) — 모바일 터치 드래그 지원. `package.json`에 추가됨.
- **스키마(additive nullable, ddl 안전)**: `ItineraryItem`에 `start_hour`/`duration_hours`/`lane_index`/`lane_span`/`source_course_id` 5컬럼. `ItineraryItemRequest`·`Response`·`toItems`에 반영(기존 생성자 유지 → `autoAddTourItem` 무손상). 레거시/미배치 아이템은 `start_hour=null`.
- **핵심 구현 교훈**: `DragOverlay`를 쓰면 원본 노드가 제자리에 남아 @dnd-kit 기본 collision detection(정적 노드 rect 기준)이 **엉뚱한 셀을 집는다**. 그래서 셀 droppable을 버리고 `onDragEnd`에서 **포인터 좌표(`activatorEvent.clientX/Y + delta`)를 그리드 rect에 매핑**해 hour/lane을 직접 계산. 이게 캘린더 그리드엔 가장 견고. `resolveLane()`으로 같은 시간대 충돌 시 빈 레인 자동 배정.
- **코스 드롭 = 단일 🎫 블록**(정차지 미리보기, `source_course_id`로 A4 예약 CTA 연결 예정). 유료 코스 무단 복제 방지.
- **검증**: `npx tsc --noEmit` 통과, `gradle compileJava` 통과, curl 라운드트립(5개 새 필드 저장·조회), **Playwright 브라우저 드래그 총 17개 체크 전부 통과** — (1차 6개) 시간표 렌더/수동추가→트레이칩/드래그 배치/놓은 시간행 정렬/+시간 리사이즈/저장·새로고침 유지, (2차 11개) Kakao 장소 드롭→블록+**지도 폴리라인 렌더**/같은 시간대 2개→**옆 레인 자동 배치(resolveLane)**/넓이+(span)/**투어코스 드롭→🎫 블록**/블록 탭→정차지 미리보기 모달/저장 후 좌표·sourceCourseId 영속. ⚠️ Playwright 팁: @dnd-kit 드래그는 **합성 PointerEvent가 delta를 못 실어 실패** → `page.mouse`(신뢰 이벤트)로 해야 함. 저장 검증은 원격 DB(Sydney) 지연 때문에 **PUT 후 2초는 기다려야** 재조회에 반영됨.
- **미확인(다음 세션)**: 실제 모바일 터치 드래그 — advisor 지적대로 팔레트가 지도 아래(그리드에서 멀리)라 폰에서 팔레트→그리드 롱드래그 시 (a) UX 사용성, (b) auto-scroll이 포인터 좌표 매핑을 틀어뜨리는지 미검증. 데스크탑은 완전 동작. **레이아웃 개선(팔레트를 그리드 옆/위로, 또는 하단 sticky 드로어) 후보** — 사용자와 상의.
- **후속 개선(같은 세션, 브라우저 7체크 통과)**: ① 헤더에 `− 칸 줄이기` 추가(빈 레인 있을 때만, `canRemoveLane`). ② 블록·팔레트를 **네모 박스→대표 아이콘 타일 UI**로: `categoryIcon(name,cat,isTour)` 헬퍼가 이름·카테고리 키워드(ko/en/zh)로 이모지 매핑(궁궐🏯·식당🍽️·카페☕·전망대🗼·시장🛍️·문화🎭 등). ③ 블록의 **아이콘 클릭→카카오맵**(`kakaoMapUrl`: placeId 있으면 `place.map.kakao.com/{id}`, 없으면 좌표/검색 링크; 코스는 미리보기). ④ 팔레트 장소·코스를 **flex-wrap 칩→아이콘 카드**로. ⑤ **워크벤치 레이아웃**: `DndContext` 안을 `lg:grid grid-cols-[minmax(0,1fr)_20rem]`로 좌(시간표+지도)·우(팔레트) **나란히 배치**, 우측 팔레트는 `lg:sticky top-4`(스크롤 따라옴)+세로 스크롤, 페이지 폭 `max-w-6xl`. 모바일(<lg)은 기존대로 세로 스택. 팔레트 카드 그리드는 좁은 사이드바 맞춰 `lg:grid-cols-1`. ⑥ 검증: tsc 통과 + Playwright 총 10체크 통과(아이콘카드/드래그/카카오맵 팝업 URL/레인 +−, **팔레트가 시간표 우측에 side-by-side**·옆 패널에서 드래그 배치). ⑦ **배치된 블록 탭→상세 모달에 카카오맵 링크**: `openDetail`이 `placeUrl: kakaoMapUrl(it)`를 채워 `PlaceDetailModal`의 "카카오맵에서 열기" 버튼이 뜨게 함(이전엔 placeUrl 없어서 버튼 안 떴음). ⑧ **팔레트 카드 ⓘ→큰 "상세 정보" 글자 버튼**(카드 하단 full-width, `li.detailsBtn`). 둘 다 Playwright 5체크 통과.
- 테스트 오염(실 dev DB): itinerary 20·22~30 등 + 테스트 유저 여럿 (콘솔 정리 대상).

## 0. Wave 5 (2026-07-06, B2 신고·차단 [안전]) 요약 — 백엔드+프론트 완료, tsc·compileJava 통과 + curl E2E 검증
DM으로 낯선 사람과 1:1 대화가 열리는데 차단 수단이 없던 문제 해결(가장 시급 항목).
- **신규 `safety/` 패키지** — `Block`(blocker→blocked, unique쌍) / `Report`(reporter, targetType[USER/CONVERSATION/POST/REVIEW], targetId, reason[SPAM/HARASSMENT/SCAM/INAPPROPRIATE/OTHER], detail, status=OPEN) 엔티티·리포지토리·`SafetyService`·`SafetyController`. 엔드포인트 4개(전부 authenticated): `POST /api/blocks`{targetUserId}, `DELETE /api/blocks/{targetUserId}`, `GET /api/blocks`, `POST /api/reports`. **차단·신고 둘 다 멱등**(중복=no-op 200), **자기차단 400** — unique 위반→500위장401 함정(§3-4) 회피.
- **차단 enforcement는 서비스 계층 한 곳**(`ConversationService`)에 둠 — REST(`/api/conversations/{id}/messages`)와 WebSocket(`/app/conversations/{id}/send`) **둘 다 `send()`를 통과**하므로 WS 우회 없음(확인함). `getOrCreate`/`send` 차단 시 400, `myConversations`는 `BlockRepository.relatedUserIds()` 배치 1회로 상호 숨김, `unreadCount` 쿼리에도 `not exists Block` 서브쿼리 추가(유령 안읽음 배지 방지). `GuideController`의 `list`/`similar`(추천)는 로그인 뷰어 기준 차단 가이드 숨김(배치 1회), `detail`은 차단 시 400으로 상세 자체를 숨김(차단해제는 `/profile`에서 하므로 스트랜딩 없음).
- **프론트** — `components/ReportBlockMenu.tsx`(신규, 케밥⋯ + 신고 모달) = DM방·가이드프로필 **공유**. **공용 `ChatRoom.tsx`엔 안 넣음**(예약 채팅과 공유되므로) → DM 래퍼 `messages/[id]/page.tsx` 헤더에만. 가이드 상세 헤더(로그인시)·`components/BlockedUsersSection.tsx`(신규, `/profile` 차단목록+해제). 차단 성공 시 DM→`/messages`, 가이드→`/guides` 이동.
- **DTO 확장** — `ConversationResponse.otherUserId`, `GuideDetailResponse.guideUserId` 추가(프론트가 사람을 차단/신고하려면 상대 userId 필요). 둘 다 additive.
- i18n: 신규 최상위 그룹 `safety.*`(23개 키) ko/en/zh.
- **검증**: `npx tsc --noEmit` 통과, `gradle compileJava` 통과, 백엔드 재시작(ddl-auto가 blocks/reports 테이블 생성 확인) 후 curl E2E — 차단 멱등/자기차단400/신고dedup/유효성400/차단후 DM전송400(양방향)/인박스 상호숨김/unread=0/차단중 새DM400/가이드목록 숨김 전부 통과. 브라우저 UI는 미확인(다음 세션 권장).
- **남은 판단(백로그)**: ① 커뮤니티 피드/댓글에서 차단 사용자 콘텐츠 숨김은 미적용(피드 N+1 우려로 이번 범위 제외). ② **예약 채팅(`MessageService.send`)은 차단 enforce 안 함** — DM(`ConversationService`)만 적용. 확정된 예약 파트너와의 채팅에는 차단 컨트롤을 안 두는 Wave-2 판단과 일관된 의도적 경계(예약 채팅에 차단 UI 없음). ③ 신고 관리자 검토 도구 없음(저장만).
- **테스트 데이터 오염**(실 Supabase dev DB, 콘솔서 수동 정리): users 44~47, guide profile 17, conversation 3, blocks/reports 몇 건.

## 0. Wave 4 (2026-07-06, 인앱 장소 상세) 요약 — 새 백엔드 없음, 프론트만
`/explore`·`/trips/[id]`에서 장소를 누르면 카카오맵으로 나가버리던 걸 앱 안에서 바로 상세를 보게 개선.
- **`components/PlaceDetailModal.tsx`(신규)** — 이름/카테고리, 단일 핀 지도(`TripMap` 재사용), 주소(+복사 버튼), 전화(`tel:` 링크), 거리, 명소 매칭 시 "이 명소 자세히 보기"(`/spots/[slug]`) 주 버튼, "카카오맵에서 열기"는 하단 보조(ghost) 버튼으로 강등. Esc/backdrop 클릭 닫기, 열릴 때 닫기 버튼 포커스, 열려있는 동안 `document.body.style.overflow` 잠금.
- **`matchSpot` 단일 소스화** — `/explore`·`/trips/[id]`에 각각 중복 정의돼 있던 걸 `lib/spots.ts`로 이동(`export function matchSpot`), 세 곳(두 페이지 + 모달) 모두 이걸 import.
- **`/explore`** — 카드 클릭 시 인라인 펼침(`expandedId`) 대신 모달 오픈(`selectedPlace`)으로 교체. 카드 내 "카카오맵에서 보기" 버튼은 모달로 흡수돼 제거(중복 제거).
- **`/trips/[id]`** — 장소 검색 결과 리스트의 "담기" 버튼(일정 추가)은 그대로 유지, 인라인 펼침(▲▼ + 명소 이미지)을 "ⓘ" 상세 버튼으로 교체해 모달을 연다. 모달에는 옵션 `onAdd`/`addLabel` prop을 추가해 이 페이지에서만 모달 안에도 "일정에 추가" 버튼이 뜨게 함(explore에서는 전달 안 하므로 안 보임).
- **`TripMap.tsx` 단일 핀 줌 버그 수정(부수 효과, 기존 컴포넌트)** — `map.setBounds(bounds)`를 점 1개로 호출하면 면적 0인 bounds라 카카오가 최대줌(건물 단위)으로 스냅해버림. 모달의 단일 핀 지도에서 처음 노출됐지만, 사실 `/trips/[id]`의 "이 날 경로" 지도도 하루에 장소가 1곳뿐이면 원래부터 같은 문제가 있었을 것(그동안 우연히 안 걸렸거나 눈치 못 챈 것으로 추정). `path.length>=2`는 기존 `setBounds` 그대로, 1개일 땐 `setCenter`+`setLevel(4)`로 분기 — 기존 다중 지점 동작은 바이트 단위로 그대로, 단일 지점만 고쳐짐. 브라우저로 직접 확인은 못 했음(이 세션 셸 도구 없음) — **다음 세션에서 `/explore` 카드 상세 모달 + `/trips/[id]` 1곳짜리 날짜의 지도 줌 레벨을 실제로 확인 권장**.
- **알려진 한계 (백로그)** — Kakao 로컬 REST API 응답에는 사진·영업시간·평점이 없음. 더 풍부한 상세가 필요하면 Google Places Details API 등 별도 데이터 소스 도입이 필요 — `IDEAS.md`에 항목 추가.
- i18n: 신규 최상위 그룹 `placeDetail.{close,call,distance,copyAddress,addressCopied,viewSpot,openInKakao,noCoords}` + `itinerary.detailsBtn` — ko/en/zh 전부 동일 키.
- 검증: 코드 리뷰 기반 수동 타입 점검(`ModalPlace` 필드 전부 optional이라 explore/trips 두 `Place` 타입 모두 구조적으로 대입 가능 확인) + ko/en/zh 키 3블록 직접 대조. `npx tsc --noEmit`은 코디네이터가 이어서 실행 예정(이 세션엔 셸 도구 없음).

## 0. Wave 3 (2026-07-06, 콘텐츠) 요약 — 백엔드는 사전 구현·검증됨, 이번 세션은 프론트만
백엔드 계약 3개(developer가 이미 curl 검증): ① `TourCourseResponse.waypoints`(sortOrder순, 항상 배열) + 신규 `PUT /api/guide-profiles/me/courses/{id}`(수정, multipart). ② `GET /api/guides/{id}/review-stats`(공개, 별점 분포+태그 집계, 항상 0-fill) + `ReviewResponse.tags`/리뷰 작성 시 `tags: string[]`(canonical key 8개: kind/punctual/knowledgeable/flexible/goodPhotos/goodFood/languageGood/funny). ③ 새 백엔드 없음(명소 퍼널은 기존 `/api/guides?city=`·`/api/courses?city=` 재사용).

프론트(designer) 완료분:
- **코스 동선(waypoints)** — `guides/[id]/page.tsx` 코스 카드에 "동선 보기" 토글(TripMap+정차지 리스트, 별도 라우트 없이 기존 인라인 표시 확장). `guide/courses/page.tsx`에 동선 편집기 신규(추천 패널에서 정차지 개별 담기 + ▲▼ 순서/삭제) + **코스 수정(PUT) 기능 신규**(그 전엔 삭제만 가능했음). `lib/api.ts`의 `apiUpload`에 `method` 옵션 추가(POST 전용 → PUT도 가능, 하위 호환 유지, auth 로직 불변).
- **명소→가이드/코스 퍼널** — `spots/[slug]/page.tsx` 하단에 "이 지역 가이드"/"이 지역 투어 코스" 섹션. `spot.city.en`이 `KoreanCity` key와 표기가 같아 그대로 city 필터로 사용(6개 명소 전부 확인), 매칭 실패/빈 결과는 섹션 자체를 생략. 코스 카드를 `components/CourseCard.tsx`로 신규 추출해 `guides/page.tsx`와 공유.
- **리뷰 분포+키워드** — `guides/[id]/page.tsx`에 별점 분포 막대(5★~1★) + 태그 칩(카운트 0 초과만, 내림차순), 리뷰별 태그 칩. `review/[bookingId]/page.tsx`에 8개 canonical 태그 다중 선택 UI 추가.
- i18n: `courses.*`(동선 관련 17개 키), `spotDetail.*`(퍼널 6개 키), `guideDetail.reviewKeywords`, `review.{tagsLabel,tagsSub}`, 신규 최상위 그룹 `reviewTags`(8개 태그 라벨) — ko/en/zh 전부 동일 키.
- **판단 사항**: ① 코스 "단건 상세 페이지"(`/courses/[id]`)는 만들지 않음 — 백엔드에 단건 GET이 없고 기존에도 코스는 항상 가이드 상세로 링크되는 구조라 인라인 확장이 최소 변경. 필요시 별도 과제로. ② 동선에 자유 장소를 직접 검색해 추가하는 기능은 없음 — 추천 패널(Kakao 장소 검색 기반)에서 담는 방식만 지원. ③ 별점 분포 막대는 `w-[10%]`~`w-full`을 10% 단위로 미리 적어둔 **리터럴** Tailwind 클래스 lookup에서 골라 씀(런타임 문자열 보간 `w-[${pct}%]`는 Tailwind 정적 스캐너가 못 읽어 스타일이 안 먹으므로 반드시 리터럴로 소스에 존재해야 함 — 이 패턴이 필요하면 재사용할 것).
- ⚠️ 이 세션도 `npx tsc --noEmit`은 코디네이터가 이어서 실행 예정(셸 도구 없음) — ko/en/zh 키 파리티는 세 블록 직접 대조로만 확인.

## 0. Wave 2 (2026-07-06, 예약 전환) 요약 — 백엔드+프론트 완료, tsc·compileJava 통과
백엔드(developer, curl 검증): `GuideProfile.instant_booking`(nullable) + `PATCH /api/guide-profiles/me/instant-booking`; 즉시예약 시 booking이 바로 ACCEPTED(단, accept()와 같은 시간겹침 충돌검사 공유 — `hasOverlapWithAccepted()`); `ItineraryItem.source_booking_id`(nullable, 멱등키) + 예약 확정 시 여행자 일정에 "🎫 가이드 투어" 자동 추가(`ItineraryService.autoAddTourItem`, `REQUIRES_NEW`로 격리 — 일정 추가 실패해도 예약 확정은 롤백 안 됨); `GET /api/guides/{id}/similar?lang=`(public, 같은 도시 우선 유사 가이드 최대 3명, GuideSummaryResponse[]).
프론트(designer): `guide/manage` 즉시예약 토글, `guides/[id]` ⚡배지+즉시/일반 버튼 분기+확정 패널+취소정책 안내박스+예약 폼 검증, `guides/page` 카드를 `components/GuideCard.tsx`로 추출·재사용(⚡배지), `traveler/bookings` REJECTED 예약 아래 유사 가이드 추천, `trips/[id]` `category==="tour"` 아이템 amber 스타일. i18n `guideDetail`/`guideManage`/`travelerBookings.similarGuidesTitle`/`itinerary.tourBadge` ko/en/zh.
**코디네이터 확인 필요한 판단**:
1. 유사 가이드 추천은 **REJECTED만** — 코드상 `cancel()`은 여행자 본인만 호출 가능해 `CANCELLED`은 항상 여행자 자발적 취소(가이드 취소 경로 없음). 가이드 취소 기능이 생기면 CANCELLED+취소주체 필드로 조건 확장.
2. 즉시예약 시간겹침 → **하드 거절**(REQUESTED 폴백 아님). 여행자가 다른 시간 선택.
3. 일정 없을 때 → **최소 1일 일정 자동 생성**(제목 "{headline} 투어").
4. ⚠️ 자동추가 아이템 `placeName`이 한국어 하드코딩("🎫 가이드 투어") — 프론트가 `tourBadge` 다국어 배지로 완화했으나 placeName 자체는 그대로. 백엔드 로케일 문구 or 프론트 완전 치환 개선 필요(IDEAS.md 백로그).

## 0. Wave 1 (2026-07-06, designer 담당, 프론트만) 요약
백엔드는 이미 구현·검증된 상태로 넘겨받음: `GET /api/posts/{id}/translate?lang=`, `GET /api/reviews/{id}/translate?lang=` (둘 다 public, 자동 언어감지, 한국어로도 번역 가능).
- 커뮤니티 게시글 "번역 보기" 토글 — `components/PostCard.tsx` **+** `guides/[id]/page.tsx` 내부 로컬 `PostCard`(별개 구현이라 둘 다 패치).
- 리뷰 "번역 보기" 토글 — `guides/[id]/page.tsx` 리뷰 목록(컴포넌트 분리 없이 인라인, id 키 Record로 상태 관리). 코멘트 없는 리뷰는 토글 숨김.
- "한국어 한마디" 빠른 문구 — `ChatRoom.tsx` 입력창 위 칩 8개. 번역 API 미사용, `i18n.ts`의 `chat.quickPhrases` **키 오브젝트**(배열 아님 — tsc가 배열 길이 불일치는 못 잡으므로 키 파리티 강제되는 오브젝트로 설계)에서 고정 문구를 꺼내 전송. ko UI는 한국어만, en/zh는 `"한국어 / 현재언어"` 병기.
- ⚠️ 이 세션도 `npx tsc --noEmit`은 코디네이터가 이어서 실행 예정 — designer가 직접 돌리지 않음, 수동 키 대조로만 확인.

---

## 1. 지금 상태 한눈에

| 항목 | 상태 |
|---|---|
| 백엔드 | Spring Boot :8080 — Java 21 필수 |
| 프론트 | Next.js :3000 |
| DB | Supabase Postgres (Sydney 리전, 왕복 ~250ms — N+1 주의) |
| 브랜치 | `main`, origin보다 3 커밋 앞섬 |
| **미커밋 변경** | **87개 파일** (지난 여러 세션분 전부 누적 — 아직 한 번도 커밋 안 함) |

**⚠️ 가장 먼저 알아야 할 것**: 디자인 개편~닉네임까지 **최근 3~4 세션 작업이 전부 미커밋 상태**다.
기능은 다 동작하고 검증도 됐지만 git에 안 올라가 있음. 아래 §4 커밋 제안 참고.

---

## 2. 이번 세션(2026-07-05)에 완료한 것

전부 백엔드 컴파일 + `tsc` + curl + Playwright 스크린샷으로 검증 완료.

1. **PeerUp 궁합 (매칭 점수)** — `MatchScore.java`. 로그인 여행자의 관심사·MBTI·언어·도시 기준 가이드별 0~99점.
   `/api/guides?lang=`에 인증 붙이면 `matchScore` 내려옴. 가이드 카드 "나와 N% 잘 맞아요" 배지 + 궁합순 정렬,
   여행자 홈 "오늘의 추천 가이드" 상위 3명. 미입력/비로그인은 null → UI 자동 숨김.
2. **온보딩 2단계 모달** — 첫 방문 시 언어 선택 직후 같은 모양 모달로 여행자/가이드 선택 (`LanguagePicker.tsx`).
   선택하면 `peerup-mode-changed` 이벤트로 랜딩 즉시 전환.
3. **커뮤니티 분리** — 홈에 섞여 있던 피드를 `/community` 독립 페이지로. 두 홈엔 그라디언트 배너 링크.
   사이드바에 👥 커뮤니티(전 역할).
4. **예약 전 1:1 메시지 (DM)** — `conversations`/`conversation_messages` 신규 테이블. 가이드 상세 "메시지 보내기" →
   `/messages` 인박스 → `/messages/[id]` 실시간 대화. `ChatRoom.tsx` 공용(예약 채팅과 공유), 번역 포함.
5. **A1 안읽음 배지** — DM 안읽은 수를 사이드바 💬에 배지(30초 폴링). 방 열면 읽음 처리.
6. **A3 DM→예약 CTA** — DM 대화방 헤더(여행자 시점)에 "예약 요청" 버튼 → 가이드 상세.
7. **B3 닉네임** — `users.nickname`. `@핸들` = 닉네임 우선, 없으면 이메일 앞부분. 가입/프로필에서 설정.
8. **✨ 코스 추천 (가이드 편의 ①)** — `/guide/courses` 상단 추천 패널. 도시+구+테마 → Kakao 장소로 동선 4~5곳
   자동 구성(`GET /api/courses/recommend`, `CourseRecommendController`). 지도 미리보기 + "이 코스로 폼 채우기".
   재호출마다 셔플. 방향: 가이드 공급 확대용 편의 기능 시리즈 시작 (다음 후보: 슬롯 반복 등록, 빠른 답장 템플릿, 대시보드 통계).
9. **B1 비밀번호 재설정 + 이메일 인증 (백엔드 + 프론트 완료)** — 백엔드는 `developer`, 프론트는 `designer` 에이전트 담당. **양쪽 다 완료.**
   - `User.emailVerified`(nullable Boolean, null=미인증) 추가. 가입 시 `EmailVerificationToken`(24h) 발급 + Resend 발송.
   - `PasswordResetToken`(1h) — `POST /api/auth/forgot-password`(계정 존재 여부 안 알려줌, 항상 같은 성공 메시지) →
     `POST /api/auth/reset-password`(토큰+새 비번, 1회용 소모).
   - `POST /api/auth/verify-email`(토큰) — 인증 완료 처리. `POST /api/users/me/resend-verification`(인증 필요) — 재발송.
     ⚠️ `/api/auth/**`는 전부 public이라 resend는 일부러 `/api/users/me/` 아래에 둠(principal null 401 방지, CLAUDE.md 규칙).
   - `ResendEmailClient`(범용 발송, `com.guidematch.email`) + `EmailService`(템플릿 계층, 향후 예약 확정 메일 등도 여기 추가하면 됨).
     발신 주소는 Resend 기본 테스트 발신자 `onboarding@resend.dev` — 커스텀 도메인 인증 전까지 **Resend 계정 소유자 본인 이메일로만 발송 가능**.
   - 미인증 사용자를 로그인/예약 등에서 하드 블록하지 않음(MVP 판단) — 프론트는 배너만 보여줌, 필요시 재검토.
   - Resend 실제 발송으로 4개 플로우(가입→인증메일, 재발송, 비번재설정 요청→링크, 재설정 완료→재로그인) curl 검증 완료.
     검증 중 만든 테스트 계정(`leekyumhi@gmail.com`, id 29, 비번 `newpass123`으로 변경됨)이 실 DB에 남아있음 — 필요시 Supabase 콘솔에서 정리.
   - ✅ **프론트 완료** — `/forgot-password`, `/reset-password?token=`(Suspense 경계 안에서 `useSearchParams`, 이 레포 최초 사례),
     `/verify-email?token=`(토큰 1회용이라 `useRef`로 effect 이중 호출 가드), 로그인 페이지 "비밀번호를 잊으셨나요?" 링크,
     `/profile` 상단 미인증 배너(`components/EmailVerifiedBanner.tsx`, `GET /api/users/me`의 `emailVerified` 사용, 재발송 버튼,
     닫기는 `sessionStorage`라 세션 단위로만 숨겨짐). `forgotPassword`/`resetPassword`/`verifyEmail`/`emailBanner` + `login.forgotLink`
     ko/en/zh 3개 언어 전부 추가. ⚠️ 이 프론트 작업 세션에는 셸 도구가 없어 `npx tsc --noEmit`을 직접 실행하지 못했음 —
     기존 로그인/회원가입/리뷰 페이지 패턴을 그대로 재사용한 수동 코드 리뷰로만 검증됨. **다음 세션에서 `cd app/frontend && npx tsc --noEmit` 먼저 재확인 권장.**

---

## 3. ⚠️ 블로커 · 주의사항 (재개 전 반드시 확인)

### 3-1. Google Translate API 키 IP 제한 (미해결, 사용자 조치 필요)
현재 회선 IP가 키 허용목록에 없어 **번역(채팅+장소명) 전부 원문 폴백** 중. 코드 경로는 정상.
- 해결: Google Cloud Console → API 키 → IP에 `2001:2d8:7431:3c57::/64` + `211.235.90.114` 추가,
  또는 Application restrictions=None + API restrictions=Translation만.

### 3-2. Hibernate `ddl-auto: update`는 additive-ONLY
- 새 nullable 컬럼 추가는 잘 됨. **NOT NULL 완화·이름 변경·타입 변경은 반영 안 됨** (실제 사고 이력 있음).
- 스키마 바꿀 땐 새 nullable 컬럼을 옆에 추가하는 방식만. 물리 테이블/컬럼명은 `@Table`/`@Column(name=)`로 고정.
- 수동 DDL이 필요하면 `psql` CLI 없음 → 스크래치 venv에 psycopg2로 접속 (creds: `app/backend/.env` `SUPABASE_DB_*`).

### 3-3. 원격 DB N+1 = 페이지 사망
Supabase 풀러가 Sydney. 목록 API에서 절대 `stream().map()` 안에 단건 쿼리 돌리지 말 것.
배치 집계(`IN (...) GROUP BY`, `findAllById`, `left join fetch`) 패턴이 이미 여러 리포지토리에 있으니 재사용.

### 3-4. 500이 401로 위장되는 함정
스키마 변경 후 POST/PATCH가 이유 없이 401 나오면 → 백엔드 로그에서 감춰진 500(`DataIntegrityViolationException`)부터 확인.
Spring `/error` 재디스패치가 Authorization 헤더를 떨궈서 JWT 진입점이 401을 반환하는 것.

### 3-5. 백엔드 재시작 시 cwd 주의
백그라운드 `gradle bootRun`은 **같은 명령 안에서** `cd .../app/backend` 해야 함 (shell cwd가 호출 간 리셋됨).
`~`에서 실행하면 "Run gradle init" 나면서 옛 서버는 죽고 8080은 빈 상태가 됨.

---

## 4. 미커밋 변경 정리 & 커밋 제안

87개 파일이 누적돼 있어 한 덩어리로 커밋하면 리뷰 불가. **기능 단위로 쪼개 커밋 권장**:

```
# .env 커밋 금지 확인 후 (.gitignore에 이미 등록됨)
1) feat: visual redesign + mode-split landing + spots     (page.tsx, globals.css, spots.ts, spots/)
2) feat: match score (PeerUp 궁합)                          (MatchScore.java, GuideSummaryResponse, GuideController, guides/traveler page)
3) feat: onboarding 2-step + community split               (LanguagePicker, community/, guide+traveler page, Sidebar)
4) feat: pre-booking DM + shared ChatRoom                  (chat/Conversation*, ChatRoom.tsx, messages/, guides/[id])
5) feat: DM unread badge + DM→booking CTA + nickname       (Conversation last_read, User.nickname, profile/signup)
6) feat: tour courses                                       (TourCourse*, guide/courses/)
7) chore: P0 fixes (알림배지/예약충돌/날짜검색/채팅번역)      (BookingService, Sidebar, ChatController 등)
8) docs: PROGRESS/IDEAS/HANDOFF
```
- ⚠️ 실제 파일-커밋 매핑은 위가 대략치. `git add -p` 또는 파일별로 확인하며 나눌 것.
- `app/SCR-20260702-kbii.png`, `app/iTerm2-Color-Schemes/`는 실수로 들어온 잡파일 → 커밋 제외(gitignore 추가) 검토.
- **사용자가 커밋을 명시적으로 요청하기 전엔 커밋하지 말 것** (지금까지 안 한 이유가 있을 수 있음 — 먼저 물어보기).

---

## 5. 실행 방법

```bash
# 백엔드 (Java 21)
cd ~/kyum_platform/app/backend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
gradle bootRun            # :8080, "80% EXECUTING"에서 멈춘 게 정상(실행 중)

# 프론트
cd ~/kyum_platform/app/frontend
npm run dev               # :3000
```
- 코드 수정 후 백엔드는 **재시작 필수**. 포트 8080은 한 번에 하나만.
- 타입체크: `cd app/frontend && npx tsc --noEmit` (반드시 frontend 디렉토리에서).
- 백엔드 컴파일만: `gradle compileJava`.

---

## 6. 다음 할 일 (우선순위) — 상세는 `IDEAS.md` "2차 아이디어"

**②번 묶음 (다음 차례)**:
- ✅ **비밀번호 재설정 + 이메일 인증 — 백엔드+프론트 완료** (위 §2-9 참고).
- ✅ **신고·차단 [안전] — 백엔드+프론트 완료, curl E2E 검증** (Wave 5 참고). 브라우저 UI만 다음 세션 확인 권장.

**이메일 없이 바로 가능한 것** (키 대기 중 병렬):
- ✅ **A2 통합 인박스 — 백엔드+프론트 완료, tsc·compileJava·브라우저 E2E 검증** (2026-07-07). `GET /api/inbox`(`chat/InboxService`)가 DM+예약채팅을 한 목록으로. `/messages`가 dm→`/messages/{id}`·booking→`/chat/{bookingId}` 분기, 예약 스레드 🎫 배지. 상세는 `app/PROGRESS.md` 최상단.
- ✅ **예약 채팅 안읽음 배지 — 완료** (2026-07-07). `Booking`에 traveler/guide_last_read_at 추가, `MessageService.history`가 방 열 때 읽음 처리, `GET /api/inbox/unread-count`가 DM+예약 합산, 사이드바 💬 배지가 이 통합 카운트를 폴링. 상세는 `app/PROGRESS.md`.
- **A4 투어 코스 예약** — 코스가 등록/노출만 되고 **예약 버튼이 없음**. 결제 붙이기 전 마지막 준비물.
- **A5 궁합 근거 표시**, **C3 게시물 permalink+공유**, **C4 환율 병기**, **C5 슬롯 KST 표기** — 각 반나절급.

**추천**: 사용자에게 이메일 키 유무를 먼저 확인 → 있으면 ②번, 없으면 A4(코스 예약)나 A2(통합 인박스)부터.

---

## 7. 검증 방법 · 테스트 자산

- **스크래치**: `/private/tmp/claude-501/.../scratchpad/` 에 Playwright 설치됨(`playwright@1.61.1`, 캐시된 chromium 사용).
  `match_token.env`에 이번 세션 토큰들 저장(`TOKEN_FOR_UI`, `GTOKEN`, `CID`) — 만료됐으면 재발급.
- **테스트 계정**: `match_test3_*@test.com`(닉네임 `seoul_lover`, ENFP+관심사), `dm_guide_*@test.com`(닉네임 없음, 가이드 프로필 id 8), `traveler_kim` 닉네임 점유됨. 비번 전부 `test1234`.
- **로그인 API 주의**: signup은 **user 객체 반환(토큰 아님)** → 이어서 `POST /api/auth/login`으로 `accessToken` 받아야 함.
- **가이드 프로필 생성 API**는 `languages` 배열 필수(비어 있으면 400) — 테스트 스크립트에 꼭 포함.
- Playwright에서 로그인 상태 만들기: `localStorage`에 `accessToken`/`userName`/`mode`/`peerup_lang` 심고 목표 라우트로 이동.

---

## 8. 재사용해야 할 핵심 컴포넌트·패턴 (중복 만들지 말 것)

| 자산 | 용도 |
|---|---|
| `components/ChatRoom.tsx` | 채팅 UI 단일 소스 — 예약채팅/DM 둘 다 wrapper로 씀. 여기만 고칠 것 |
| `components/PostCard.tsx` | 피드 게시물 카드 단일 소스 (`FeedPost` 타입 canonical) |
| `components/PostComposeModal.tsx` | 게시물 작성 모달 공용 |
| `components/CourseCard.tsx` | 투어 코스 카드 단일 소스 — `guides/page.tsx`(투어 코스 탭)·`spots/[slug]`(지역 퍼널) 공유 |
| `components/GuideCard.tsx` | 가이드 카드 단일 소스 — 가이드 목록·유사 가이드 추천·명소 퍼널 공유 |
| `User.getHandle()` (백엔드) | @핸들 단일 소스 = nickname ?? 이메일로컬파트 |
| `MatchScore.compute()` | 궁합 계산 — 추가 쿼리 없이 메모리 계산 |
| Sidebar 배지 패턴 | pending-count(예약요청) / unread-count(DM) 둘 다 30초 폴링, `Item.badge` |
| `peerup-mode-changed` 이벤트 | React 밖에서 mode 바뀔 때 랜딩에 알리는 신호 |

---

## 부록: DB에 이번에 추가된 테이블/컬럼 (ddl-auto가 자동 생성함)

- `conversations` (traveler_user_id, guide_profile_id[unique 쌍], guide_user_id, last_message_at/preview, **traveler_last_read_at, guide_last_read_at**, created_at)
- `conversation_messages` (conversation_id, sender_id, content, created_at)
- `tour_courses` (가이드 고정 코스 상품)
- `users.nickname` (nullable, unique, 20자)
- `users.gender`, `guide_posts.author_user_id` 등은 이전 세션분
