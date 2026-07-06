# 개발 진행 상황 (이어서 작업용 메모)

## Wave 4 (인앱 장소 상세) 프론트엔드 완료분 (2026-07-06 — designer, 프론트만, 새 백엔드 없음)
`/explore`(지역 둘러보기)·`/trips/[id]`(여행일정 짜기)에서 장소를 누르면 카카오맵으로 이탈하던 걸 앱 안에서 상세를 보여주도록 개선. 새 API/백엔드 변경 없이 `/api/places`가 이미 내려주던 필드(id/name/category/phone/address/latitude/longitude/placeUrl/distanceMeters)만 사용.

- **`components/PlaceDetailModal.tsx` 신규** — 이름+카테고리 헤더, 좌표가 있으면 `TripMap`(신규 컴포넌트가 아니라 기존 것을 단일 핀 배열로 재사용) 표시(없으면 안내 문구로 우아하게 생략), 주소(📍 + 주소 복사 버튼, `navigator.clipboard`), 전화(📞 `tel:` 링크), 거리(km), 명소(`SPOTS`) 매칭 시 "이 명소 자세히 보기" 주 버튼(`/spots/[slug]`로 이동), 마지막에 "카카오맵에서 열기"를 보조(ghost) 버튼으로 배치(기존엔 이게 유일한 액션이었는데 이제 fallback). Esc 키·backdrop 클릭으로 닫힘, 열릴 때 닫기 버튼에 포커스, 열려있는 동안 `document.body.style.overflow = "hidden"`로 배경 스크롤 잠금(닫을 때 원복). `onAdd`/`addLabel` optional prop을 추가해 트립 빌더에서만 모달 안에 "일정에 추가" 보조 액션을 노출할 수 있게 함(explore에서는 전달하지 않아 안 보임).
- **`matchSpot` 헬퍼 단일 소스화** — 기존에 `/explore`·`/trips/[id]` 두 페이지에 완전히 동일한 함수가 중복 정의돼 있던 걸 `lib/spots.ts`로 이동해 `export`, 두 페이지 + 모달 셋이 import해서 공유.
- **`/explore/page.tsx`** — 카드 클릭 인터랙션을 인라인 펼침(`expandedId` + 명소 이미지/카카오 링크)에서 모달 오픈(`selectedPlace` state)으로 교체. 카드 안의 "카카오맵에서 보기" 버튼은 모달 안으로 흡수돼 카드에서는 제거(같은 액션이 두 군데 있는 걸 방지), 대신 카드 우측에 `›` 화살표로 "더 보기"를 암시.
- **`/trips/[id]/page.tsx`** — 장소 검색 결과 리스트의 기존 "이름 클릭 → 즉시 일정에 담기" 동작은 그대로 보존(가장 중요한 제약이었음). 기존 ▲▼ 인라인 펼침(명소 이미지 표시용)을 "ⓘ" 버튼으로 교체해 `PlaceDetailModal`을 연다 — 별도 라우트 이동 없이 상세 확인 가능. 또한 `Place` 타입에 `phone?`/`placeUrl?`/`distanceMeters?`를 optional로 추가(백엔드는 이미 이 필드들을 내려주고 있었는데 이 페이지의 타입에만 없었음 — API 응답을 더 풍부하게 모달에 넘기기 위한 타입 확장, 런타임 동작 변경 없음).
- **판단 사항** — ① explore는 인라인 펼침을 완전히 걷어내고 모달로 전면 대체(코디네이터 지시의 "cleanest" 옵션 채택). ② trips는 "add" 버튼을 절대 건드리지 않는다는 제약 때문에 상세 보기를 별도 "ⓘ" 어포던스로 분리했고, 쉬운 범위였던 "모달 안에서도 추가 가능"까지 옵션 prop으로 추가함. ③ Kakao REST 응답에 사진/영업시간/평점이 없어 모달에 억지로 만들어 넣지 않음 — `IDEAS.md`에 Google Places Details 연동을 백로그로 기록.
- **i18n** — 신규 최상위 그룹 `placeDetail.{close,call,distance,copyAddress,addressCopied,viewSpot,openInKakao,noCoords}` + `itinerary.detailsBtn` 1개 — ko/en/zh 전부 동일 키 세트로 추가(직접 세 블록 대조 완료).
- 검증: `ModalPlace` 타입의 모든 카카오 파생 필드를 optional로 설계해 explore/trips 두 `Place` 타입이 구조적으로 그대로 대입 가능함을 코드 리뷰로 확인. `npx tsc --noEmit`은 코디네이터가 이어서 실행 예정(이 세션엔 셸 도구 없음) — ko/en/zh 키 파리티는 세 블록 직접 대조로만 확인.

## Wave 3 (콘텐츠) 프론트엔드 완료분 (2026-07-06 — designer, 프론트만)
백엔드(developer)가 이미 구현·curl 검증한 3개 계약 위에 프론트만 작업.

- **코스 상세 — 동선 지도 + 편집** — `TourCourseResponse`에 `waypoints`(sortOrder순)가 추가돼 `GET /api/guides/{id}/courses`(공개)·`GET/POST/PUT /api/guide-profiles/me/courses`(가이드용)에 함께 내려옴. `PUT /api/guide-profiles/me/courses/{id}`(수정, multipart) 신규.
  - `guides/[id]/page.tsx` "투어 코스 상품" 카드에 "동선 보기" 토글 추가 — `waypoints.length > 0`일 때만 노출, 펼치면 `TripMap`(번호 핀+폴리라인) + 순서대로 정차지 리스트(이름·카테고리·주소). 별도 코스 상세 라우트는 이 앱에 원래 없었고(코스는 `/guides`의 "투어 코스" 탭·가이드 상세에만 인라인 노출, 단건 조회 API도 없음) 새 라우트/백엔드 엔드포인트를 만들지 않고 기존 인라인 표시를 확장하는 쪽으로 판단.
  - `guide/courses/page.tsx`(코스 등록 관리)에 동선 편집 추가: 기존 "✨ 코스 추천 받기" 패널의 각 정차지에 "+ 담기" 버튼을 달아 개별로 폼의 동선에 추가할 수 있게 했고, "이 코스로 폼 채우기"는 추천 결과 전체를 동선으로 한 번에 채운다. 폼에 동선 리스트 에디터(▲▼ 순서 변경, 삭제) 추가, 제출 시 `waypoints` JSON 배열을 multipart 필드로 전송(등록·수정 모두 통째 교체이므로 비어있어도 항상 전송). **코스 수정(편집) 기능 신규 추가** — 목록의 "수정" 버튼으로 기존 코스+동선을 폼에 불러와 `PUT`으로 저장(그 전엔 삭제만 가능했음). 목록에서도 "동선 보기"로 등록된 동선을 지도+리스트로 읽기 확인 가능.
  - `lib/api.ts`의 `apiUpload`가 POST 전용이라 PUT 멀티파트를 못 보내 `apiUpload(path, formData, { auth, method })`로 `method` 옵션 추가(기본 POST, 하위 호환) — auth 로직은 건드리지 않음.
- **명소 → 가이드/코스 전환 퍼널** — `spots/[slug]/page.tsx` 하단에 "이 지역 가이드"/"이 지역 투어 코스" 섹션 신규(프론트 전용, 새 백엔드 없음). 기존 공개 엔드포인트 `GET /api/guides?city=&lang=`, `GET /api/courses?city=`를 그대로 재사용. `spots.ts`의 `spot.city.en`이 `KoreanCity`의 표준 key("Seoul"/"Busan"/"Jeju"/"Jeonju" 등)와 표기가 동일해 best-effort로 그대로 city 필터에 사용(가이드 프로필/코스의 `city` 컬럼도 같은 key 규약). 두 목록 다 비어있으면 섹션 자체를 렌더링하지 않음(에러 없이 조용히 생략). `components/GuideCard.tsx`/신규 `components/CourseCard.tsx`(코스 카드를 `guides/page.tsx`에서 처음 컴포넌트로 추출, 기존 인라인 마크업 100% 동일) 재사용.
- **리뷰 통계 — 별점 분포 + 키워드 태그** — `GET /api/guides/{id}/review-stats`(공개, `{average, count, distribution, tagCounts}`, 항상 0으로 채워짐) 소비. `guides/[id]/page.tsx` 리뷰 섹션에 5★~1★ 막대 그래프(`count > 0`일 때만 표시) + 카운트 0 초과 태그만 내림차순 칩으로 노출. 각 리뷰(`ReviewResponse.tags`)에도 개별 태그 칩 렌더. `review/[bookingId]/page.tsx`에 8개 canonical 태그(kind/punctual/knowledgeable/flexible/goodPhotos/goodFood/languageGood/funny) 다중 선택 칩 추가, 제출 시 `POST /api/bookings/{id}/review`의 `tags` 배열로 KEY만 전송(라벨은 프론트에서만 로컬라이즈).
  - 막대 너비는 인라인 style 금지 규칙 때문에 `w-[10%]`~`w-full` 같은 **리터럴** Tailwind 클래스를 10% 단위로 미리 적어둔 lookup 객체에서 골라 씀(문자열 보간으로 즉석 조합하면 Tailwind 정적 스캐너가 못 읽어 스타일이 안 먹으므로 반드시 리터럴로 존재해야 함).
- **i18n** — `courses.{routeTitle,noRoute,viewRoute,hideRoute,waypointsSection,waypointsHint,addWaypoint,addedWaypoint,removeWaypoint,moveUp,moveDown,editBtn,editTitle,cancelEdit,updateBtn,updating,stopUnit}`, `spotDetail.{localGuidesTitle,localGuidesSub,localCoursesTitle,localCoursesSub,seeAllGuides,seeAllCourses}`, `guideDetail.reviewKeywords`, `review.{tagsLabel,tagsSub}`, 신규 최상위 그룹 `reviewTags.{kind,punctual,knowledgeable,flexible,goodPhotos,goodFood,languageGood,funny}` — ko/en/zh 3개 언어 모두 동일 키 순서로 추가(`Translations = typeof t.ko` 구조적 타입이라 키 누락 시 en/zh 블록에서 타입 에러가 나므로 셋 다 반드시 대조).
- **판단(코디네이터 확인 필요)**: ① 코스 "단건 상세 페이지"는 만들지 않고 기존 인라인 표시(가이드 상세·코스 관리 목록)를 확장 — 백엔드에 단건 조회 API가 없고 코스 카드가 항상 가이드 상세로 링크되는 기존 동선을 유지하는 게 최소 변경이라 판단. 필요시 `/courses/[id]` 전용 라우트 + 백엔드 단건 GET을 별도 과제로. ② 동선 수동 입력(자유 장소 검색으로 임의 지점 추가)은 구현 안 함 — 추천 패널의 Kakao 장소만 담을 수 있음(코디네이터 지시의 "REUSE the existing place-picking UX"를 추천 패널 재사용으로 해석). ③ 명소 city→가이드 city 매칭은 `spot.city.en` 그대로 사용(6개 명소 전부 `KoreanCity` key와 완전 일치 확인 완료), 매칭 실패 시 조용히 섹션 생략.
- 검증: 코드 리뷰 기반 수동 타입 점검 + ko/en/zh 키 3블록 직접 대조(이 세션엔 셸 도구로 `npx tsc --noEmit` 미실행 — 코디네이터가 이어서 실행 권장).

## Wave 2 (예약 전환) 백엔드 완료분 (2026-07-06 — developer, 백엔드만)
- **즉시 예약(instantBooking)** — `GuideProfile.instantBooking`(nullable Boolean, 기본 false). `PATCH /api/guide-profiles/me/instant-booking` `{"instantBooking":true}`로 토글. `GuideProfileResponse`/`GuideSummaryResponse`/`GuideDetailResponse` 전부에 `instantBooking` 노출. 켜져 있으면 `BookingService.create()`가 바로 `ACCEPTED`로 확정하되, `accept()`와 동일한 시간 겹침 검사(`hasOverlapWithAccepted` 공용 헬퍼로 추출)를 통과해야 함 — 겹치면 REQUESTED로 조용히 낮추지 않고 에러("선택하신 시간에 이미 다른 예약이 확정되어 있습니다")를 던짐.
- **확정 예약 → 여행 일정 자동 추가** — `ItineraryItem.sourceBookingId`(nullable Long, 멱등성 가드) 추가. `ItineraryService.autoAddTourItem(...)`을 `accept()`(가이드 수락)와 `create()`(즉시예약 확정) 양쪽에서 호출. 여행자의 일정 중 예약 날짜(KST 기준 변환)를 포함하는 게 있으면 그 날에 `placeName:"🎫 가이드 투어"`, `category:"tour"` 아이템 추가, 없으면 해당 날짜 하루짜리 일정을 새로 만들어 추가. **REQUIRES_NEW로 별도 트랜잭션 처리 + try/catch** — 이 부가 기능이 실패해도 예약 확정 자체(원래 트랜잭션)는 절대 롤백되지 않도록 격리.
- **유사 가이드 추천** — `GET /api/guides/{id}/similar?lang=` (public, 비로그인 가능). 같은 도시 우선 + 로그인 여행자에게 궁합 점수 근거(관심사/MBTI)가 있으면 궁합순, 없으면 평점·리뷰수순으로 최대 3명. 기존 가이드 목록의 일괄 집계 로직(`GuideController.buildSummaries`로 추출해 재사용)을 그대로 써서 N+1 없음. 응답은 `GuideSummaryResponse[]` (가이드 목록과 동일한 카드 모양).
- 검증: `gradle compileJava` 통과. 컬럼 추가 로그로 additive 확인(`add column instant_booking boolean`, `add column source_booking_id bigint`). curl로 즉시예약 확정, 겹침 거부, 일반 accept() 경로 둘 다 여행 일정 자동 생성 확인, `/similar` 공개/비공개 응답 확인 완료.

## Wave 2 (예약 전환) 프론트엔드 완료분 (2026-07-06 — designer, 프론트만)
- **⚡ 즉시 예약 UI** — `guide/manage/page.tsx`에 "예약 받는 중/중단" 토글과 동일한 세그먼트 스타일로 "즉시 예약 켬/끔" 토글 추가(`PATCH /api/guide-profiles/me/instant-booking`). `guides/[id]/page.tsx`는 `guide.instantBooking`이면 헤더에 ⚡ 배지 + 예약 버튼 라벨을 "즉시 예약"/"즉시 예약하기"로 교체하고, 예약 생성 응답의 `status`가 `ACCEPTED`면 "예약이 확정되었어요!" 축하 메시지를, `REQUESTED`면 기존 "요청을 보냈어요" 메시지를 보여주는 확정 패널로 폼을 대체(리다이렉트 대신 인라인 처리, 겹침 에러는 기존 `bookingError` 경로로 표시). `guides/page.tsx` 카드에도 ⚡ 배지 노출.
- **가이드 카드 컴포넌트 추출** — 기존에 `guides/page.tsx`에 인라인으로만 있던 카드 마크업을 `components/GuideCard.tsx`로 추출(궁합 배지·⚡ 배지 포함, 동작 100% 동일). 유사 가이드 추천에서도 동일 컴포넌트를 재사용.
- **비슷한 가이드 추천 (예약 거절 시)** — `traveler/bookings/page.tsx`에서 상태가 `REJECTED`인 예약 카드 아래 `GET /api/guides/{guideProfileId}/similar?lang=` 결과를 `GuideCard`로 렌더링(예약 응답의 `guideProfileId` 필드 사용 확인 완료). `BookingService.cancel()`은 여행자 본인만 호출 가능하고(184~186행에서 `travelerId` 불일치 시 예외) 가이드가 취소하는 경로가 없어 `CANCELLED`는 항상 여행자 자발적 취소임을 코드로 확인 — 과제의 "CANCELLED by guide"에 해당하는 경로가 존재하지 않으므로 `REJECTED`에만 한정. 거절된 예약에 한해서만 지연 로딩(마운트 시 1회, 언어 변경 시 재요청).
- **취소 정책 안내** — 예약 폼(예약 확정/즉시예약 공용) 제출 버튼 위에 sky 톤 정보 박스로 고정 문구 노출("72시간 전 전액 무료, 24~72시간 50% 환불, 24시간 이내·노쇼는 환불 불가"). 백엔드 연동 없는 순수 정적 카피, ko/en/zh 3개 언어.
- **🎫 가이드 투어 일정 스타일링** — `trips/[id]/page.tsx`에서 `category === "tour"`인 아이템을 amber 톤 카드(리본 배지 "예약된 가이드 투어" + 🎫 아이콘 넘버 배지)로 구분. `TripMap`은 기존에 이미 `latitude`/`longitude` null 가드가 있어 좌표 없는 투어 아이템도 지도 렌더링을 깨지 않음을 확인(코드 변경 없음).
- **예약 폼 검증 보강** — `guides/[id]/page.tsx` 예약 제출 시 시작 시각이 현재 이후인지, 이용 시간이 1 이상의 정수인지 클라이언트에서 검증 후 에러 메시지 표시(기존에 없던 로직, 디자이너 UX 규칙 반영).
- **i18n** — `guideManage`(instantLabel/instantOn/instantOff/instantHint), `guideDetail`(instantBadge/instantBookBtn/instantBookNote/instantSendBtn/requestedTitle/requestedDesc/instantConfirmedTitle/instantConfirmedDesc/viewBookingsBtn/futureDateError/hoursError/cancellationPolicyTitle/cancellationPolicyBody), `travelerBookings.similarGuidesTitle`, `itinerary.tourBadge` — ko/en/zh 3개 언어 모두 추가, 키 세트 동일.
- **판단(코디네이터 확인 필요)** — ① 유사 가이드 추천은 `REJECTED`에만 한정(위 근거 참고, `CANCELLED`는 제외). ② `IDEAS.md`/`HANDOFF.md`는 저장소에 파일 자체가 없어 새로 생성함(기존 내용 없음 — 덮어쓴 것이 아니라 신규 작성). ③ 백엔드가 자동 추가하는 여행 일정 아이템의 `placeName`이 `"🎫 가이드 투어"`로 하드코딩돼 있어 en/zh 사용자에게도 한글로 노출됨 — 프론트에서 다국어 `tourBadge` 배지를 함께 보여줘 완화했지만, 근본 해결은 백엔드에서 다국어 placeName을 내려주거나 프론트에서 `category === "tour"`일 때 placeName 자체를 로컬라이즈드 문구로 치환하는 방식이 필요.
- 검증: 백엔드 커밋된 DTO(`GuideSummaryResponse`/`GuideDetailResponse`/`GuideProfileResponse`/`BookingResponse`) 직접 대조로 필드명 확인. `npx tsc --noEmit`는 코디네이터가 실행 예정.

## 최근 세션 (2026-07-04) 완료분
- **디자인 전면 개편** — Airbnb/Klook 스타일, 하늘색(sky→cyan→teal) 팔레트, 전 페이지(~22개) 적용
- **랜딩 모드 분리** — 첫 방문 시 여행자/가이드 선택 → 맞춤 홈, Airbnb식 검색바(도시+체크인/체크아웃+인원)
- **명소 상세 페이지** — `/spots/[slug]` 6개 명소(소개 3개 언어 + 주변 맛집·카페 카카오 연동)
- **성별** — 가입/프로필에서 선택, 가이드 카드에 배지 표시
- **인스타식 홈** — 가이드/여행자 홈 = 프로필 헤더(@아이디) + 게시물 그리드 + 커뮤니티 피드(좋아요·댓글)
- **여행자도 게시물 작성 가능** (guide_posts에 author_user_id 추가, 기존 데이터 유지)
- **성능** — N+1 제거: /api/guides 13초 → 1.4초 (원격 DB라 쿼리 왕복이 병목, 배치 집계 필수)
- **알림 배지** — 사이드바 "예약 요청"에 대기 건수 (30초 폴링)
- **예약 충돌 방지** — 겹치는 시간대 이중 수락 차단
- **날짜 검색** — 체크인~체크아웃 기간에 슬롯 있는 가이드만 필터
- **채팅 번역** — 메시지별 번역 보기 + 자동 번역 토글 (Google 자동 언어감지)
- **투어 코스 상품** — 가이드가 고정 코스 등록(`/guide/courses`) → 가이드찾기 "투어 코스" 탭 + 상세 페이지 노출

## 최근 세션 (2026-07-05) 완료분
- **PeerUp 궁합 (매칭 점수)** — 로그인 여행자의 관심사·MBTI·언어·도시 기준 가이드별 0~99점 (`MatchScore.java`, 가이드별 추가 쿼리 없이 메모리 계산, `/api/guides?lang=`에 viewer 인증 시 `matchScore` 포함). 가이드 카드에 "나와 N% 잘 맞아요" 배지 + 궁합순 정렬 칩, 여행자 홈에 "오늘의 추천 가이드" 상위 3명 섹션. 관심사·MBTI 미입력/비로그인 시 null → 배지·정렬·섹션 자동 숨김. 본인 가이드 카드에는 점수 미표시.
- **온보딩 2단계 모달** — 첫 방문 시 언어 선택 직후 같은 모양의 모달로 여행자/가이드 선택 (`LanguagePicker.tsx` 2-step). 선택하면 `peerup-mode-changed` 이벤트로 랜딩이 즉시 맞춤 히어로로 전환. 역할 단계는 비로그인 + 모드 미설정일 때만.
- **커뮤니티 분리** — 홈(가이드/여행자)에 섞여 있던 커뮤니티 피드를 `/community` 독립 페이지로 이동 (비로그인 열람 가능, 글쓰기는 로그인). 사이드바에 👥 커뮤니티 항목(전 역할), 두 홈에는 그라디언트 배너 링크로 대체 (여행자=sky, 가이드=emerald).
- **예약 전 1:1 메시지 (DM)** — `conversations`/`conversation_messages` 신규 테이블 (기존 messages는 booking_id NOT NULL이라 불변). REST `/api/conversations` (get-or-create 멱등, 인박스, 히스토리, 번역) + STOMP `/app/conversations/{id}/send` → `/topic/conversations/{id}`. 가이드 상세에 "메시지 보내기" 버튼, `/messages` 인박스(상대 이름·배지·미리보기·시각), `/messages/[id]` 대화 화면. 채팅 UI는 `ChatRoom.tsx`로 추출해 예약 채팅(`/chat/[bookingId]`)과 공유 (번역·자동번역 포함). 참여자 검증(제3자 400), 본인 DM 차단 확인.

## IDEAS 2차 묶음 ① (2026-07-05, 이어서)
- **A1 안읽음 배지** — `conversations`에 `traveler_last_read_at`/`guide_last_read_at`, `ConversationRepository.unreadCount` 단일 집계 쿼리, `GET /api/conversations/unread-count`. 대화 히스토리 열람(`history()`) 시 읽음 처리. Sidebar 💬 메시지에 안읽음 수 배지(전 역할, 30초 폴링). 본인 메시지는 미포함, 열람 시 0으로 클리어 확인.
- **A3 DM→예약 CTA** — `ChatRoom`에 선택적 `headerAction` prop 추가. DM 대화방에서 상대가 가이드일 때(내가 여행자)만 헤더에 "예약 요청" 버튼 → 가이드 상세로.
- **B3 @핸들→닉네임** — `users.nickname` (nullable, unique length 20). `User.getHandle()` = 닉네임 우선, 없으면 이메일 로컬파트. `PATCH /api/users/me/nickname` (3~20자 영문·숫자·밑줄, 대소문자 무시 유니크, 빈값이면 해제) + 가입 시 선택 설정. `UserResponse`에 nickname/handle 추가. 프로필 페이지 인라인 편집기, 가입 폼 @아이디 필드, 홈 헤더는 `me.handle` 사용. 형식/중복/폴백/가입 모두 curl 검증.

## 가이드 편의 ① 코스 추천 (2026-07-05, 이어서)
- **✨ 코스 추천 받기** — 가이드가 도시(+구)와 테마(믹스/핵심명소/맛집/카페/역사문화/전통시장)를 고르면 Kakao 장소 데이터로 걷기 좋은 4~5곳 동선을 자동 구성. `GET /api/courses/recommend?city=&district=&theme=&lang=` (인증 필요, `CourseRecommendController.java` in geo 패키지). greedy nearest-neighbor + 가까운 상위 3곳 중 랜덤 선택이라 재호출마다 다른 코스("다시 추천"). 구간/총 이동거리 + 예상 소요시간(정차 40분 + 도보 4km/h) 계산. lang≠ko면 장소명·카테고리 번역(캐시 우선, 카테고리는 Kakao 전체 경로 중 마지막 segment만).
- **프론트** — `/guide/courses` 상단에 추천 패널: CitySelect+DistrictSelect+테마 칩 → TripMap 미리보기(번호 핀+폴리라인) + 정차지 리스트(카테고리·주소·구간 도보거리) → "이 코스로 폼 채우기"가 등록 폼에 제목("성동구 카페 투어" 식, 현재 언어 지역 라벨)·소개(동선 목록)·도시·소요시간 자동 입력 후 스크롤. i18n `courses.rec*` 3개 언어.
- 검증: 컴파일+tsc, curl(테마별/셔플/비로그인 401/en 번역), Playwright 스크린샷(추천 결과 지도+리스트, 폼 자동 채움).

## B1 비밀번호 재설정 + 이메일 인증 — 백엔드만 (2026-07-05, 이어서)
- **엔티티** — `User.emailVerified`(nullable Boolean, ddl-auto additive-only라 null=미인증으로 취급, 기존 회원 전부 미인증 상태로 시작).
  `EmailVerificationToken`(24h 만료), `PasswordResetToken`(1h 만료) — 둘 다 `consumedAt`으로 1회용 소모, `Conversation`/`TranslationCache`와 같은 보조 테이블 패턴.
- **이메일 발송** — `com.guidematch.email.ResendEmailClient`(범용 발송, GoogleTranslateClient와 동일 모양: 키 없으면 스킵) +
  `EmailService`(인증/재설정 템플릿 계층 — 예약 확정 메일 등 향후 알림도 여기 메서드만 추가하면 재사용 가능).
  발신자는 Resend 기본 테스트 주소 `onboarding@resend.dev`(커스텀 도메인 인증 전까지 계정 소유자 본인 메일로만 발송 가능).
- **API** — `POST /api/auth/verify-email`, `POST /api/auth/forgot-password`(계정 존재 여부 노출 안 함, 항상 동일 성공 메시지),
  `POST /api/auth/reset-password`, `POST /api/users/me/resend-verification`(인증 필요 — `/api/auth/**`는 permitAll이라 principal null 401을 피하려고 `/api/users/me/` 아래 배치).
  `UserResponse`/`GET /api/users/me`에 `emailVerified` 추가.
- **이메일 발송 위치** — 계정 생성 트랜잭션과 분리(발송 실패가 가입 자체를 막지 않게). `resetPassword`/`verifyEmail`만 `@Transactional`(이메일 발송 없음).
- **검증** — 컴파일 + Resend 실제 발송 curl(가입→인증메일 발송 확인, 재발송, forgot-password 존재/미존재 이메일 동일 응답, reset-password 유효/무효/재사용 토큰, 재설정 후 재로그인). 4개 플로우 전부 실 이메일로 확인 완료.
- **판단(사용자 확인 필요)** — 미인증 사용자를 로그인/예약에서 하드 블록하지 않음(MVP는 배너만). 필요시 뒤집을 것.

## B1 프론트 완료 (2026-07-05, 같은 세션 이어서 — designer)
- **`/forgot-password`** — 이메일 입력 → `POST /api/auth/forgot-password` → 계정 존재 여부와 무관하게 항상 같은 성공 메시지만 표시.
- **`/reset-password?token=`** — Next.js 15 App Router라 `useSearchParams`는 `<Suspense>` 내부 클라이언트 컴포넌트에서 읽음(이 레포에서 쿼리 파라미터 읽는 첫 페이지). 새 비밀번호+확인 입력, 클라이언트에서 일치·8자 이상 검증 후 `POST /api/auth/reset-password`. 성공 시 확인 화면 → 2.5초 후 `/login` 자동 이동. 토큰 누락/만료/재사용 에러는 전용 에러 카드 + `/forgot-password`로 돌아가는 링크.
- **로그인 페이지** — 비밀번호 입력 아래 "비밀번호를 잊으셨나요?" 링크 → `/forgot-password`.
- **`/verify-email?token=`** — 진입 시 자동으로 `POST /api/auth/verify-email` 호출, 성공/실패 화면. 토큰이 1회용이라 React 18 dev StrictMode의 effect 이중 호출로 재요청되지 않도록 `useRef` 가드 적용.
- **미인증 배너** — `components/EmailVerifiedBanner.tsx`, `/profile` 페이지 상단에 배치(로그인 사용자 대상 가장 안전한 단일 위치). `GET /api/users/me`의 `emailVerified`가 false면 노출, "인증 메일 재발송" 버튼(`POST /api/users/me/resend-verification`) + 닫기. 닫기는 `sessionStorage`에 저장해 같은 세션 동안만 숨김(새 세션에서는 다시 노출).
- **i18n** — `forgotPassword`/`resetPassword`/`verifyEmail`/`emailBanner` + `login.forgotLink` 전부 ko/en/zh 3개 언어 추가.
- **검증** — 코드 리뷰 기반 수동 타입 점검(이 세션 환경에 셸 도구가 없어 `npx tsc --noEmit` 직접 실행 불가 — 다음 세션에서 꼭 재확인 권장). 기존 로그인/회원가입/리뷰 페이지의 폼·카드 스타일, `api()` 래퍼, `t()` 패턴을 그대로 재사용.

## Wave 1 (번역·소통) 완료분 (2026-07-06 — designer, 프론트만)
- **커뮤니티 게시글 번역 보기** — `components/PostCard.tsx`(커뮤니티 피드·`/guides` 게시글 탭 공용 canonical 카드)에 채팅과 동일한 "번역 보기" 토글 추가. `GET /api/posts/{id}/translate?lang=` (public, 인증 불필요) 호출, 결과는 컴포넌트 state에 캐시해 재토글 시 재요청 안 함. **`guides/[id]/page.tsx` 안에 있는 별도 로컬 `PostCard`(가이드 상세의 "게시글" 탭 전용, `components/PostCard.tsx`와 별개 구현)에도 동일하게 적용** — 코디네이터 지시엔 "shared component 하나만 고치면 된다"고 되어 있었지만 실제로는 두 곳에 중복 구현이 있어 둘 다 패치함 (외국인 여행자가 가이드 프로필에서 읽는 게시글도 번역 대상이라 판단).
- **리뷰 번역 보기** — `guides/[id]/page.tsx` 리뷰 목록(인라인 렌더, 별도 컴포넌트 없음)에 리뷰별 번역 토글 추가. `GET /api/reviews/{id}/translate?lang=`. `comment`가 없는 리뷰(별점만)는 토글 자체를 숨김.
- **한국어 한마디 (채팅 빠른 문구)** — `ChatRoom.tsx` 입력창 위에 가로 스크롤 칩 8개(여기예요!/10분 늦어요/곧 도착해요/어디세요?/잠시만요/감사합니다!/출발할게요!/곧 뵐게요). 번역 API 호출 없이 **고정 문구**를 `i18n.ts`의 `chat.quickPhrases` 키 오브젝트(배열이 아님 — tsc가 키 누락을 못 잡는 배열 대신 키 파리티가 강제되는 오브젝트로 설계)에서 꺼내 전송. 전송 포맷: UI 언어가 ko면 한국어만, en/zh면 `"한국어 / 현재언어"` (예: en 사용자가 "Running 10 min late" 탭 → `"10분 늦어요 / Running 10 min late"` 전송). 예약 채팅(`/chat/[bookingId]`)과 DM(`/messages/[id]`)이 `ChatRoom`을 공유하므로 양쪽 다 자동 적용.
- i18n: 새 키 없음(번역 토글 라벨은 기존 `chat.translateBtn`/`hideTranslation`/`translating` 재사용) + `chat.quickPhrasesLabel`, `chat.quickPhrases.{here,late,almostThere,whereAreYou,oneMoment,thankYou,letsGo,seeYouSoon}` 전부 ko/en/zh 추가.
- 검증: `npx tsc --noEmit`은 코디네이터가 별도 실행 예정(이 세션 담당 범위 아님) — 키 파리티는 세 언어 블록 모두 동일 순서로 직접 추가해 수동 대조 완료.

## ⚠️ 미해결 (다음 세션 시작 시 확인)
- **Google Translate API 키 IP 제한** — 현재 회선 IP가 허용 목록에 없어 403 → 번역(채팅+장소명) 전부 원문 폴백 중.
  해결: Google Cloud Console → API 키 → IP에 `2001:2d8:7431:3c57::/64` + `211.235.90.114` 추가, 또는 Application restrictions=None + API restrictions=Translation만 허용.
- 아이디어 백로그는 리포 루트 `IDEAS.md` 참고 (매칭 점수, 결제, 리뷰 사진 등)

## 완료된 것
- **1. 데이터 모델 설계 (ERD)**
- **2. 프로젝트 뼈대** — Next.js(3000) + Spring Boot(8080) + Supabase(Postgres + Storage)
- **3. 회원가입·로그인** — Spring Security + JWT
- **4. 가이드 프로필 등록** — 프로필 + 언어 + 자격증 파일 업로드 + 프로필 사진(아바타). 화면: `/become-guide`, `/guide/manage`
- **5. 가이드 검색·목록·상세** — 공개 `GET /api/guides`, `/api/guides/{id}`. 화면: `/guides`, `/guides/[id]`
- **6. 매칭/예약** — 예약 생성/수락/거절/취소. 시급 스냅샷 + 권한 체크. 화면: 상세 페이지 예약 폼, `/traveler/bookings`, `/guide/requests`
- **모드 분리** — 로그인 후 `/select-mode` → `/traveler`(여행자) / `/guide`(가이드) 대시보드. 비로그인 둘러보기 유지(예약 시점에만 로그인 요구)

- **7. 실시간 채팅** — Spring WebSocket(STOMP) + JWT 인증. 예약 단위 1:1 채팅. 화면: `/chat/[bookingId]`. (이전 폴링 방식 → WebSocket으로 교체 완료)
- **8. 리뷰** — 예약 완료 처리(`PATCH /api/bookings/{id}/complete`) → 여행자 리뷰 작성(`POST /api/bookings/{id}/review`) → 가이드 목록/상세에 평균 별점·리뷰 표시. 화면: `/review/[bookingId]`

## 다음에 할 것 (결제 — 별도 큰 과제)
- **결제·정산** — 사업 준비(통신판매중개업 신고, 정산·환불 정책) 먼저 → Stripe Connect + 에스크로 구조
- (그 외 다듬을 거리) 예약 일정 충돌 방지, 알림, 검색 필터(언어), 가이드 활동 on/off 토글 UI 등

## 실행 방법 (다음 세션 시작 시)
1. 백엔드: 새 터미널 → `cd ~/kyum_platform/app/backend` → `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` → `gradle bootRun`
2. 프론트: 새 터미널 → `cd ~/kyum_platform/app/frontend` → `npm run dev`
3. 브라우저: http://localhost:3000

## 주의사항 (배운 것)
- 코드 수정 후엔 **백엔드 재시작** 필수 (안 하면 변경 반영 안 됨)
- 백엔드는 **한 번에 하나만** (포트 8080 충돌 주의)
- `gradle bootRun`이 `80% EXECUTING`에 머무는 건 **정상** (서버 켜진 상태)
- curl 토큰 변수(`$TOKEN`)는 **같은 터미널 안에서만** 유효

## TODO (출시 전 보완)
- `credentials` 버킷을 공개 → **비공개 + 서명URL** 방식으로 변경 (자격증은 민감 정보)
- Supabase 키 재발급(rotate) 검토
- JWT 저장을 localStorage → httpOnly 쿠키 고려
- 채팅 확장 시: 백엔드 여러 대 → 외부 메시지 브로커(Redis/RabbitMQ) 도입, 유휴 연결 타임아웃
