# 투트랙 유저플로우 재설계 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서비스를 "🎫 인증 가이드 투어"와 "🤝 로컬 동행(동행 파트너)" 두 트랙으로 이원화 — 스펙 `docs/superpowers/specs/2026-07-17-two-track-userflow-design.md`.

**Architecture:** 백엔드 게이팅(ServiceCategory·GuideVerification)은 이미 존재. 이번 작업은 ① 백엔드 additive 2건(`Booking.request_details`, 일정 자동추가 라벨 일반화) ② 프론트 IA 재편(라우트 `/companions`·`/find` 신설, `/guides` 투어 전용화, 사이드바·랜딩·온보딩 개편) ③ 카테고리별 예약 요청 폼.

**Tech Stack:** Next.js 15 + React 19 + TS + Tailwind 3 / Spring Boot 3.3.5 + Java 21 / Supabase Postgres (ddl-auto: update)

## Global Constraints (모든 태스크에 암묵 적용)

- **커밋**: 사용자가 이 작업에 한해 태스크별 커밋을 승인했다(2026-07-17). 브랜치 `feat/two-track`, 베이스 = 체크포인트 커밋 `ecd4c79`. 각 태스크는 **자기가 만지 파일만** 스테이징해 1건 이상 커밋한다. `git add -A` 금지(다른 태스크 작업물 유입). main에는 푸시·머지하지 않는다.
- **테스트 인프라 없음** — 이 레포에는 unit test 프레임워크·테스트 디렉토리가 존재하지 않는다(의도된 현 상태). 따라서 TDD "실패하는 테스트 먼저"는 적용되지 않으며, 테스트 부재는 결함이 아니다. 검증은 `npx tsc --noEmit`(반드시 `app/frontend`에서) / `gradle compileJava`(`app/backend`에서, `JAVA_HOME=$(/usr/libexec/java_home -v 21)`) / curl / Playwright(스크래치패드에 설치됨)로 하고, 각 태스크의 검증 스텝에 적힌 명령과 기대 출력이 그 태스크의 통과 기준이다.
- **i18n**: 모든 신규 키는 `src/lib/i18n.ts`의 ko/en/zh 3개 블록에 **동일 키 세트**로 추가. `Translations = typeof t.ko` 구조적 타입이라 누락 시 en/zh 블록에서 tsc 에러.
- **DDL은 additive-only** — nullable 컬럼 추가만 가능. NOT NULL/rename/타입변경 불가.
- **새 npm 패키지 금지**, 아키텍처 변경 금지.
- **호칭 규칙**: 동행 컨텍스트(신규 `/companions*`, `/find`, 동행 예약 폼, 무자격 온보딩 경로)의 ko/en/zh 카피에 '가이드'/'Guide'/'导游' 금지.
- 백엔드 코드 수정 후 **재시작 필수**(백그라운드 `gradle bootRun`은 같은 명령 안에서 `cd .../app/backend` 후 실행). 목록 API에서 루프 내 단건 쿼리 금지(원격 DB Sydney ~250ms).
- 기존 컴포넌트 재사용: `GuideCard`, `CitySelect`, `DistrictSelect`, `ServiceCategoryPicker`, `useModalDismiss`, `SlotCalendar`, `ChatRoom`.

---

### Task 1: 백엔드 — `Booking.request_details` 컬럼 + DTO 왕복

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/booking/Booking.java`
- Modify: `app/backend/src/main/java/com/guidematch/booking/dto/CreateBookingRequest.java`
- Modify: `app/backend/src/main/java/com/guidematch/booking/dto/BookingResponse.java`
- Modify: `app/backend/src/main/java/com/guidematch/booking/BookingService.java` (create 메서드)

**Interfaces:**
- Produces: `Booking.getRequestDetails(): String`(nullable), `CreateBookingRequest.requestDetails(): String`, `BookingResponse.requestDetails` 필드 — Task 10/11이 소비. JSON 문자열이지만 **백엔드는 불투명 저장**(파싱 안 함), 길이 상한 2000자만 검증.

- [ ] **Step 1: Booking 엔티티에 컬럼 추가** — `rejectionSeen` 필드 선언 아래에:

```java
    /**
     * 동행 예약의 카테고리별 요청 내용 (프론트 전용 JSON 규약, 백엔드는 불투명 텍스트).
     * placeCard 규약과 같은 철학 — 서버는 저장/반환만 한다. 투어 예약은 null.
     */
    @Column(name = "request_details", columnDefinition = "text")
    private String requestDetails;
```

getter/setter는 `markRejectionSeen()` 아래에:

```java
    public String getRequestDetails() { return requestDetails; }
    public void setRequestDetails(String requestDetails) { this.requestDetails = requestDetails; }
```

(기존 9-인자 생성자는 건드리지 않는다 — additive 세터 패턴.)

- [ ] **Step 2: CreateBookingRequest에 필드 추가** — `String message` 아래에 (import `jakarta.validation.constraints.Size` 추가):

```java
        String message,

        /** 동행 예약의 카테고리별 요청 내용 (선택, 프론트 JSON 규약 — 서버는 불투명 저장). */
        @Size(max = 2000, message = "요청 내용이 너무 깁니다.")
        String requestDetails
```

`BookingController`의 create 핸들러에 `@Valid`가 붙어 있는지 확인(기존 `@NotNull` 메시지가 동작 중이므로 붙어 있을 것). 없으면 `@Valid @RequestBody CreateBookingRequest`로 수정.

- [ ] **Step 3: BookingService.create에서 저장** — `Booking saved = bookingRepository.save(booking);` **직전**에:

```java
        if (request.requestDetails() != null && !request.requestDetails().isBlank()) {
            booking.setRequestDetails(request.requestDetails());
        }
```

- [ ] **Step 4: BookingResponse에 노출** — record 컴포넌트 `MeetingPlace meetingPlace` 앞에 `String requestDetails` 추가(선언 순서는 `createdAt` 뒤), `of()`에서 `b.getRequestDetails()` 전달. record 전체 시그니처가 바뀌므로 위치 인자 순서를 정확히 맞출 것:

```java
        /** 동행 예약의 카테고리별 요청 내용 (프론트 JSON 규약). 투어/기존 예약은 null. */
        String requestDetails,
        Instant createdAt,
        MeetingPlace meetingPlace
```

`of()`의 인자 나열에서 `b.getMessage(), ... , b.getRequestDetails(), b.getCreatedAt(), mp` 순으로.

- [ ] **Step 5: 컴파일 + DDL 확인**

Run: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava`
Expected: BUILD SUCCESSFUL. 이후 백엔드 재시작 시 로그에 `alter table bookings add column request_details text` 확인.

- [ ] **Step 6: curl 왕복 검증** — 테스트 여행자 토큰으로(HANDOFF §7 참고: signup → login으로 `accessToken`, 가이드 프로필엔 `languages` 배열 필수):

```bash
curl -s -X POST localhost:8080/api/bookings -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
  -d '{"guideId":GID,"startAt":"2026-08-01T01:00:00Z","hours":2,"serviceCategory":"DINING_COMPANION","message":"hi","requestDetails":"{\"diningFood\":\"bibimbap\"}"}'
```

Expected: 201/200 응답 JSON에 `"requestDetails":"{\"diningFood\":\"bibimbap\"}"`. 이어서 `GET /api/bookings/{id}`로도 동일 값 확인. 2001자 문자열로 400 확인.

---

### Task 2: 백엔드 — 일정 자동추가 라벨을 serviceCategory 기반으로 일반화

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/itinerary/ItineraryService.java:91-128`
- Modify: `app/backend/src/main/java/com/guidematch/booking/BookingService.java` (autoAddTourItem 호출 2곳 — create의 instant 경로와 accept 경로, `grep -n autoAddTourItem`으로 위치 확인)

**Interfaces:**
- Consumes: `Booking.getServiceCategory()` (기존, nullable)
- Produces: `autoAddTourItem(Long travelerId, Long bookingId, Instant startAt, String guideHeadline, String guideCity, String travelerNote, String serviceCategory)` — 마지막 파라미터 신규(nullable). 일정 아이템 category는 투어/null이면 `"tour"`, 동행이면 `"companion"` — Task 14의 프론트 스타일이 소비.

- [ ] **Step 1: 라벨 매핑 헬퍼 추가** — `ItineraryService`에 private static 메서드 2개:

```java
    /** 예약 카테고리 → 일정 아이템 표시 라벨. null(기존 예약)은 투어로 취급. */
    private static String bookingItemLabel(String serviceCategory) {
        if (serviceCategory == null) return "🎫 가이드 투어";
        return switch (serviceCategory) {
            case "MEDICAL_INTERPRETER" -> "🏥 병원 동행";
            case "DINING_COMPANION"    -> "🍽️ 식사 동행";
            case "CAFE_COMPANION"      -> "☕ 카페 동행";
            case "SHOPPING_INTERPRETER"-> "🛍️ 쇼핑 통역";
            case "LANGUAGE_EXCHANGE"   -> "🗣️ 언어 교환";
            default -> "🎫 가이드 투어"; // TOUR_GUIDE 포함
        };
    }

    /** 일정 아이템 category 값 — 프론트가 tour=amber / companion=중립 스타일로 구분. */
    private static String bookingItemCategory(String serviceCategory) {
        return (serviceCategory == null || "TOUR_GUIDE".equals(serviceCategory)) ? "tour" : "companion";
    }
```

- [ ] **Step 2: autoAddTourItem 시그니처 확장 + 사용** — 파라미터 끝에 `String serviceCategory` 추가. 본문 두 곳 교체:

새 일정 제목(108-110행 근방):

```java
                boolean isTour = "tour".equals(bookingItemCategory(serviceCategory));
                String suffix = isTour ? " 투어" : " 동행";
                String title = (guideHeadline != null && !guideHeadline.isBlank())
                        ? guideHeadline + suffix : (isTour ? "가이드 투어" : "동행 일정");
```

아이템 생성(119-120행):

```java
            ItineraryItem item = new ItineraryItem(dayIndex, nextSort, null,
                    bookingItemLabel(serviceCategory), bookingItemCategory(serviceCategory),
                    null, null, null, travelerNote);
```

(placeName 한국어 하드코딩은 기존 알려진 한계(Wave 2 #4) — 이번 범위에서 유지.)

- [ ] **Step 3: 호출부 2곳 수정** — `BookingService`에서 `autoAddTourItem(...)` 호출에 마지막 인자 추가:

```java
                        saved.getServiceCategory() != null ? saved.getServiceCategory().name() : null
```

(accept 경로도 동일 패턴 — 그쪽 지역변수 이름에 맞출 것.)

- [ ] **Step 4: 컴파일 + curl 검증**

Run: `gradle compileJava` → BUILD SUCCESSFUL. 백엔드 재시작 후: 즉시예약 가이드에 `serviceCategory=DINING_COMPANION`으로 예약 생성 → `GET /api/itineraries/me`에서 해당 날짜 아이템이 `"placeName":"🍽️ 식사 동행","category":"companion"`인지 확인. `TOUR_GUIDE` 예약은 기존대로 `"🎫 가이드 투어"`/`"tour"`.

---

### Task 3: i18n — 신규 키 전체 추가 (ko/en/zh)

**Files:**
- Modify: `app/frontend/src/lib/i18n.ts` (ko/en/zh 3개 블록 전부)

**Interfaces:**
- Produces: 아래 키 전부 — Task 4~14가 소비. 키 이름은 여기 적힌 그대로 써야 한다.

- [ ] **Step 1: `nav` 그룹 값 변경 + 키 추가** (3개 언어 모두):

| 키 | ko | en | zh |
|---|---|---|---|
| `nav.findGuide` (값 변경) | 투어 | Tours | 导览 |
| `nav.companions` (신규) | 동행 찾기 | Companions | 同行伙伴 |
| `nav.find` (신규) | 찾기 | Find | 发现 |
| `nav.guideHome` (값 변경) | 파트너 홈 | Partner Home | 伙伴主页 |

- [ ] **Step 2: 신규 최상위 그룹 4개 추가** — ko 블록 예시(en/zh는 표의 대응 값으로 동일 구조):

```ts
    tracks: {
      tourTitle: "인증 가이드 투어",
      tourDesc: "관광통역안내사 자격을 인증받은 가이드가 안내하는 관광 투어",
      tourCta: "투어 보러가기",
      companionTitle: "로컬 동행",
      companionDesc: "병원·쇼핑·식사·통역 등 일상을 함께하는 동행 서비스",
      companionCta: "동행 파트너 찾기",
      badgeExplainTitle: "관광통역안내사 인증",
      badgeExplainBody: "국가 자격 '관광통역안내사' 증빙을 운영진이 확인한 가이드에게만 부여되는 배지예요. 관광 안내(명소·문화유산 해설 등)는 인증 가이드만 제공할 수 있어요.",
      companionNotice: "동행 파트너는 관광안내(명소·문화유산 해설 등)를 제공할 수 없어요. 관광 안내가 필요하면 인증 가이드 투어를 이용해 주세요.",
      companionNoticeLink: "인증 가이드 투어 보기",
    },
    find: {
      title: "무엇이 필요하세요?",
      sub: "투어와 동행, 두 가지 방법으로 현지인을 만나보세요",
    },
    companions: {
      title: "로컬 동행",
      sub: "병원, 쇼핑, 식사… 필요한 순간에 함께할 동행 파트너를 찾아보세요",
      categoryPrompt: "어떤 동행이 필요하세요?",
      all: "전체",
      empty: "조건에 맞는 동행 파트너가 없어요",
      partnerBadge: "동행 파트너",
      alsoCertified: "인증 가이드이기도 해요",
      viewTourProfile: "투어 프로필 보기",
      alsoCompanion: "동행 서비스도 제공해요",
      viewCompanionProfile: "동행 프로필 보기",
    },
    companionBooking: {
      detailsTitle: "요청 내용",
      optionalHint: "모두 선택 입력이에요",
      hospitalName: "병원·기관명",
      hospitalPurpose: "방문 목적 (진료과 등)",
      shoppingArea: "쇼핑 지역",
      shoppingItems: "원하는 품목",
      diningFood: "원하는 음식·식당",
      diningRecommend: "추천해 주세요",
      cafeNote: "메모",
      languageTarget: "배우고 싶은 언어",
      languageLevel: "수준",
    },
    onboardingFork: {
      question: "관광통역안내사 자격증이 있으신가요?",
      yes: "네, 있어요",
      yesDesc: "투어 가이드와 동행 서비스를 모두 제공할 수 있어요 (자격 인증 심사 필요)",
      no: "아니요, 없어요",
      noDesc: "식사·쇼핑·병원 등 동행 서비스를 제공할 수 있어요",
      licenseNote: "인증 심사 중에도 동행 서비스는 바로 시작할 수 있어요. 투어는 인증 승인 후 열려요.",
      partnerTitle: "파트너 되기",
      partnerSub: "여행자의 한국 생활과 여행을 함께할 파트너가 되어보세요",
    },
```

en 값:

```
tracks: { tourTitle: "Certified Guide Tours", tourDesc: "Sightseeing tours led by guides holding a national tourist-interpreter license", tourCta: "Browse tours", companionTitle: "Local Companions", companionDesc: "Everyday help — hospital visits, shopping, dining, interpretation", companionCta: "Find a companion", badgeExplainTitle: "Certified Tourist Interpreter", badgeExplainBody: "This badge is granted only to guides whose national tourist-interpreter license was verified by our team. Only certified guides may provide sightseeing guidance.", companionNotice: "Companions cannot provide sightseeing guidance (landmark or heritage commentary). For sightseeing, please book a certified guide tour.", companionNoticeLink: "See certified guide tours" }
find: { title: "What do you need?", sub: "Two ways to meet a local — tours and companions" }
companions: { title: "Local Companions", sub: "Hospital, shopping, dining… find a companion for the moments you need one", categoryPrompt: "What kind of help do you need?", all: "All", empty: "No companions match your filters", partnerBadge: "Companion", alsoCertified: "Also a certified guide", viewTourProfile: "View tour profile", alsoCompanion: "Also offers companion services", viewCompanionProfile: "View companion profile" }
companionBooking: { detailsTitle: "Request details", optionalHint: "All fields optional", hospitalName: "Hospital / institution", hospitalPurpose: "Purpose of visit (department, etc.)", shoppingArea: "Shopping area", shoppingItems: "What you want to buy", diningFood: "Food or restaurant you want", diningRecommend: "Recommend for me", cafeNote: "Note", languageTarget: "Language to practice", languageLevel: "Level" }
onboardingFork: { question: "Do you hold a tourist-interpreter guide license?", yes: "Yes, I do", yesDesc: "You can offer both guided tours and companion services (license review required)", no: "No, I don't", noDesc: "You can offer companion services — dining, shopping, hospital visits and more", licenseNote: "You can start offering companion services right away while your license is under review. Tours unlock after approval.", partnerTitle: "Become a Partner", partnerSub: "Help travelers with everyday life and travel in Korea" }
```

zh 값:

```
tracks: { tourTitle: "认证导游导览", tourDesc: "由持有国家观光翻译导游资格的导游带领的观光导览", tourCta: "浏览导览", companionTitle: "本地同行", companionDesc: "医院、购物、用餐、翻译等日常陪同服务", companionCta: "寻找同行伙伴", badgeExplainTitle: "观光翻译导游认证", badgeExplainBody: "此徽章仅授予由运营团队核实国家“观光翻译导游”资格的导游。观光讲解(景点·文化遗产解说等)仅限认证导游提供。", companionNotice: "同行伙伴不能提供观光讲解(景点·文化遗产解说等)。如需观光讲解,请预约认证导游导览。", companionNoticeLink: "查看认证导游导览" }
find: { title: "您需要什么?", sub: "导览与同行,两种方式遇见本地人" }
companions: { title: "本地同行", sub: "医院、购物、用餐……在需要的时刻找到同行伙伴", categoryPrompt: "您需要哪种陪同?", all: "全部", empty: "没有符合条件的同行伙伴", partnerBadge: "同行伙伴", alsoCertified: "也是认证导游", viewTourProfile: "查看导览主页", alsoCompanion: "也提供陪同服务", viewCompanionProfile: "查看陪同主页" }
companionBooking: { detailsTitle: "请求详情", optionalHint: "均为选填", hospitalName: "医院·机构名称", hospitalPurpose: "到访目的(科室等)", shoppingArea: "购物区域", shoppingItems: "想购买的物品", diningFood: "想吃的美食·餐厅", diningRecommend: "请为我推荐", cafeNote: "备注", languageTarget: "想学的语言", languageLevel: "水平" }
onboardingFork: { question: "您持有观光翻译导游资格证吗?", yes: "是,我有", yesDesc: "可以同时提供导览和陪同服务(需资格审核)", no: "没有", noDesc: "可以提供用餐、购物、医院陪同等服务", licenseNote: "资格审核期间即可开始提供陪同服务,导览将在审核通过后开放。", partnerTitle: "成为伙伴", partnerSub: "陪伴旅行者在韩国的日常与旅程" }
```

- [ ] **Step 3: 기존 그룹에 키 추가** (3개 언어):

| 키 | ko | en | zh |
|---|---|---|---|
| `guides.tourTrackSub` | 관광통역안내사 인증을 받은 가이드만 모았어요 | Only guides with a verified tourist-interpreter license | 仅展示通过观光翻译导游认证的导游 |
| `guideDashboard.declareBanner`* | 제공 서비스를 선언해야 목록에 노출돼요 | Declare your services to appear in listings | 需选择提供的服务才会在列表中展示 |
| `guideDashboard.declareCta`* | 제공 서비스 선택하기 | Choose your services | 选择提供的服务 |
| `itinerary.findPartnerCta` | 이 날 함께할 파트너 찾기 | Find a partner for this day | 为这一天寻找同行伙伴 |

\* `/guide` 대시보드 페이지가 쓰는 기존 그룹명을 먼저 확인(`grep -n "guideDashboard\|guideHome" src/lib/i18n.ts` 및 `app/guide/page.tsx`의 `t.` 접두)하고 그 그룹에 추가. 없으면 `guideDashboard` 신설.

- [ ] **Step 4: 타입 검증**

Run: `cd app/frontend && npx tsc --noEmit`
Expected: 에러 0 (키 세트 불일치 시 en/zh 블록에서 타입 에러 발생).

---

### Task 4: `TrackEntryCards` 공용 컴포넌트 + 랜딩·여행자 홈 투트랙 진입

**Files:**
- Create: `app/frontend/src/components/TrackEntryCards.tsx`
- Modify: `app/frontend/src/app/page.tsx` (게스트/여행자 히어로 아래)
- Modify: `app/frontend/src/app/traveler/page.tsx` (커뮤니티 배너 위)

**Interfaces:**
- Produces: `<TrackEntryCards compact?: boolean />` — Task 5(/find)도 소비. 카테고리 숏컷은 `/companions?category=<key>` 링크.

- [ ] **Step 1: 컴포넌트 작성**

```tsx
"use client";

import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";
import { NON_TOUR_CATEGORY_KEYS, type ServiceCategoryKey } from "@/lib/serviceCategories";

const CAT_ICONS: Record<string, string> = {
  MEDICAL_INTERPRETER: "🏥", DINING_COMPANION: "🍽️", CAFE_COMPANION: "☕",
  SHOPPING_INTERPRETER: "🛍️", LANGUAGE_EXCHANGE: "🗣️",
};

/** 투트랙 진입 카드 — 랜딩·여행자 홈·/find 공용. */
export default function TrackEntryCards({ compact = false }: { compact?: boolean }) {
  const { t } = useLanguage();
  const tr = t.tracks;
  return (
    <div className={`grid gap-4 ${compact ? "" : "sm:grid-cols-2"}`}>
      <Link href="/guides" className="card-hover flex flex-col gap-2 border-2 border-emerald-100 p-6">
        <span className="text-3xl">🎫</span>
        <span className="text-lg font-extrabold text-stone-900">{tr.tourTitle}</span>
        <span className="text-sm leading-relaxed text-stone-500">{tr.tourDesc}</span>
        <span className="mt-2 text-sm font-bold text-emerald-600">{tr.tourCta} →</span>
      </Link>
      <div className="card flex flex-col gap-2 border-2 border-sky-100 p-6">
        <span className="text-3xl">🤝</span>
        <span className="text-lg font-extrabold text-stone-900">{tr.companionTitle}</span>
        <span className="text-sm leading-relaxed text-stone-500">{tr.companionDesc}</span>
        <div className="mt-1 flex flex-wrap gap-1.5">
          {NON_TOUR_CATEGORY_KEYS.map((k) => (
            <Link key={k} href={`/companions?category=${k}`} className="chip">
              {CAT_ICONS[k]} {t.serviceCategories[k as ServiceCategoryKey]}
            </Link>
          ))}
        </div>
        <Link href="/companions" className="mt-2 text-sm font-bold text-sky-600">{tr.companionCta} →</Link>
      </div>
    </div>
  );
}
```

(`chip` 클래스는 globals.css에 이미 존재 — guides/page.tsx가 사용 중. `card`/`card-hover`도 기존 클래스.)

- [ ] **Step 2: 랜딩에 삽입** — `app/page.tsx`에서 게스트/여행자 히어로 섹션(약 290행 `<section ...>` 내부, `<TravelerSearchBar />` 다음)에 `<div className="mt-8"><TrackEntryCards /></div>` 추가 + import. `mode === "guide"` 분기(207행)에는 넣지 않는다.

- [ ] **Step 3: 여행자 홈에 삽입** — `app/traveler/page.tsx` "커뮤니티 배너" 주석(약 194행) **위**에 `<section className="mb-8"><TrackEntryCards /></section>` + import.

- [ ] **Step 4: 검증**

Run: `npx tsc --noEmit` → 0 에러. 브라우저: `/`(게스트·여행자 모드)와 `/traveler`에서 두 카드 + 카테고리 숏컷 5개 렌더, 숏컷 클릭 → `/companions?category=...` 이동(404여도 다음 태스크에서 해소 — 링크만 확인).

---

### Task 5: `/find` 허브 페이지

**Files:**
- Create: `app/frontend/src/app/find/page.tsx`

**Interfaces:**
- Consumes: `TrackEntryCards` (Task 4)

- [ ] **Step 1: 페이지 작성**

```tsx
"use client";

import { useLanguage } from "@/context/LanguageContext";
import TrackEntryCards from "@/components/TrackEntryCards";

export default function FindPage() {
  const { t } = useLanguage();
  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="mb-6 text-center">
          <h1 className="section-title">{t.find.title}</h1>
          <p className="section-subtitle">{t.find.sub}</p>
        </div>
        <TrackEntryCards compact />
      </div>
    </main>
  );
}
```

(`container-sm`/`section-title`/`section-subtitle`는 become-guide/page.tsx가 쓰는 기존 클래스.)

- [ ] **Step 2: 검증** — tsc 0 에러, 브라우저 `/find` 렌더(3개 언어 전환 확인).

---

### Task 6: 사이드바 재편 — 트랙 메뉴·모바일 탭·파트너 라벨·코스 메뉴 게이팅

**Files:**
- Modify: `app/frontend/src/components/Sidebar.tsx`

**Interfaces:**
- Consumes: `nav.findGuide`(→"투어")/`nav.companions`/`nav.find`/`nav.guideHome`(→"파트너 홈") (Task 3), `GET /api/guide-profiles/me`의 `verificationStatus`

- [ ] **Step 1: verificationStatus 로드** — 상태/이펙트 추가 (기존 `useEffect` 아래):

```tsx
  const [verified, setVerified] = useState(false);
  useEffect(() => {
    if (!loggedIn || mode !== "guide") { setVerified(false); return; }
    api<{ verificationStatus?: string }>("/api/guide-profiles/me", { auth: true })
      .then((p) => setVerified(p.verificationStatus === "VERIFIED"))
      .catch(() => setVerified(false));
  }, [loggedIn, mode]);
```

`import { api } from "@/lib/api"` 병합. **선행 확인**: `GuideProfileResponse`(백엔드)가 `verificationStatus`를 내려주는지 — `grep -n verificationStatus app/backend/src/main/java/com/guidematch/guide/dto/GuideProfileResponse.java`. 없으면 `String verificationStatus` 필드를 추가하고 팩토리에서 `p.getVerificationStatus().name()` 전달(GuideSummaryResponse의 기존 패턴 복사).

- [ ] **Step 2: 데스크탑 레일 메뉴 교체**

게스트(64-71행):

```tsx
    items = [
      it("/", "🏠", n.home, exact("/")),
      it("/guides", "🎫", n.findGuide, under("/guides")),
      it("/companions", "🤝", n.companions, under("/companions")),
      it("/community", "👥", n.community, under("/community")),
      it("/explore", "🧭", n.explore, under("/explore")),
      it("/trips", "🗺️", n.trips, under("/trips")),
    ];
```

여행자(else 분기): 기존 배열에서 `it("/guides", "🔍", n.findGuide, ...)`를 `it("/guides", "🎫", n.findGuide, under("/guides"))`로 바꾸고 바로 아래 `it("/companions", "🤝", n.companions, under("/companions"))` 추가.

가이드(mode === "guide"): 항목 유지하되 `it("/guide/courses", ...)` 줄을 `...(verified ? [it("/guide/courses", "🎫", n.courses, under("/guide/courses"))] : []),`로 감싼다.

- [ ] **Step 3: 모바일 탭 교체**

게스트:

```tsx
    mobileItems = [
      items[0],                                        // 홈
      it("/find", "🔍", n.find, under("/find")),
      it("/community", "👥", n.community, under("/community")),
      it("/trips", "🗺️", n.trips, under("/trips")),
      it("/login", "👤", n.login, under("/login")),
    ];
```

여행자:

```tsx
    mobileItems = [
      it("/traveler", "🧳", n.travelerHome, exact("/traveler")),
      it("/find", "🔍", n.find, under("/find") || under("/guides") || under("/companions")),
      { ...it("/messages", "💬", n.messages, under("/messages")), badge: unreadCount || undefined },
      it("/trips", "🗺️", n.trips, under("/trips")),
      it("/profile", "👤", n.profile, under("/profile")),
    ];
```

(가이드 모바일 탭은 무변경. `items[숫자]` 인덱스 참조가 깨지지 않게 게스트 mobileItems는 명시 생성으로 교체.)

- [ ] **Step 4: 프로필 카드 모드 라벨** — 204행 `"🗺️ Guide"` → `"🤝 Partner"` (Traveler는 유지).

- [ ] **Step 5: 검증** — tsc 0 에러. Playwright: 게스트/여행자/가이드(미인증·인증) 4상태에서 레일·탭 스크린샷 — 미인증 파트너 레일에 "투어 코스" 없음, 인증 가이드엔 있음, 여행자 모바일 탭 5개(찾기·여행일정 포함).

---

### Task 7: `/guides` — 투어 트랙 전용화

**Files:**
- Modify: `app/frontend/src/app/guides/page.tsx`

**Interfaces:**
- Consumes: `guides.tourTrackSub`, `tracks.badgeExplainTitle/Body` (Task 3), `useModalDismiss`(기존 훅)

- [ ] **Step 1: TOUR_GUIDE 고정 필터** — `loadGuides`에서 `if (cat) q.set("category", cat);` → `q.set("category", "TOUR_GUIDE");`로 교체하고 `cat` 파라미터·`categoryFilter` state·`onCategoryChange` 함수·카테고리 칩 블록(251-269행)을 **삭제**. `loadGuides` 호출부 3곳(`onCityChange`/`clearSearch`/`clearDateRange`)에서 세 번째 인자 제거. import에서 `SERVICE_CATEGORIES` 제거(`ServiceCategoryKey`도 미사용이 되면 함께).

- [ ] **Step 2: 트랙 헤더** — 페이지 헤딩(211-214행)에서 `{l.sub}` → `{l.tourTrackSub}`로 교체하고, 제목 옆에 배지 설명 트리거 추가:

```tsx
        <div className="mb-5">
          <h1 className="text-2xl font-extrabold tracking-tight text-stone-900 md:text-3xl">
            {l.title}{" "}
            <button onClick={() => setBadgeOpen(true)}
              className="badge-emerald align-middle text-xs">✓ {t.tracks.badgeExplainTitle}</button>
          </h1>
          <p className="mt-1 text-sm text-stone-500">{l.tourTrackSub}</p>
        </div>
```

배지 설명 시트(컴포넌트 하단, `useModalDismiss(badgeOpen, () => setBadgeOpen(false))` 사용 — 기존 모달 5곳과 같은 패턴):

```tsx
      {badgeOpen && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center"
             onClick={() => setBadgeOpen(false)}>
          <div className="w-full max-w-md rounded-t-2xl bg-white p-6 sm:rounded-2xl" onClick={(e) => e.stopPropagation()}>
            <h3 className="mb-2 text-lg font-bold text-stone-900">✓ {t.tracks.badgeExplainTitle}</h3>
            <p className="text-sm leading-relaxed text-stone-600">{t.tracks.badgeExplainBody}</p>
            <button onClick={() => setBadgeOpen(false)} className="btn-secondary mt-4 w-full">{t.common.close ?? "OK"}</button>
          </div>
        </div>
      )}
```

(`t.common.close` 존재 여부를 grep으로 확인, 없으면 `t.placeDetail.close` 재사용.)

- [ ] **Step 3: 검증** — tsc 0 에러. 브라우저 `/guides`: 카테고리 칩 없음, 인증 가이드만 목록(네트워크 탭에서 `category=TOUR_GUIDE` 확인), 배지 클릭 → 설명 시트. 투어코스·게시글 탭 회귀 확인.

---

### Task 8: `/companions` 목록 페이지 + GuideCard 트랙 변형

**Files:**
- Modify: `app/frontend/src/components/GuideCard.tsx` (href/track prop)
- Create: `app/frontend/src/app/companions/page.tsx`

**Interfaces:**
- Produces: `GuideCard`에 `href?: string`, `track?: "tour" | "companion"` prop — Task 9도 소비. track="companion"이면 링크 기본값 `/companions/{id}`, 서비스 태그에서 TOUR_GUIDE 제외, "동행 파트너" 배지 표시.

- [ ] **Step 1: GuideCard prop 확장** — 시그니처와 링크/태그부 수정:

```tsx
export default function GuideCard({ guide: g, href, track = "tour" }:
  { guide: GuideCardData; href?: string; track?: "tour" | "companion" }) {
  const { t } = useLanguage();
  const l = t.guides;
  const link = href ?? (track === "companion" ? `/companions/${g.id}` : `/guides/${g.id}`);
  const visibleCategories = (g.serviceCategories ?? []).filter(
    (k) => track === "tour" || k !== "TOUR_GUIDE");
  return (
    <Link href={link} className="card-hover flex flex-col p-5">
```

이름 옆 배지 블록에 (VERIFIED 배지 뒤):

```tsx
            {track === "companion" && (
              <span className="rounded-md bg-sky-100 px-2 py-0.5 text-[11px] font-bold text-sky-700">
                🤝 {t.companions.partnerBadge}
              </span>
            )}
```

서비스 태그 블록의 `g.serviceCategories.map` → `visibleCategories.map`으로 교체(빈 배열이면 기존 조건대로 블록 생략).

- [ ] **Step 2: 목록 페이지 작성** — guides/page.tsx의 가이드 탭 부분을 본떠 작성(게시글/코스 탭 없음):

```tsx
"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import CitySelect from "@/components/CitySelect";
import GuideCard, { type GuideCardData } from "@/components/GuideCard";
import TrackNotice from "@/components/TrackNotice";
import { NON_TOUR_CATEGORY_KEYS, type ServiceCategoryKey } from "@/lib/serviceCategories";

const CAT_ICONS: Record<string, string> = {
  MEDICAL_INTERPRETER: "🏥", DINING_COMPANION: "🍽️", CAFE_COMPANION: "☕",
  SHOPPING_INTERPRETER: "🛍️", LANGUAGE_EXCHANGE: "🗣️",
};

export default function CompanionsPage() {
  const { t, lang } = useLanguage();
  const c = t.companions;
  const [partners, setPartners] = useState<GuideCardData[]>([]);
  const [city, setCity] = useState("");
  const [category, setCategory] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function load(cityFilter: string, cat: string) {
    setLoading(true); setError("");
    try {
      const q = new URLSearchParams();
      if (cityFilter) q.set("city", cityFilter);
      if (cat) q.set("category", cat);
      q.set("lang", lang);
      const list = await api<GuideCardData[]>(`/api/guides?${q.toString()}`, { auth: true });
      // 카테고리 미선택 시엔 전체가 내려오므로 동행 카테고리 보유자만 남긴다
      setPartners(list.filter((g) => (g.serviceCategories ?? []).some((k) => k !== "TOUR_GUIDE")));
    } catch (err) {
      setError(err instanceof Error ? err.message : t.common.error);
    } finally { setLoading(false); }
  }

  useEffect(() => {
    const initial = new URLSearchParams(window.location.search).get("category") ?? "";
    if (initial && (NON_TOUR_CATEGORY_KEYS as readonly string[]).includes(initial)) setCategory(initial);
    load("", initial);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function onCategory(cat: string) {
    const next = cat === category ? "" : cat;
    setCategory(next); load(city, next);
  }

  return (
    <main className="page px-4">
      <div className="container-lg">
        <div className="mb-5">
          <h1 className="text-2xl font-extrabold tracking-tight text-stone-900 md:text-3xl">🤝 {c.title}</h1>
          <p className="mt-1 text-sm text-stone-500">{c.sub}</p>
        </div>
        <TrackNotice />
        <p className="mb-2 text-sm font-semibold text-stone-700">{c.categoryPrompt}</p>
        <div className="shelf -mx-4 mb-3 px-4 !pb-3">
          <button onClick={() => onCategory("")} className={category === "" ? "chip-active" : "chip"}>{c.all}</button>
          {NON_TOUR_CATEGORY_KEYS.map((k) => (
            <button key={k} onClick={() => onCategory(k)} className={category === k ? "chip-active" : "chip"}>
              {CAT_ICONS[k]} {t.serviceCategories[k as ServiceCategoryKey]}
            </button>
          ))}
        </div>
        <div className="card mb-5 p-4">
          <CitySelect value={city} onChange={(key) => { setCity(key); load(key, category); }} />
        </div>
        {loading && <p className="py-10 text-center text-sm text-stone-400">…</p>}
        {error && <p className="text-sm text-red-600">{error}</p>}
        {!loading && !error && partners.length === 0 && (
          <p className="py-16 text-center text-stone-500">{c.empty}</p>
        )}
        {!loading && (
          <div className="grid gap-4 sm:grid-cols-2">
            {partners.map((g) => <GuideCard key={g.id} guide={g} track="companion" />)}
          </div>
        )}
      </div>
    </main>
  );
}
```

- [ ] **Step 3: `TrackNotice` 컴포넌트 생성** — `app/frontend/src/components/TrackNotice.tsx` (동행 컨텍스트 법적 안내 박스, Task 9·10도 재사용):

```tsx
"use client";

import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";

/** 동행 트랙 법적 안내 — 동행 목록·상세·예약 폼 공용. */
export default function TrackNotice() {
  const { t } = useLanguage();
  return (
    <div className="mb-5 rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      {t.tracks.companionNotice}{" "}
      <Link href="/guides" className="font-semibold underline">{t.tracks.companionNoticeLink}</Link>
    </div>
  );
}
```

- [ ] **Step 4: 검증** — tsc 0 에러. 브라우저 `/companions`: 안내 박스, 카테고리 칩 → 필터 동작(네트워크 `category=` 확인), 투어 전용 가이드는 미노출, 카드 배지 "동행 파트너", 카드 클릭 → `/companions/{id}`(다음 태스크 전까지 404 허용). `/companions?category=DINING_COMPANION` 프리셋 동작.

---

### Task 9: 가이드 상세 → 트랙 인지 공유 뷰 + `/companions/[id]`

**Files:**
- Create: `app/frontend/src/components/ProfileDetailView.tsx` (guides/[id]/page.tsx 본문 이관)
- Modify: `app/frontend/src/app/guides/[id]/page.tsx` (thin wrapper)
- Create: `app/frontend/src/app/companions/[id]/page.tsx` (thin wrapper)

**Interfaces:**
- Produces: `<ProfileDetailView track="tour" | "companion" />` — 내부에서 `useParams()`로 id를 읽는다(기존과 동일). Task 10이 이 컴포넌트의 예약 위젯을 수정.

- [ ] **Step 1: 본문 이관** — `app/guides/[id]/page.tsx`의 내용 전체(1001행)를 `components/ProfileDetailView.tsx`로 이동. 기존 default export 함수명을 `ProfileDetailView`로 바꾸고 `{ track }: { track: "tour" | "companion" }` prop 추가. 파일 상단 `"use client"`·import 전부 그대로 가져간다(경로가 `@/` alias라 이동해도 수정 불요). 그런 다음 두 래퍼:

```tsx
// app/guides/[id]/page.tsx
"use client";
import ProfileDetailView from "@/components/ProfileDetailView";
export default function GuideDetailPage() { return <ProfileDetailView track="tour" />; }
```

```tsx
// app/companions/[id]/page.tsx
"use client";
import ProfileDetailView from "@/components/ProfileDetailView";
export default function CompanionDetailPage() { return <ProfileDetailView track="companion" />; }
```

**이관 직후 즉시 `npx tsc --noEmit` + 브라우저에서 기존 `/guides/{id}` 무회귀 확인 후 다음 스텝으로** (이동과 기능 변경을 한 커밋 단위로 섞지 않기).

- [ ] **Step 2: 트랙별 분기 추가** — `ProfileDetailView` 내부에서 guide 로드 완료 후:

```tsx
  const categories = guide?.serviceCategories ?? [];
  const hasTour = categories.includes("TOUR_GUIDE");
  const hasCompanion = categories.some((k) => k !== "TOUR_GUIDE");
  const trackCategories = categories.filter((k) =>
    track === "tour" ? k === "TOUR_GUIDE" : k !== "TOUR_GUIDE");
```

리다이렉트 가드(guide 로드 useEffect 안 또는 로드 직후):

```tsx
  // 이 트랙에서 제공하는 서비스가 없으면 반대 트랙 상세로
  useEffect(() => {
    if (!guide) return;
    if (track === "companion" && !hasCompanion && hasTour) router.replace(`/guides/${id}`);
    if (track === "tour" && !hasTour && hasCompanion) router.replace(`/companions/${id}`);
  }, [guide]); // eslint-disable-line react-hooks/exhaustive-deps
```

- [ ] **Step 3: 섹션 게이팅** — ① 투어 코스 상품 섹션(코스 카드/동선 보기)을 `{track === "tour" && (...)}`로 감싼다. ② 예약 위젯의 카테고리 셀렉트(438행 근방 `bookingCategorySelect`)의 옵션 소스를 `guide.serviceCategories` → `trackCategories`로 교체하고, `trackCategories.length === 1`이면 자동 선택(`useEffect`로 `setBookingCategory(trackCategories[0])`) + 셀렉트 숨김. ③ companion 트랙이면 예약 위젯 상단에 `<TrackNotice />` 렌더.

- [ ] **Step 4: 크로스링크** — 헤더(이름·배지 영역) 아래에:

```tsx
        {track === "tour" && hasCompanion && (
          <Link href={`/companions/${id}`} className="text-sm font-semibold text-sky-600 hover:underline">
            🤝 {t.companions.alsoCompanion} — {t.companions.viewCompanionProfile} →
          </Link>
        )}
        {track === "companion" && hasTour && guide.verificationStatus === "VERIFIED" && (
          <Link href={`/guides/${id}`} className="text-sm font-semibold text-emerald-600 hover:underline">
            🎫 {t.companions.alsoCertified} — {t.companions.viewTourProfile} →
          </Link>
        )}
```

(`GuideDetailResponse`에 `serviceCategories`·`verificationStatus`가 내려오는지 grep으로 확인 — Phase 2에서 예약 위젯 서비스 선택에 이미 쓰고 있으므로 존재할 것.)

- [ ] **Step 5: 검증** — tsc 0 에러. 브라우저: 투어+동행 겸업 인증 가이드로 두 상세 모두 열어 ① `/guides/{id}`: 코스 섹션 있음·예약 셀렉트=투어만·동행 크로스링크 ② `/companions/{id}`: 코스 섹션 없음·안내 박스·예약 셀렉트=동행만·투어 크로스링크 ③ 동행 전용 파트너의 `/guides/{id}` 접근 → `/companions/{id}` 리다이렉트. 리뷰·게시글·팔로우·DM 버튼 등 나머지 섹션 양쪽 렌더 무회귀.

---

### Task 10: 동행 예약 — 카테고리별 요청 폼

**Files:**
- Create: `app/frontend/src/lib/companionRequest.ts`
- Modify: `app/frontend/src/components/ProfileDetailView.tsx` (예약 위젯·postBookings)

**Interfaces:**
- Produces: `COMPANION_FIELDS: Record<string, readonly string[]>`, `buildRequestDetails(category, fields): string | null`, `parseRequestDetails(raw): Record<string,string> | null` — Task 11이 parse를 소비. 필드 키는 i18n `companionBooking.*` 키와 1:1.

- [ ] **Step 1: lib 작성**

```ts
// 동행 예약 카테고리별 요청 필드 규약 (프론트 전용 — 백엔드는 불투명 저장).
// 필드 키는 i18n `companionBooking.*` 라벨 키와 1:1로 일치해야 한다.
export const COMPANION_FIELDS: Record<string, readonly string[]> = {
  MEDICAL_INTERPRETER:  ["hospitalName", "hospitalPurpose"],
  SHOPPING_INTERPRETER: ["shoppingArea", "shoppingItems"],
  DINING_COMPANION:     ["diningFood", "diningRecommend"],
  CAFE_COMPANION:       ["cafeNote"],
  LANGUAGE_EXCHANGE:    ["languageTarget", "languageLevel"],
};

/** 체크박스형 필드 — 값은 "1"(체크) 또는 미포함. */
export const CHECKBOX_FIELDS: ReadonlySet<string> = new Set(["diningRecommend"]);

/** 비어있지 않은 필드만 JSON으로. 전부 비었으면 null(전송 생략). */
export function buildRequestDetails(category: string, fields: Record<string, string>): string | null {
  const keys = COMPANION_FIELDS[category];
  if (!keys) return null;
  const out: Record<string, string> = {};
  keys.forEach((k) => { const v = (fields[k] ?? "").trim(); if (v) out[k] = v; });
  return Object.keys(out).length ? JSON.stringify(out) : null;
}

/** 파싱 실패/비정형이면 null — 호출부는 raw 텍스트로 폴백 표시. */
export function parseRequestDetails(raw: string | null | undefined): Record<string, string> | null {
  if (!raw) return null;
  try {
    const o: unknown = JSON.parse(raw);
    if (o && typeof o === "object" && !Array.isArray(o)
        && Object.values(o).every((v) => typeof v === "string")) {
      return o as Record<string, string>;
    }
  } catch { /* fall through */ }
  return null;
}
```

- [ ] **Step 2: 예약 위젯에 필드 렌더** — `ProfileDetailView`에 state 추가: `const [reqFields, setReqFields] = useState<Record<string, string>>({});` — `bookingCategory` 변경 시 `setReqFields({})` 리셋(카테고리 셀렉트 onChange 및 Task 9의 자동선택 useEffect에서). `bookingCategorySelect` 아래(두 노출 지점 885·971행 근방 모두에 뜨도록 `bookingCategorySelect` JSX 정의부에 함께 묶는 게 안전)에:

```tsx
      {COMPANION_FIELDS[bookingCategory] && (
        <div className="mt-3 flex flex-col gap-2">
          <p className="text-xs font-semibold text-stone-500">
            {t.companionBooking.detailsTitle} <span className="font-normal">({t.companionBooking.optionalHint})</span>
          </p>
          {COMPANION_FIELDS[bookingCategory].map((k) =>
            CHECKBOX_FIELDS.has(k) ? (
              <label key={k} className="flex items-center gap-2 text-sm text-stone-700">
                <input type="checkbox" checked={reqFields[k] === "1"}
                  onChange={(e) => setReqFields((f) => ({ ...f, [k]: e.target.checked ? "1" : "" }))}
                  className="h-4 w-4 accent-sky-600" />
                {t.companionBooking[k as keyof typeof t.companionBooking]}
              </label>
            ) : (
              <input key={k} value={reqFields[k] ?? ""}
                onChange={(e) => setReqFields((f) => ({ ...f, [k]: e.target.value }))}
                placeholder={t.companionBooking[k as keyof typeof t.companionBooking]}
                className="input text-sm" />
            ))}
        </div>
      )}
```

- [ ] **Step 3: 전송에 포함** — `postBookings`(358행 근방)의 body에:

```tsx
          body: { guideId: Number(id), startAt: e.startAt, hours: e.hours,
                  serviceCategory: bookingCategory, message: bookingMsg,
                  requestDetails: buildRequestDetails(bookingCategory, reqFields) },
```

- [ ] **Step 4: 검증** — tsc 0 에러. 브라우저 `/companions/{id}`: 병원 카테고리 선택 → 기관명·목적 입력칸, 식사 → 음식 입력+추천 체크박스, 카테고리 전환 시 값 리셋. 예약 제출 → 네트워크 페이로드에 `requestDetails` JSON. 투어 트랙 예약은 `requestDetails: null` 전송 확인.

---

### Task 11: 예약 상세·요청 카드에 요청 내용 렌더

**Files:**
- Create: `app/frontend/src/components/RequestDetailsBlock.tsx`
- Modify: `app/frontend/src/app/bookings/[id]/page.tsx` (BookingResponse 타입 + 렌더)
- Modify: `app/frontend/src/app/guide/requests/page.tsx` (동일)

**Interfaces:**
- Consumes: `parseRequestDetails`/`CHECKBOX_FIELDS` (Task 10), `BookingResponse.requestDetails` (Task 1)

- [ ] **Step 1: 렌더 컴포넌트**

```tsx
"use client";

import { useLanguage } from "@/context/LanguageContext";
import { parseRequestDetails, CHECKBOX_FIELDS } from "@/lib/companionRequest";

/** 동행 예약 요청 내용 표시 — 예약 상세·가이드 요청 카드 공용. 값 없으면 null 렌더. */
export default function RequestDetailsBlock({ raw }: { raw?: string | null }) {
  const { t } = useLanguage();
  if (!raw) return null;
  const parsed = parseRequestDetails(raw);
  return (
    <div className="rounded-xl bg-stone-50 px-4 py-3">
      <p className="mb-1.5 text-xs font-semibold text-stone-500">{t.companionBooking.detailsTitle}</p>
      {parsed ? (
        <dl className="flex flex-col gap-1 text-sm">
          {Object.entries(parsed).map(([k, v]) => (
            <div key={k} className="flex gap-2">
              <dt className="shrink-0 font-semibold text-stone-600">
                {t.companionBooking[k as keyof typeof t.companionBooking] ?? k}
              </dt>
              <dd className="text-stone-800">{CHECKBOX_FIELDS.has(k) ? "✓" : v}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="text-sm text-stone-700">{raw}</p>
      )}
    </div>
  );
}
```

- [ ] **Step 2: 두 페이지에 삽입** — 각 페이지의 `BookingResponse`(또는 로컬 Booking 타입)에 `requestDetails?: string | null` 추가. `/bookings/[id]`는 메시지 표시 영역 아래에 `<RequestDetailsBlock raw={booking.requestDetails} />`. `/guide/requests`는 요청 카드의 message 아래 동일 삽입(정확한 위치는 `grep -n "message" 각 파일`로 앵커 확인).

- [ ] **Step 3: 검증** — Task 10에서 만든 동행 예약을 여행자 `/bookings/{id}`와 가이드 `/guide/requests`에서 열어 라벨:값 행 렌더 확인(3개 언어 라벨 전환). requestDetails 없는 투어 예약은 블록 자체가 안 뜸. DB에 수동으로 비정형 문자열을 넣은 경우 plain text 폴백(선택 확인).

---

### Task 12: become-guide — Step 0 자격 분기

**Files:**
- Modify: `app/frontend/src/components/ServiceCategoryPicker.tsx` (`hideTour` prop)
- Modify: `app/frontend/src/app/become-guide/page.tsx`

**Interfaces:**
- Produces: `ServiceCategoryPicker`에 `hideTour?: boolean` — true면 관광 그룹 자체를 렌더하지 않음(자물쇠 힌트도 없음).
- Consumes: `onboardingFork.*` 키 (Task 3)

- [ ] **Step 1: Picker prop** — Props에 `hideTour?: boolean` 추가, groups 계산을:

```tsx
  const groups = [
    ...(hideTour ? [] : [{ label: ls.tourGroup, items: SERVICE_CATEGORIES.filter((c) => c.requiresLicense) }]),
    { label: ls.nonTourGroup, items: SERVICE_CATEGORIES.filter((c) => !c.requiresLicense) },
  ];
```

- [ ] **Step 2: 분기 화면** — `BecomeGuidePage`에 state·프리셋:

```tsx
  const [licenseFork, setLicenseFork] = useState<"yes" | "no" | null>(null);
  useEffect(() => {
    const p = new URLSearchParams(window.location.search).get("license");
    if (p === "yes" || p === "no") setLicenseFork(p);
  }, []);
```

`return` 최상단에 분기 화면(폼보다 먼저):

```tsx
  if (licenseFork === null) {
    const f = t.onboardingFork;
    return (
      <main className="page px-4">
        <div className="container-sm">
          <div className="mb-8 text-center">
            <h1 className="section-title">{f.partnerTitle}</h1>
            <p className="section-subtitle">{f.partnerSub}</p>
          </div>
          <p className="mb-4 text-center text-lg font-bold text-stone-900">{f.question}</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <button onClick={() => setLicenseFork("yes")}
              className="card-hover flex flex-col gap-2 border-2 border-emerald-100 p-6 text-left">
              <span className="text-3xl">🎫</span>
              <span className="font-extrabold text-stone-900">{f.yes}</span>
              <span className="text-sm text-stone-500">{f.yesDesc}</span>
            </button>
            <button onClick={() => setLicenseFork("no")}
              className="card-hover flex flex-col gap-2 border-2 border-sky-100 p-6 text-left">
              <span className="text-3xl">🤝</span>
              <span className="font-extrabold text-stone-900">{f.no}</span>
              <span className="text-sm text-stone-500">{f.noDesc}</span>
            </button>
          </div>
        </div>
      </main>
    );
  }
```

- [ ] **Step 3: 경로별 폼 차이** — ① 페이지 헤더 카피를 `l.title`/`l.sub` → `t.onboardingFork.partnerTitle`/`partnerSub`로 교체. ② Sec 6의 Picker를 `<ServiceCategoryPicker selected={serviceCategories} onChange={setServiceCategories} verified={false} hideTour={licenseFork === "no"} />`로. ③ `licenseFork === "yes"`일 때 Sec 6 카드 안에 안내 박스:

```tsx
            {licenseFork === "yes" && (
              <p className="mb-3 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                🎫 {t.onboardingFork.licenseNote}
              </p>
            )}
```

④ 제출 성공 리다이렉트: `licenseFork === "yes"`면 `router.push("/guide/manage#verification")`(인증 신청 섹션으로 — `guide/manage`의 인증 섹션 래퍼에 `id="verification"` 추가, `grep -n verification app/guide/manage/page.tsx`로 앵커 확인), "no"면 기존 `/guide/manage`.

- [ ] **Step 4: 진입 CTA 프리셋** — `grep -rn "become-guide" src/app src/components`로 기존 링크를 찾아, 투어 트랙(`/guides` 헤더 등) 쪽 링크는 `/become-guide?license=yes`, 동행/일반 쪽은 그대로 `/become-guide`(분기 화면 표시). 링크가 랜딩·select-mode 등 중립 위치뿐이면 수정하지 않는다.

- [ ] **Step 5: 검증** — tsc 0 에러. 브라우저: `/become-guide` → 분기 화면, "아니요" → 관광 그룹 자체가 없는 Picker·자물쇠 힌트 없음 → 등록 성공(동행 카테고리 1개 이상) → `/guide/manage`. "네" → 관광 자물쇠 표시 + licenseNote 박스 → 등록 → `#verification` 앵커 도착. `?license=no` 딥링크 시 분기 화면 스킵. 3개 언어 렌더.

---

### Task 13: 파트너홈 서비스 선언 리마인더 배너

**Files:**
- Modify: `app/frontend/src/app/guide/page.tsx`

**Interfaces:**
- Consumes: Task 3의 `declareBanner`/`declareCta` 키(확정된 그룹명 기준), `GET /api/guide-profiles/me`

- [ ] **Step 1: 배너 추가** — `app/guide/page.tsx`에서 가이드 프로필 로드 여부 확인(`grep -n "guide-profiles/me"`). 이미 로드하면 그 응답의 `serviceCategories`를 쓰고, 안 하면 마운트 시 1회 fetch. 페이지 상단(프로필 헤더 아래)에:

```tsx
      {profileLoaded && serviceCategories.length === 0 && (
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
          <p className="text-sm font-semibold text-amber-800">⚠️ {t.guideDashboard.declareBanner}</p>
          <Link href="/guide/manage" className="btn-primary px-4 py-1.5 text-sm">{t.guideDashboard.declareCta}</Link>
        </div>
      )}
```

(프로필 자체가 없는 사용자는 이 페이지 기존 동작(become-guide 유도)을 따르므로 배너 제외 — `catch`에서 `profileLoaded`를 세우지 않는다.)

- [ ] **Step 2: 검증** — serviceCategories 미선언 테스트 가이드로 `/guide` → 배너 노출·CTA 이동, 선언 후 재방문 → 배너 없음.

---

### Task 14: 여행일정 통합 — 일차 CTA + companion 아이템 스타일

**Files:**
- Modify: `app/frontend/src/components/TimetableBuilder.tsx`
- Modify: `app/frontend/src/app/trips/[id]/page.tsx:143-146`

**Interfaces:**
- Produces: `TimetableBuilder`에 `dayCta?: React.ReactNode` prop — 일차 탭 행 옆(trip 모드에서만 의미)에 렌더.
- Consumes: `itinerary.findPartnerCta` (Task 3), 아이템 `category === "companion"` (Task 2)

- [ ] **Step 1: dayCta prop** — `TimetableBuilder` props에 `dayCta?: React.ReactNode` 추가, 일차 탭 행(`grep -n "dayIndex\|일차\|dayTabs" components/TimetableBuilder.tsx`로 탭 렌더 위치 확인) 오른쪽 끝에 `{dayCta}` 렌더(탭 row가 flex면 `ml-auto`).

- [ ] **Step 2: trip 페이지에서 전달** — `trips/[id]/page.tsx`의 `<TimetableBuilder ...>` 에:

```tsx
        <TimetableBuilder
          items={items} onItemsChange={setItems}
          city={city} mode="trip" minDayCount={minDayCount}
          dayCta={
            <Link href="/find" className="whitespace-nowrap text-xs font-semibold text-sky-600 hover:underline">
              🤝 {t.itinerary.findPartnerCta}
            </Link>
          }
        />
```

- [ ] **Step 3: companion 아이템 스타일** — `grep -n '"tour"' components/TimetableBuilder.tsx`로 amber 스타일 분기를 찾아, `category === "companion"`일 때 sky 톤(예: amber 클래스 문자열의 `amber` → `sky` 대응 리터럴) 분기 추가. 배지 텍스트는 아이템 placeName에 이미 이모지 라벨이 있으므로 별도 배지 불요.

- [ ] **Step 4: 검증** — tsc 0 에러. 브라우저 `/trips/{id}`: 일차 탭 옆 CTA → `/find` 이동. Task 2의 동행 예약 자동 추가 아이템이 sky 톤으로 렌더(투어 아이템 amber 무회귀). 코스 빌더(`/guide/courses`)는 dayCta 미전달로 무변화 확인.

---

### Task 15: '가이드' 단어 소탕 + 전체 E2E 검증

**Files:**
- Modify: 스윕 결과에 따른 카피 수정 (i18n 값 위주)

- [ ] **Step 1: 단어 스윕**

```bash
cd app/frontend/src
grep -rn "가이드" app/companions app/find lib/companionRequest.ts components/TrackEntryCards.tsx components/TrackNotice.tsx components/RequestDetailsBlock.tsx
```

Expected: 0건 (주석 포함). i18n에서 신규 그룹만 추출 검사: `companions`/`companionBooking`/`find` 그룹의 en 블록에 `Guide`, zh 블록에 `导游`가 없는지 육안 + grep. `tracks` 그룹은 인증 가이드를 지칭하는 문구가 있으므로 **예외**(투어 쪽 설명에만 등장하는지 확인).

- [ ] **Step 2: 정적 검증 일괄**

```bash
cd app/frontend && npx tsc --noEmit && npx next lint
cd ../backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle compileJava
```

Expected: 전부 클린.

- [ ] **Step 3: Playwright E2E** (스크래치패드의 기존 playwright 설치 재사용, localStorage에 `accessToken`/`mode`/`peerup_lang` 주입 방식 — HANDOFF §7):
  1. 게스트 랜딩: 투트랙 카드 렌더 → 동행 카드 숏컷 클릭 → `/companions?category=` 필터 적용
  2. 온보딩 "아니요" 경로: 신규 가입 → become-guide 분기 → 동행 등록 → `/companions` 목록에 노출(카테고리 선언했으므로)
  3. 온보딩 "네" 경로: 분기 → licenseNote 확인 → 등록 → `/guide/manage#verification` 도착, 사이드바에 "투어 코스" 메뉴 없음(미인증)
  4. 동행 예약: `/companions/{id}` → 병원 카테고리 → 기관명 입력 → 예약 생성 → `/bookings/{id}`와 `/guide/requests`에 요청 내용 행 렌더
  5. 투어 회귀: `/guides` 인증 가이드만 노출 + 기존 슬롯 예약 1건 성공(requestDetails null)
  6. 일정: 동행 즉시예약 → `/trips`에서 "🏥 병원 동행" companion(sky) 아이템 확인, 일차 CTA → `/find`
  7. 모바일 뷰포트(390×844): 여행자 하단 탭 5개(홈/찾기/메시지/여행일정/프로필) 스크린샷
  8. en/zh 전환: `/companions`·`/find`·분기 화면에 undefined 없음

- [ ] **Step 4: 테스트 데이터 기록** — 이번 검증에서 만든 테스트 계정/예약 id를 HANDOFF.md 관례대로 "실 dev DB 오염" 목록에 기록.

---

### Task 16: 문서 — IDEAS 백로그 + 진행 기록

**Files:**
- Modify: `IDEAS.md` (루트)
- Modify: `app/PROGRESS.md`, `HANDOFF.md`

- [ ] **Step 1: IDEAS.md 백로그 4건 추가** (스펙 §7):

```markdown
## 투트랙 후속 (2026-07-17 스펙에서 백로그로 미룸)
- [ ] 일정 기반 파트너 추천 — 여행 일정의 도시·날짜로 가능한 동행 파트너/가이드 매칭
- [ ] 일정에서 바로 예약 요청 — 일차 CTA에서 날짜·시간 프리필된 예약 폼으로 딥링크
- [ ] 예약 + 일정 통합 캘린더 뷰
- [ ] 공유한 일정을 보고 파트너가 견적·제안하는 플로우
- [ ] 동행 고정 패키지 상품 (예: "병원 동행 4시간" — TourCourse 패턴 재사용, 예약 모델 논의에서 보류)
```

- [ ] **Step 2: PROGRESS/HANDOFF 최상단에 이번 작업 요약 추가** — 기존 문서 관례(완료 항목·검증 방법·테스트 오염·남은 판단)를 따른다. 스펙/플랜 파일 경로를 명기.

---

## Self-Review 결과 (계획 작성 후 점검)

- **스펙 커버리지**: §4-1(T5·7·8·9·12) §4-2(T4) §4-3(T6) §5(T12·13) §6(T1·10·11) §7(T2·14·16) §8(T1·2) §9(T3·7·8·15) §10(T3) §11(T13, 라우트 유지로 자동 충족) §12(T15) — 전부 매핑됨.
- **타입 일관성**: `requestDetails`(T1↔T10↔T11), `track` prop(T8↔T9), `dayCta`(T14), `hideTour`(T12), i18n 키(T3↔T4~14) 교차 확인 완료.
- **의도적 순서**: T1·T2(백엔드) → T3(i18n) → T4~T14(프론트, 각각 독립 검증 가능) → T15(통합 검증) → T16(문서). T9 Step 1(파일 이동)은 기능 변경과 분리해 회귀 지점을 좁힌다.
