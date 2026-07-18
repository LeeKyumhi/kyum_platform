# 트랙 월드 분리 — 진입 게이트 + 법적 안내 (2026-07-18)

## 목적
사용자가 앱에 **들어가자마자** 자격증 유무에 따른 두 세계 중 하나를 선택하게 한다.
- **동행 세계**: 여행지 투어·가이드와 **완전 무관**한 화면 (서비스 제공자든 여행자든).
- **투어 세계**: 진입 시 **법적 규정(무엇이 불법인지)** 고지를 필수 확인.

두트랙 1차 작업(feat/two-track, 17bef6b 머지)의 후속. 프론트 전용, 백엔드 변경 없음.

## 사용자 결정 (AskUserQuestion)
1. **진입 게이트 = 기억 + 쉬운 전환**: 첫 방문 전체화면 선택 → localStorage 저장 → 재방문 직행. 사이드바 상시 "⇄ 다른 서비스 보기".
2. **법적 안내 = 필수 확인 게이트 + 상시 페이지**: 투어 세계 진입 시 §38 고지 동의 게이트(1회, 기억) + 상시 `/legal` 페이지.
3. **기존 진입 컴포넌트(/find, TrackEntryCards)는 새 게이트로 대체·흡수**.
4. (코디네이터 확정, 실행 지시로 승인) 법적 게이트는 **투어 세계 진입자 전원**(여행자 포함) 대상. 제공자는 기존 hostTermsAgreedAt 서버 동의가 추가 커버.

## 아키텍처 (승인된 접근 A)
클라이언트 트랙 상태 + 트랙 인지 셸. 라우트 구조 변경 없음 — 기존 페이지 전부 재사용.

### ① `lib/track.ts` (신규 — mode.ts 미러)
- `type Track = "companion" | "tour"`, `getTrack/setTrack/clearTrack` (localStorage key `track`)
- 법적 동의: `getTourLegalAck(): boolean` / `ackTourLegal()` (localStorage key `tourLegalAckAt` = ISO timestamp)
- 변경 알림: 호출부가 `window.dispatchEvent(new Event("peerup-track-changed"))` (기존 `peerup-mode-changed` 관례)

### ② `components/TrackGate.tsx` (신규 — layout에 마운트)
- `usePathname`으로 **암묵 트랙 동기화**: `/companions*` → companion, `/guides*` → tour (딥링크 마찰 0)
- **선택 오버레이**: `track === null`이고 제외 라우트가 아니면 전체화면 chooser (🤝 동행 파트너 / 🎫 인증 가이드 투어 — `tracks.*` 키 재사용). z-[90] — LanguagePicker(z-100) 밑이라 첫 방문 순서 = 언어 → 역할 → 세계.
- **법적 게이트**: chooser에서 투어 선택 시 즉시 setTrack 하지 않고 법적 스텝 표시 → 체크박스 + 동의 후 `ackTourLegal()+setTrack("tour")`. 딥링크로 track==="tour"인데 미동의면 법적 스텝만 표시. "← 뒤로"는 chooser 복귀(clearTrack).
- 제외 라우트(오버레이 미표시): `/legal`, `/login`, `/signup`, `/forgot-password`, `/reset-password`, `/verify-email`, `/select-mode`

### ③ Sidebar 트랙 인지
- `getTrack()` state + `peerup-track-changed` 리스너
- 메뉴 필터: 동행 세계에서는 `/guides`(가이드 찾기)·`/guide/courses`(코스) 제거, 투어 세계에서는 `/companions` 제거. 공통 유지: 홈·커뮤니티·탐색·여행일정·메시지·예약·프로필 (여행일정 양세계 유지 = 기존 결정).
- 모바일 `/find` 탭 → 트랙별 목록(`/companions` 또는 `/guides`) 직행으로 교체
- 데스크탑 하단 + 모바일: **"⇄ 다른 서비스 보기"** → `clearTrack()` + 이벤트 + `router.push("/")` (chooser 재표시)
- 투어 세계에서만 ⚖️ `/legal` 링크 노출

### ④ 랜딩 분리 + 흡수
- `app/page.tsx`: `track === "companion"`이면 신규 `components/CompanionLanding.tsx` 렌더 (투어·가이드·명소 갤러리 흔적 0 — 동행 히어로, 카테고리 타일 → `/companions?category=`, 이용 방법, 여행일정 카드, 파트너 되기 CTA → `/become-guide?license=no`). 그 외(투어/미선택)는 기존 랜딩 유지.
- 랜딩·여행자홈의 `TrackEntryCards` 삽입 제거, 컴포넌트 삭제.
- `/find` → `clearTrack()` 후 `/`로 redirect (세계 전환 딥링크로 유지). trips 일차 CTA는 `/companions` 직행으로 변경.

### ⑤ `/legal` 페이지 (신규, 공개)
상시 법적 안내: ① 법적 근거(관광진흥법 §38, 과태료 150/300/500만) ② 무엇이 불법인가(무자격 유상 관광안내 = 명소·문화유산 해설 등, 자격 대여) ③ 허용되는 동행 서비스(식사·쇼핑·병원·카페·언어교환) ④ 플랫폼 정책(인증 배지, 신고, 위반 시 조치). i18n `legalPage.*` ko/en/zh.

## i18n (전 키 ko/en/zh 필수)
- `trackChooser`: title, sub / `tourLegal`: gateTitle, gateSub, point1~3, checkLabel, agreeBtn, backBtn, detailLink / `legalPage`: title, sub, sec1~4 Title+Body / `nav`: legal, switchService. (chooser 카드 본문은 기존 `tracks.*` 재사용)

## 카피 규칙 (기존 유지)
동행 컨텍스트에서 가이드/Guide/导游 금지. 인증 가이드 교차참조·법적 설명 내 지칭은 허용.

## 검증
`tsc --noEmit` 0 / `next lint` 클린 / 동행 세계 '가이드' 스윕 / 시나리오: 첫 방문 chooser → 동행 선택 → 사이드바·랜딩에 투어 흔적 0 / 투어 선택 → 법적 게이트 → 동의 후 진입, 재방문 직행 / `/companions` 딥링크 = 게이트 없음 / `/guides` 딥링크 = 미동의 시 법적 게이트.

## 비범위
백엔드 변경, 결제, 트랙별 계정 분리, 서버 저장 트랙 선호.
