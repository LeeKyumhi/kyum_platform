# 개발 진행 상황 (이어서 작업용 메모)

## 위시리스트 페이즈 1 — 찜 저장 (2026-07-19~20, 브랜치 `feat/wishlist`, 백엔드+프론트, 빌드·테스트·브라우저 스모크 7/7 통과, ⚠ main 미머지)

여행자가 가이드·코스·장소를 ♡ 저장 → `/saved` 3탭에서 재열람 + 저장수 소셜 프루프 + "이 코스 따라하기". 스펙 `docs/superpowers/specs/2026-07-19-wishlist-design.md`, 플랜 `docs/superpowers/plans/2026-07-19-wishlist.md`, 태스크 원장 `.superpowers/sdd/progress.md`. Subagent-Driven Development(태스크당 구현자→리뷰→커밋), 총 14커밋 `e4b1b7e..0aa4efe`.

- **백엔드(신규 `com.guidematch.saved` 패키지)**: 다형성 `saved_items` 테이블(GUIDE/COURSE=refId 참조, PLACE=placeRef+저장시점 스냅샷 — SPOTS slug 또는 `kakao:{id}`), `SavedItemService`(idempotent 저장/해제, 목록 배치 조립 — 원격 DB N+1 금지 준수, 사라진 가이드·비활성 코스 조용히 제외), REST 5개(`POST/DELETE/GET /api/saved`, `/ids`, `/counts` — counts만 public·SecurityConfig 등록). **이 레포 최초 `src/test`**: Mockito 15테스트(+build.gradle `junit-platform-launcher` testRuntimeOnly — Gradle 9 필수).
- **프론트**: `lib/saved.ts`(ids 공유 캐시 = loadCities 패턴, `SAVED_CHANGED_EVENT`), `SaveButton`(낙관적 토글+롤백, 비로그인→/login), 부착점 4곳(GuideCard·CourseCard·explore 카드 인라인·spots 히어로 오버레이 — spot은 `name.ko` 고정 저장), `/saved` 3탭 페이지+사이드바 "저장됨"(데스크탑 여행자만, 모바일 5탭 무변), `lib/followCourse.ts`(POST+PUT 2호출로 코스 블록 1개짜리 새 일정 — **dayIndex는 1**, 빌더 일차 1-인덱스), 가이드/코스 카드 ♥N 뱃지(`fetchSaveCounts` 공개 배치).
- **검증**: 태스크별 리뷰 전부 클린 + `gradle build -x bootJar` SUCCESS + `npm run build` 성공(/saved 라우트) + **Playwright 브라우저 스모크 7/7 PASS**(비로그인 리다이렉트/새로고침 유지/3탭/해제 즉시 반영/따라하기 빌더 블록/공개 뱃지/en·zh). 스모크가 플랜 코드 버그 1건 발견→수정(f59e5a2: followCourse dayIndex 0→1, 0이면 블록 영구 미표시) 후 재검증 PASS. 최종 브랜치 리뷰(opus): "With fixes" → 유일 필수건(유니크 제약 설명 부정확 — NULLS DISTINCT라 전 타입 비실효, 중복 방지는 서비스 레벨 단독) 정정 커밋(0aa4efe) 완료.
- **백로그(최종 리뷰 triage — 전부 머지 비차단)**: ① `/api/saved/counts` public+ids 무제한+`(item_type,ref_id)` 인덱스 없음(시퀀셜 스캔 — v1 볼륨 허용, 성장 시 ids 상한+인덱스) ② 서비스 레벨 exists→save TOCTOU(Follow 패턴 동일) ③ guides 페이지 ♥뱃지는 로드 시 1회 조회(♡ 토글해도 즉석 갱신 안 됨) ④ followCourse PUT 실패 시 고아 빈 일정 ⑤ `/saved` fetch 실패=빈상태 구분 불가 ⑥ 해제 시 stale row 간헐(1/5, 이벤트 refetch 의존) ⑦ SaveButton aria-label 정적 "save".
- **DB 오염**(실 dev DB, 콘솔 정리 대상): users 9·10(`wish_smoke_*@test.com`/test1234), guide profile 4, tour course 1, itinerary 7(버그 재현 잔재·dayIndex 0 아이템)·8, saved 4건. 상세는 `.superpowers/sdd/task-10-smoke-report.md`.
- **미머지**: `feat/wishlist` 브랜치에만 존재. push 안 함. 머지는 사용자 결정 대기.

## 내부 페이지 프리미엄 폴리시 — Tier 1 (2026-07-19, 프론트 전용, tsc·lint·브라우저 스크린샷 검증 통과, 미커밋)

리브랜딩 3커밋(`ab129e0`~`62fc880`) 후속 — 랜딩에만 있던 프리미엄 어휘를 여행자 핵심 퍼널로 확장. 스펙 `docs/superpowers/specs/2026-07-19-interior-page-polish-design.md` (실사 결과 반영해 1차안 축소: 스켈레톤·카드위계·빈상태는 대부분 이미 양호, 진짜 갭 = 헤더 불일치·fade-up 전무·companions 미완성).

- **신규 공유 컴포넌트 2개**: `PageHeader`(accent bar+title+subtitle+back/action, sky|emerald — guides·companions에 손으로 쓰던 마크업 추출) / `EmptyState`(카드+gradient 아이콘 타일+title/message/CTA — trips·explore·traveler-bookings의 3중복 마크업 추출). **새 페이지 헤더·빈상태는 반드시 이 둘을 쓸 것.**
- **적용**: guides(헤더+빈상태 3곳), companions(헤더+`…`로딩→스켈레톤+bare-p 빈상태→EmptyState), explore(헤더+장소카드 `card-hover`+빈상태 2곳), trips, traveler/bookings + fade-up: bookings/[id]·profile·trips/[id]·ProfileDetailView 컨테이너. explore 미사용 `Link` import 제거.
- **원칙 유지**: 신규 i18n 0, admin 제외, 백엔드 무변경, 순수 프레젠테이션.
- **검증**: tsc 0 · lint 클린(기존 explore 경고 1건 외) · Playwright 스크린샷 8장 눈 확인(guides ko/en, companions, explore, trips, traveler/bookings, profile, guides/[id]) — 페이지 에러 0, en 렌더 정상.
- **잔재**: 테스트 계정 `polish_t_1@test.com`(user id 8) dev DB에 생성됨.
- **다음**: Tier 2(가이드 사이드 5페이지) → Tier 3(auth/유틸) 각각 별도 스펙으로.

## 트랙 월드 분리 — 진입 게이트 + 법적 안내 (2026-07-18, 브랜치 `feat/track-worlds`, tsc·lint 통과, ⚠ 브라우저 E2E 미실행)

투트랙 재편(아래 섹션)의 후속 — 사용자가 **진입하자마자** 세계를 선택. 스펙 `docs/superpowers/specs/2026-07-18-track-worlds-design.md`, 플랜 `docs/superpowers/plans/2026-07-18-track-worlds.md`. 프론트 전용, 백엔드 무변경.

- **`lib/track.ts`** (mode.ts 미러) — `Track = companion | tour` localStorage + 투어 법적 동의(`tourLegalAckAt`) + `peerup-track-changed` 이벤트.
- **`TrackGate`** (layout 상시 마운트) — 트랙 미선택 시 전체화면 세계 선택(z-90, LanguagePicker 아래라 첫 방문 = 언어→역할→세계). 투어 선택/딥링크 시 §38 법적 고지 동의 게이트(1회 기억, 체크박스+동의). `/companions*`·`/guides*` 딥링크는 암묵 트랙 설정(chooser 억제로 깜빡임 방지). 제외: /legal·인증 플로우·/select-mode.
- **Sidebar 트랙 인지** — 동행 세계: /guides·투어코스 메뉴 제거, 투어 세계: /companions 제거 + ⚖️ /legal 링크. 모바일 find 탭 → 현 세계 목록 직행. 상시 "⇄ 다른 서비스 보기"(데스크탑 rail + 모바일 top bar) = clearTrack + 홈.
- **동행 전용 랜딩 `CompanionLanding`** — 투어·가이드·명소 흔적 0 (히어로+카테고리 타일+여행일정/탐색 카드+파트너 되기 배너 → /become-guide?license=no). track=companion이면 랜딩이 이걸 렌더.
- **흡수** — TrackEntryCards 삭제(랜딩·여행자홈에서 제거), /find는 clearTrack 후 홈 리다이렉트(세계 전환 딥링크), trips 일차 CTA → /companions 직행.
- **`/legal`** — 상시 법적 안내 페이지(법적 근거·불법 행위·허용 동행·플랫폼 정책, ko/en/zh).
- **검증**: tsc 0 · next lint 클린(기존 explore 경고 1건 외) · 동행 파일 '가이드' 스윕 클린(코드주석 메타 설명 2건은 예외 규칙대로 유지). ⚠ 브라우저 E2E 미실행 — 머지 전 수동 스모크(첫방문 3단 온보딩, 투어 법적 게이트 동의/뒤로, ⇄ 전환, 딥링크) 권장.

## 투트랙 유저플로우 재편 — 법규 대응 프론트 IA (2026-07-17 스펙 / 2026-07-18 구현 완료, 브랜치 `feat/two-track`, tsc·compileJava 통과, ⚠ 브라우저 E2E 미실행)

2026-07-13 백엔드 게이팅(위 섹션)의 **프론트 IA 완성편**. 관광진흥법 §38에 맞춰 앱을 **인증 가이드 투어** / **동행 파트너** 두 트랙으로 사용자가 오인 없이 분리. 자격증 보유자는 투어+동행 둘 다, 무자격자는 동행만. 스펙 `docs/superpowers/specs/2026-07-17-two-track-userflow-design.md`, 플랜 `docs/superpowers/plans/2026-07-17-two-track-userflow.md`, 태스크별 상세 원장 `.superpowers/sdd/progress.md`.

**구현 방식**: Subagent-Driven Development(태스크당 신규 sonnet 구현자 → 코디네이터 리뷰 → 커밋). 16개 태스크(T1 booking.request_details 컬럼 → T16 문서). 브랜치 `feat/two-track`, 체크포인트 `ecd4c79`(기존 미커밋 105파일 격리) 위에 태스크별 커밋.

**완료 항목 (커밋 순)**:
- **백엔드(additive)**: `Booking.request_details` nullable text 컬럼(불투명 저장, 2000자 상한) + DTO 왕복(T1). 예약 자동추가 일정 라벨을 serviceCategory 기반 일반화(동행="🏥 병원 동행" 등 category="companion", 투어 유지)(T2).
- **i18n**: 신규 그룹 `tracks/find/companions/companionBooking/onboardingFork` ko/en/zh(T3).
- **진입/네비**: `TrackEntryCards`(랜딩·여행자홈), `/find` 허브, 사이드바 투어/동행 분리 + 코스메뉴 VERIFIED 게이팅(T4-6).
- **목록**: `/guides` 투어 전용화(TOUR_GUIDE 고정, 배지 설명 시트), `/companions` 신규 + `GuideCard` track 변형(T7-8).
- **상세**: `guides/[id]` 1001행을 `ProfileDetailView`(track prop)로 이관 + `/companions/[id]` 래퍼. 코스섹션 게이팅·예약셀렉트 트랙스코프·반대트랙 리다이렉트·겸업 교차링크(T9).
- **동행 예약**: `companionRequest.ts`(카테고리별 요청 필드→JSON) + 예약위젯 폼 + `RequestDetailsBlock`(예약상세·가이드요청 렌더)(T10-11).
- **온보딩**: become-guide Step 0 자격 분기(`hideTour`, `?license` 딥링크, 인증신청 리다이렉트)(T12). 파트너홈 서비스 미선언 배너(T13).
- **여행일정 통합**: 일차 탭 "🤝 동행 찾기" CTA(`/find`) + 동행 아이템 sky 톤(T14).
- **카피 소탕**: 동행 컨텍스트 '가이드/Guide/导游' 제거(공유 폼 submitBtn·플레이스홀더·취소정책 중립화, 인증 가이드 교차참조는 유지)(T15).

**검증 방법**: 태스크마다 신규 구현자 커밋 → 코디네이터가 커밋 diff·통합 `tsc --noEmit`(0)·`next lint`·백엔드 `gradle compileJava`(SUCCESS)로 리뷰. 순수 이관(T9 ff668ee)은 옛 파일과 byte-identical 확인. T15 후 프론트 tsc 0 / 백엔드 compileJava 성공 / next lint는 기존 `explore/page.tsx:66` 경고 1건 외 클린.

**⚠ 남은 판단·미검증**:
- **브라우저 E2E 전면 미실행** — dev 서버 부재(작업 중 :3000 종료됨)로 Playwright 미실행, 정적 검증만. 머지 전 `npm run dev`로 수동 스모크 필요(특히: 겸업 인증가이드로 `/guides/{id}`·`/companions/{id}` 양쪽, 동행전용 파트너의 `/guides/{id}`→`/companions/{id}` 리다이렉트, become-guide 자격 분기 3언어).
- **최종 triage(minor)**: 동행 수동시간입력 폼에서 텍스트 필드 Enter 시 예약 조기 제출 가능(슬롯 경로는 무관). onKeyDown 가드 여부 결정 필요.
- **DB 오염**: 실 dev DB에 테스트 계정 2 + guide profile id 1(VERIFIED+TOUR_GUIDE,DINING_COMPANION로 변조) + 예약 몇 건 남음(T1-2 런타임 검증 잔재).
- **아직 미머지**: `feat/two-track` 브랜치에만 존재. main 머지 안 함.

## 법률 컴플라이언스 트랙 — 관광진흥법 대응 (2026-07-13 — 백엔드+프론트, gradle compileJava·tsc --noEmit 통과, ⚠ end-to-end 미실행)

계기: `창업 체크리스트/startup-platform-business-guide.md`. 외국인 대상 유상 관광안내는 **관광진흥법 제38조상 관광통역안내사 자격 필수**(무자격 시 제86조 과태료 150/300/500만). 전략 = **C 하이브리드**(자격 보유=관광, 무자격=비관광 동행만). 관광 카테고리 = **1개 `TOUR_GUIDE`로 통합**(사용자 결정). **모든 신규 엔티티/컬럼 nullable**(ddl-auto 추가형, 기존 행 안전).

- **위치정보법 회피 — GPS 개인위치 수집 제거**: `CitySelect` GPS "내 위치" 버튼·`detect()` 삭제(도시 드롭다운만), `GeoController`(`/api/geo/reverse`)+`KakaoLocalClient.reverseGeocode`/`KakaoRegion` 삭제, `GuideController` nearLat/nearLng 죽은 거리정렬 제거, SecurityConfig `/api/geo/**` 제거, i18n useMyLocation/geo* 6키 제거(3언어). **남김**: GuideProfile/User lat·lng(도시 중심좌표일 뿐 기기GPS 아님), TripMap/PlaceController(공개 POI), 채팅 "내 위치 보내기"(보류 결정). → 앱이 개인 GPS를 안 받으므로 위치정보사업 신고 부담 완화.

- **Phase 1 — 진짜 자격 인증 + 어드민 게이트** (가짜 배지 제거가 핵심): 기존 배지가 `credentials.length>0`(파일 1개면 누구나 "인증")이던 걸 **관리자 승인 상태**로 교체. 공식 3자 검증 API 없음(Q-net 수동 조회뿐) → 관리자 수동 승인.
  - 어드민 인프라(신규, 이전엔 role 개념 전무): `UserRole{USER,ADMIN}`, `User.role`, JWT `role` 클레임, `JwtAuthenticationFilter`가 role=ADMIN이면 `ROLE_ADMIN` 부여, SecurityConfig `/api/admin/**` `hasRole("ADMIN")`. **부트스트랩: DB에서 `UPDATE users SET role='ADMIN'` 1회 + 반드시 재로그인**(기존 토큰엔 role 클레임 없음).
  - 인증 모델: `CredentialType.TOUR_GUIDE_LICENSE`, `VerificationStatus{NONE,PENDING,VERIFIED,REJECTED}`, `GuideVerification`(가이드당 1행: legalName·자격증번호·발급일·내지번호·자격증/신분증 경로·status·rejectReason·reviewedBy/At), `GuideProfile.verificationStatus`(비정규화). 신청 `POST/GET /api/guide-profiles/me/verification`(guide/manage 섹션, 재신청 가능). 어드민 `admin/AdminVerification*` + `app/admin/verifications` 페이지(승인/반려).
  - **신분증·자격증 = 비공개 저장(PII)**: `SupabaseStorageClient.uploadPrivate`(경로만 반환)+`createSignedUrl`(10분). 신규 config `supabase.storage.verification-bucket`(기본 `verification`). ⚠ **Supabase에 "verification" 버킷을 private로 생성해야 함.** 어드민만 서명 URL로 열람.

- **Phase 2 — 관광/비관광 완전 분리** (서버 3중 게이팅): `guide/ServiceCategory`(enum + `requiresGuideLicense`; TOUR_GUIDE=true, 병원동행/식사/카페/쇼핑통역/언어교환=false; 야간·운송 제외). 데이터: `GuideProfile.serviceCategories`(interests 미러), `TourCourse.serviceCategory`, `Booking.serviceCategory`. **게이팅 3지점**: ① `GuideProfileService.create`+`updateServiceCategories` ② `TourCourseService.create/update` ③ `BookingService.create`(카테고리 필수+자격검증+가이드가 실제 제공하는지 검증). `GuideController.list`에 `category` 필터. 프론트: `lib/serviceCategories.ts`, `ServiceCategoryPicker`(관광은 미인증 시 자물쇠), become-guide/guide-manage 제공서비스 섹션, guide-courses 카테고리 셀렉터, `guides/[id]` 예약 위젯 서비스 선택, `guides/page` 카테고리 칩 필터, `GuideCard` "인증 가이드" 배지+서비스 태그. i18n `serviceCategories`. **⚠ 하드 컷오버: 기존 가이드는 serviceCategories 선언 전까지 예약 불가, 기존 코스(null 카테고리)는 공개 탐색서 숨김** — 가이드가 로그인해 서비스 선언·코스 재분류 필요(의도된 마이그레이션).

- **Phase 3 — 계정·자격 대여 방지**: #3 `bookings/[id]`에 가이드 아바타 노출 + "다른 사람이 나왔어요" 신고(진행중 예약, `BookingResponse.guideAvatarUrl` 추가; `SafetyService`에 `IMPERSONATION` 사유·`BOOKING` 대상 추가). #4 become-guide 호스트 약관 동의 섹션(대여금지·비관광 관광안내금지·명의자 책임 + 필수 체크박스; `GuideProfile.hostTermsAgreedAt`, `create()`서 미동의 거부). #1 신원결박은 Phase 1 신분증 이름일치로 반영. **#2 실명계좌 정산은 결제 단계에서**(미구현).

- **어드민 신고 검토 페이지**(detect→act 루프 완성): `admin/AdminReport*`(`/api/admin/reports` OPEN 목록/review·dismiss). `Report.markReviewed()/dismiss()`(OPEN→REVIEWED/DISMISSED), `findByStatusOrderByCreatedAtDesc`. listOpen은 신고자+BOOKING 대상 가이드 이름 배치 enrich(사칭 조치용). `app/admin/reports` 페이지 + `/admin/verifications`와 상호 링크. i18n `adminReports`. (BOOKING 외 대상은 type+id만, 참여자 검증 없음 — 기존 report 패턴.)

- **실사용 전 남은 것(코드 아님)**: ① **결제/정산(#2)** — PG 지급대행 계약 후. 이때 통신판매중개자 고지도. ② **변호사 약관 검토** — hostTerms/verification 문구는 "법률 검토 필요" 초안. ③ **end-to-end 테스트** — 어드민 승격+재로그인, verification 버킷 private 생성 후 3개 Phase 전체 구동. ④ 어드민 접근은 직접 URL(사이드바 메뉴 없음). ⑤ 이 세션에 **DB 전체 초기화 SQL 제공**(`TRUNCATE ... RESTART IDENTITY CASCADE`, 23개 테이블, translation_cache 제외) — 사용자가 Supabase SQL 에디터에서 실행 예정, 스토리지 파일은 별도.
- 상세: 프로젝트 메모리 "법률 컴플라이언스 트랙" 섹션 + 계획 파일 `~/.claude/plans/prancy-conjuring-kay.md`.

## 코드 정리 패스 (/simplify, 2026-07-07 — 4에이전트 리뷰 후 적용, tsc·lint·compileJava·스모크 통과)
미커밋 전체(59파일)를 재사용/단순화/효율/설계깊이 4관점으로 리뷰 후 동작 무변경 정리. **재사용해야 할 새 단일 소스들**:
- **`lib/i18n.ts`**: `localeOf(lang)`(lang→BCP-47, 기존 삼항 11곳 교체 — 언어 추가 시 여기만), `QUICK_PHRASE_KEYS`(ChatRoom·예약상세 공유).
- **`lib/bookingStatus.ts`**(신규): `STATUS_CLS`/`STATUS_ICON` — 예약 상태 배지 3페이지 공유.
- **`lib/useModalDismiss.ts`**(신규): Esc+body 스크롤락 훅 — 모달 5곳(PlaceDetail/PlacePicker/SharePicker/PlanPreview/CoursePreview) 공유. 새 모달은 이걸 쓸 것.
- **`lib/usePolledCount.ts`**(신규): 30초 폴링+경로변경 refetch 배지 훅 — Sidebar 배지 3종이 1줄씩. 4번째 배지도 이걸로.
- **백엔드**: `BookingService.assertParticipant` public화 = 예약 접근 규칙 단일 소스(MessageService 위임, 자체 복사본 삭제). `chat/Previews.clip()` = 인박스 프리뷰 80자 규칙 단일 소스.
- **효율**: `BookingService.toResponses()` 배치(목록 N+1 제거: 예약 M건당 ~2M+1쿼리→3쿼리, listForTraveler/listForGuide 둘 다), bookings/[id] act()가 PATCH 응답 재사용(재조회 GET 삭제), InboxService 안읽음 쿼리를 early-return 뒤로, ChatRoom `grouped` useMemo(키입력마다 재계산 방지), SlotCalendar useMemo deps 수정(`[props]`→실제 배열).
- **삭제**: i18n 죽은 키 25종×3언어(75줄, 리팩터 잔재 — courses waypoint 편집기/trips 구UI/guideDetail 단일폼/availability 등, 전부 grep 0-usage 검증), guides/[id] 예약 제출 루프 2개→`postBookings()` 통합, placeCard 내부 파서 un-export, ChatRoom myId 중복 제거.
- **의도적 스킵**: ① 예약 생성 병렬화(즉시예약 겹침 이중확정 레이스 — 순차가 의도된 동작, postBookings 주석에 명시) ② `/api/badges` 통합 엔드포인트(새 컨트롤러 필요, 백로그) ③ Booking 엔티티 필드 누적/ChatRoom props/카드 규약/TimetableBuilder mode 분기 — 리뷰 판정 "현행 유지가 맞음".
- 검증: compileJava·tsc·lint 클린 + 백엔드 스모크(배치 목록/채팅 위임 검증/제3자 400/인박스/거절 배지) + 브라우저(배지·채팅·모달 Esc·예약상세, 페이지 에러 0).

## UX 배치 ②: 다중일 시간범위 예약 · 채팅 일정/코스 공유 (2026-07-07 — 프론트만, tsc·브라우저 E2E 통과)
사용자 요청 2건. **둘 다 백엔드 무변경**.
- **다중일 시간범위 예약** (`guides/[id]` 수동 폼) — 단일 datetime→**날짜별 행 편집기**(각 행 = 날짜 + 시작~종료 시간, `type=time step=3600`으로 정수시간 보장, "+ 일수 추가"/행 삭제). 제출 시 행별 `POST /api/bookings` 개별 생성 + **부분실패 요약**(#3의 `bookingSummary {sent,accepted,failed}` 재사용). `slotHours`/`rowHours` 헬퍼. 슬롯 다중선택(직전 배치)과 공존 — 수동은 "직접 입력" 경로. 검증: Playwright 2행→2건 생성(9/10·9/11 REQUESTED 10:00 KST), en/zh 렌더.
- **채팅 일정/코스 공유** — 여행자 여행일정 / 가이드 투어코스를 채팅에서 **날짜별로 공유**. **스냅샷 임베드 규약** `PEERUP::PLAN::{json}`(장소카드와 같은 프론트 전용·백엔드 불투명, 권한문제 회피·공유시점 고정). `lib/placeCard.ts`에 **통합 `parseCard`**(place|plan) + `cardPreview` — advisor 지적대로 4개 소비지점(렌더분기·자동번역필터·번역토글·인박스프리뷰) 전부 이거로 통일(장소카드 버그 재발 방지).
  - `SharePickerModal`(신규): 탭 여행일정(`GET /api/itineraries/me`)+투어코스(`/api/guide-profiles/me/courses`, **비가이드 400은 catch→빈목록**, 코스는 날짜 선택 옵션). 선택 시 스냅샷 빌드(일정=일차별 아이템 name/startHour/category, 코스=정차지). `PlanCard.tsx`(신규): `PlanMessageCard`(요약카드, 버블 밖) + `PlanPreviewModal`(일정=날짜별/일차별, 코스=정차지 순서).
  - ChatRoom: 📍/🗺️ 알약 2개 → **"+" 첨부 버튼 + 액션시트**(내위치/장소/일정·코스 공유)로 교체(모바일 폭·확장성, advisor 제안). 공유는 **DM·예약채팅 양쪽, 양쪽 참여자 모두** 무조건(onSetMeetingPlace처럼 게이팅 안 함). 카드 자동번역 스킵.
  - 검증: Playwright — 여행자 일정공유→카드(2일·3곳)+미리보기(1일차/2일차, 경복궁·광장시장·해운대), **가이드도 같은 카드 봄**(양쪽), 가이드 코스공유(날짜)→카드(정차 2곳), DM에서도 공유됨, 인박스 "📅/🎫 …공유" 프리뷰(raw JSON 아님), en/zh 카드·피커 렌더(undefined 없음).
- **재사용/한계**: `parseCard`/`cardPreview` 단일소스. 스냅샷은 name/startHour/category 등 essentials만(수 KB, STOMP 64KB 한참 아래). 코스 스냅샷 date는 선택. 공유 후 원본 수정돼도 카드는 공유시점 고정(의도).

## UX 배치 ①: 채팅 버튼 라벨 · 채팅별 안읽음 · 다중날짜 예약 · 거절 배지 (2026-07-07 — 백엔드+프론트, tsc·compileJava·브라우저 E2E 통과)
사용자 요청 4건.
- **#1 채팅 장소 버튼 라벨** — `ChatRoom` 입력바의 아이콘만 있던 📍/🗺️를 **입력창 위 별도 라벨 행**으로 분리("📍 내 위치 보내기", "🗺️ 장소 보내기"). advisor 지적대로 모바일 420px에서 입력창과 안 겹치게 위쪽 행 배치. i18n `placeCard.sendLocation/searchPlace` 문구를 액션형으로 수정.
- **#2 채팅별 안읽음 표시** — 인박스(`/messages`)에서 내가 안 읽은 스레드에 **파란 점 + 프리뷰 볼드**. `InboxThreadResponse.unread` 추가. `ConversationRepository.conversationIdsWithUnread` + `MessageRepository.bookingIdsWithUnread` 배치쿼리(각각 `unreadCount`와 **동일 술어** — 사이드바 총배지와 안 어긋남, DM은 차단 제외 포함). `InboxService`가 두 ID Set으로 스레드별 unread 세팅. (해석: 인박스 스레드별 안읽음 점. 상대가 내 메시지 읽었는지=읽음표시는 별도 미구현.)
- **#3 다중 날짜 예약** — 예약 캘린더(`SlotCalendar` traveler)를 **단일→다중 선택**(`selectedSlots`/`onToggleSlot`, 슬롯 체크박스 + 날짜셀 초록 카운트 배지, 월 이동해도 선택 유지). `guides/[id]` 예약 패널: 선택 요약(N개·합계금액)+메시지+**"N건 예약 요청" 버튼** → 슬롯별 예약 개별 생성. **부분 실패 처리**: 하나 실패(즉시예약 시간겹침 등)해도 나머지 진행, "N건 보냄 / M건 실패" 요약(`bookingSummary {sent,accepted,failed}`). 백엔드 모델 무변경. 수동입력(직접 날짜) 단일 경로는 유지. "모든 캘린더"는 예약 캘린더에 적용(가이드 슬롯 등록은 기존 +등록이 확인 역할).
- **#4 예약 거절 배지** — `Booking.rejection_seen`(nullable, `reject()`가 false로, additive). `BookingService.listForTraveler`가 REJECTED 미확인을 seen 처리(목록 열면 클리어). `GET /api/bookings/traveler/rejected-count`. 사이드바 여행자 **내 예약** 배지(30초 폴링, mode==traveler). `RejectionSeenFalse`가 null(옛 예약) 제외 → 기존 거절은 배지 안 뜸. 데스크탑 레일 전용(모바일 하단탭에 내예약 없음 — "왼쪽 베너"와 일치).
- **검증**: curl(#4 0→거절1→목록열기0; #2 예약스레드 unread True→방열기 False, unread-count 정합) + Playwright(#3 정상=2건 생성, 즉시예약 겹침=1확정+"시간겹침 실패" 표시 부분실패 보고; #1 라벨·#2 파란점·#4 사이드바 "1" 배지 스크린샷) + en 파리티(신규 키 undefined 없음). 테스트 계정 다수 실 dev DB.

## T1/T2/T3 만남장소·예약상세·위치공유 (2026-07-07 — 백엔드+프론트 완료, tsc·compileJava·브라우저 E2E 통과)
"예약 확정 이후~투어 당일" 구간을 채움. 외국인이 한국 주소를 말로만 받던 문제 해결.
- **T1 만남 장소 + 채팅 장소 카드** — 채팅(예약/DM 공용 `ChatRoom`)에서 🗺️로 Kakao 자유검색(`PlacePickerModal` → `GET /api/places/search?query=&lang=`, authenticated) 후 **장소 카드 전송**. 카드 탭 → `PlaceDetailModal`(지도 핀). **예약 채팅에서만** 카드에 "만남 장소로 지정" 버튼(`onSetMeetingPlace` prop, DM 래퍼는 미전달) → `PATCH /api/bookings/{id}/meeting-place` → `Booking.meeting_place_*`(5컬럼, additive). 지정 시 "✓ 만남 장소로 지정됨".
- **T3 내 위치 보내기** — 입력바 📍 → `navigator.geolocation` → 좌표를 kind:"me" 장소 카드로 전송(거부 시 에러 표시, NaN 전송 안 함).
- **장소 카드 = 프론트 전용 인코딩 규약** `lib/placeCard.ts` (`PEERUP::PLACE::{json}`) — 백엔드는 content를 불투명 텍스트로 저장/브로드캐스트(STOMP 전송경로·메시지테이블 무변경). `parsePlaceCard` 성공 시 **텍스트 버블 밖 전용 카드**로 렌더(흰배경 흰글자 방지). **자동번역/수동토글 스킵**(raw JSON 번역 방지 — auto 필터 + 토글 둘 다). 파싱 실패 시 일반 텍스트로 degrade.
- **인박스 프리뷰** — `/messages`가 raw `PEERUP::PLACE::…`(80자 truncation 후에도 prefix 생존) 감지 → "📍 장소 공유" 라벨(`placeCardPreview`).
- **T2 예약 상세 `/bookings/[id]`(신규)** — 참여자 전용(`GET /api/bookings/{id}`). 상태 배지·D-day·시간(KST + 뷰어 로컬 병기, 로컬=KST면 자동 숨김)·만남장소 지도(`TripMap` 단일핀)+주소복사+카카오 열기(미지정 시 "채팅에서 정하기")·상대(가이드면 프로필 링크)·"채팅 열기"·완료/취소/리뷰·"한국어 한마디" 참고. 목록(`/traveler/bookings`)+요청(`/guide/requests`)에 "상세 보기" 링크.
- **검증**: curl(places/search 15개·비인증 401·PATCH 저장·가이드 조회·타인 400) + Playwright(T2 렌더, 채팅 연결→장소검색카드→만남지정 확인→PATCH 반영 "경복궁"→내위치 카드, 인박스 "📍 장소 공유" 표시·raw 미노출). 테스트 계정 `mp_t_*/mp_g_*@test.com`(booking 18) 실 dev DB.
- **재사용/결정**: `PlaceDetailModal`·`TripMap`·`chat.quickPhrases` 재사용. 만남장소는 참여자 누구나 지정(양쪽 협의, PATCH 덮어쓰기, REQUESTED/ACCEPTED에서만). DM엔 만남장소 개념 없음(booking 없음) — 장소 카드/내위치만.

## 가이드 코스 빌더 = 드래그 타임테이블 (2026-07-07 — 백엔드+프론트, tsc·compileJava·브라우저 드래그 E2E 통과)
가이드 코스 동선 편집을 ▲▼ 순서 리스트에서 **여행자 일정과 동일한 드래그 타임테이블**로 전면 개편. 우측 팔레트(장소 검색 + ✨코스 추천)에서 끌어다 시간표에 놓는다. 사용자 결정: 시간표 방식(여행자와 100% 동일 UI). 개선점: 시간/레인을 **저장·복원**(순서만 남는 손실 방지).
- **`components/TimetableBuilder.tsx` 신규 (공유 컴포넌트)** — 트립(`/trips/[id]`)과 코스(`/guide/courses`)가 공유. DndContext + 미배치 트레이 + 시간표 그리드(06~24시, 레인) + 블록(깊이/넓이 ±) + 지도 동선 + 팔레트 + **Wave 6 검증 드래그 수학(포인터좌표→hour/lane, resolveLane)** 을 한 곳에. controlled: 부모가 `items`/`onItemsChange` 소유. props: `mode`("trip"|"course"), `singleDay`, `minDayCount`, `onFillFormFromRec`. `mode="trip"`=멀티데이+팔레트 2번째 탭 투어코스 / `mode="course"`=단일 시간표(일차탭 숨김)+팔레트 2번째 탭 코스추천. **추출 원칙(advisor): 멀티데이 로직을 부모로 빼지 않고 컴포넌트 안에 그대로 이관, singleDay 플래그로 분기** — 검증된 코드 최소 이동.
- **`/trips/[id]/page.tsx` 리팩터링** — 870→약 160줄. 메타(제목/도시/날짜)·로드/저장/삭제만 소유, 시간표·팔레트·일차탭은 `<TimetableBuilder mode="trip">`에 위임. **회귀 하드게이트 GREEN**: Playwright 드래그(칩 생성→배치→저장→새로고침 유지→일차탭 유지) 통과. `_k`/dayIndex 경계 무손상.
- **`/guide/courses/page.tsx` 재작성** — waypoints ▲▼ 편집기 + 별도 추천 패널 삭제 → 메타 폼 + `<TimetableBuilder mode="course" singleDay onFillFormFromRec>` + 등록 버튼. 저장 시 배치 블록을 (startHour,laneIndex) 순 정렬 → sortOrder 재부여 + **시간/레인 함께 저장**. 편집 시 waypoint.startHour 있으면 복원, 없으면(레거시/추천) 10시부터 순차 배치.
- **추천 개선(백엔드 recommender 유지)** — 추천을 워크벤치 팔레트의 ✨탭으로 통합. 정차지가 **드래그 가능한 카드**(직접 시간표로 끌어다 놓기) + "폼 채우기"(제목·소개·도시·소요 채우고 정차지 5곳 시간표에 자동 배치). 기존 "담기" 리스트 방식 대체.
- **백엔드(additive nullable)** — `TourCourseWaypoint`+req/resp에 `start_hour`/`duration_hours`/`lane_index`/`lane_span`(ItineraryItem 패턴). **편집 전용** — 공개 코스 뷰(가이드 상세/CourseCard/명소 퍼널)·트립 코스드롭(waypoints[0]만 사용)에 시간 노출 안 함(grep로 유출 없음 확인).
- **검증**: `gradle compileJava`+`npx tsc --noEmit` 통과. Playwright — 트립 회귀 GREEN, 코스: 추천 5개 로드→2개 카드 11시·13시 드래그 배치→등록→API로 waypoint startHour=11/13·lane=0 저장 확인→수정 클릭 시 2블록 복원. 폼채우기→5블록 자동배치+제목 "서울 믹스 투어". 테스트 계정 `crs_g_*@test.com`(가이드프로필 29), `trip_t_*`(일정 37) 실 dev DB에 남음.
- **가이드 사이드바 정리(같은 세션)** — 가이드 데스크탑 레일에서 `지역 둘러보기(/explore)`·`여행일정 짜기(/trips)` 제거(10→8개, 가이드 업무 무관·모바일 탭엔 원래 없음). 라우트는 살아있음(모드 전환으로 접근).

## A2 통합 인박스 (2026-07-07 — 백엔드+프론트, tsc·compileJava·브라우저 E2E 통과)
`/messages` 인박스에 DM(예약 전 문의)만 있고 예약 채팅은 예약 목록에서만 진입하던 걸 **한 목록으로 통합**.
- **백엔드 신규 `GET /api/inbox`** (`chat/InboxController` + `InboxService`, 인증 필요 — SecurityConfig permitAll 미등록으로 기본 authenticated). DM(`ConversationService.myConversations` 재사용, 차단 필터·배치 조회 포함) + **예약 채팅(메시지가 실제로 오간 방만)** 을 합쳐 `lastMessageAt` 내림차순 정렬해 `InboxThreadResponse[]` 반환.
- **`InboxThreadResponse`** — `type`("dm"|"booking") 디스크리미네이터 + `conversationId`/`bookingId`(링크용) + guideProfileId/otherUserId/otherName/otherAvatarUrl/otherIsGuide/lastMessagePreview/lastMessageAt + `bookingStatus`(예약 스레드만). `dm()` 팩토리로 `ConversationResponse`에서 변환.
- **스키마 무변경(compute-on-read)** — 예약 채팅엔 last-message 컬럼이 없어 읽기 시점에 계산. `MessageRepository.findByBookingIdInOrderByCreatedAtAsc(ids)` **배치 1회**로 예약별 마지막 메시지(오름차순이라 마지막 put이 최신), 상대 가이드 프로필·유저는 `findAllById` 배치 — 원격 DB(Sydney ~250ms) N+1 회피. 여행자 방향(`findByTravelerId…`) + 가이드 방향(`findByUserId`로 내 프로필 → `findByGuideProfileId…`) 둘 다 모아 mode 무관하게 표시.
- **프론트 `/messages/page.tsx`** — `/api/conversations` → `/api/inbox`로 교체. dm→`/messages/{conversationId}`, booking→`/chat/{bookingId}` 링크 분기. 예약 스레드는 amber `🎫 예약 대화` 배지(DM은 기존 가이드/여행자 배지 유지) — advisor 지적대로 같은 가이드가 DM+예약 두 줄로 보여도 배지로 구분됨. `ChatRoom.tsx`·`/chat/[bookingId]`·`/messages/[id]` 래퍼는 **무수정**.
- **의도적 범위 제외(팔로업)**: ① ~~예약 채팅 안읽음 배지~~ → **아래 별도 항목으로 완료(2026-07-07)**. ② 예약 스레드는 **차단 필터 미적용**(Wave 5 "예약 채팅 차단 enforce 안 함"과 일관). ③ 메시지 0건 예약은 인박스에 안 뜸(빈 DM 방은 뜸 — DM 생성은 명시적 "대화 시작" 행위라 의도적 비대칭).

## 예약 채팅 안읽음 배지 (2026-07-07 — 백엔드+프론트, compileJava·tsc·curl·브라우저 검증)
A2에서 남겼던 팔로업. 사이드바 💬 배지가 DM만 세던 걸 **DM + 예약 채팅 통합**으로.
- **스키마(additive nullable, ddl 안전)** — `Booking`에 `traveler_last_read_at`/`guide_last_read_at` 2컬럼(재시작 시 `bookings`에 `add column` 확인). `Conversation`의 동명 컬럼 패턴과 동일. `Booking.markChatRead(boolean isTraveler)`.
- **읽음 처리** — `MessageService.history()`를 `@Transactional`(writable)로 바꿔 예약 채팅방 열 때(`GET /api/bookings/{id}/messages`, ChatRoom 마운트 시 호출) 뷰어 쪽 last-read를 지금으로. 여행자/가이드는 `booking.getTravelerId().equals(userId)`로 판정.
- **집계** — `MessageRepository.unreadCount(userId, guideProfileId)` 단일 COUNT(Message⋈Booking, 상대 발신 + 내 last_read 이후). `guideProfileId`가 null(가이드 아님)이면 가이드 분기 미매칭(`:guideProfileId is not null` 가드). 예약 채팅은 차단 enforce 대상 아니라 DM unread와 달리 **차단 서브쿼리 없음**.
- **통합 엔드포인트** — `InboxService.unreadCount(userId)` = `conversationService.unreadCount`(기존 DM) + `messageRepository.unreadCount`(신규 예약). `GET /api/inbox/unread-count` {count}. `Sidebar.tsx`가 `/api/conversations/unread-count` → `/api/inbox/unread-count`로 폴링 대상 변경(30초 폴링·pathname 변경 refetch 그대로). 기존 `/api/conversations/unread-count`는 미사용이지만 하위호환 위해 유지.
- **검증**: curl 전이(가이드 2건→여행자 unread 2 / 방 열기 후 0 / 새 1건 후 1 / 발신자 본인은 0) + DM 1 + 예약 1 = **합산 2** + 순수 여행자(guideProfileId null) 무오류 + Playwright 데스크탑 사이드바에 💬 메시지 빨강 배지 "1"(예약 채팅만 있는 계정, 이전 엔드포인트였다면 0) 렌더 확인.
- **한계(기존과 동일)**: 배지 즉시 클리어는 pathname 변경 시 refetch에 의존(booking pending-count·DM과 동일, 30초 내 자기보정). 예약 채팅에는 여전히 실시간 read-receipt/방별 안읽음 표시는 없음(사이드바 총합만).
- i18n: `dm.bookingBadge`("예약 대화"/"Trip chat"/"预约对话") ko/en/zh 추가.
- **검증**: `gradle compileJava` + `npx tsc --noEmit` 통과. curl 양방향(여행자 인박스=예약+DM 2줄 최신순, 가이드 인박스=예약 스레드 여행자 기준) + Playwright(모바일 뷰 렌더 스크린샷 + 클릭 라우팅: booking→/chat/14 Haeundae 메시지, dm→/messages/5 Sunday 메시지 확인). 테스트 계정 `inbox_t_*/inbox_g1_*/inbox_g2_*@test.com`(비번 test1234) + booking/conversation 몇 건 실 dev DB에 남음(콘솔 정리 대상).

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
