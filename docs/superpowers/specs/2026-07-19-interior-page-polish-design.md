# 내부 페이지 프리미엄 폴리시 마감 — Tier 1 (2026-07-19)

## 배경

`ab129e0`·`221fde4`·`62fc880` 3개 커밋으로 "premium travel editorial" 리브랜딩을 진행했다.
지금까지 닿은 곳:

- **랜딩(`page.tsx`)** — 사진 히어로(ken-burns drift + gradient scrim + frosted badge), eyebrow 라벨, gradient accent bar, `.card`/`.card-hover` 그리드, gradient 아이콘 타일, snap shelf, `animate-fade-up` 진입.
- **전역 크롬** — `Sidebar`, `Footer`.
- **색상 마이그레이션** — 거의 모든 페이지·컴포넌트가 이미 sky/stone/gradient 브랜드 토큰 사용 (구식 gray/indigo straggler 정리 완료).

**남은 갭은 색이 아니라 "폴리시"다.** 스캔 결과 랜딩을 제외한 모든 페이지가 eyebrow / `animate-fade-up` / `.card-hover` / 히어로 패턴을 사실상 안 쓴다. 색은 맞지만 랜딩이 가진 편집 디자인의 위계·리듬·진입감이 내부에 없다.

이 작업은 **새 비주얼 방향을 정하는 게 아니라 이미 결정·배포된 어휘를 내부로 적용하는 것**이다. 그래서 스펙은 가볍다.

## 목표

Tier 1(여행자 핵심 퍼널) 페이지에 랜딩의 프리미엄 어휘를 일관되게 적용해, 사용자가 "여긴 아직 리디자인 안 됐네"라고 느끼는 화면을 없앤다.

## 결정 사항 (확정)

- **범위: Tier 1만.** Tier 2(가이드 사이드)·Tier 3(auth/유틸)는 각각 별도 스펙/플랜으로 이어서.
- **i18n: 신규 문자열 0.** 직전 리브랜딩 커밋의 원칙 유지. 기존 title/subtitle 키를 그대로 쓰고, eyebrow는 자연스러운 기존 짧은 키가 있을 때만 재사용, 없으면 생략(accent bar + title만으로도 프리미엄감 확보).
- **admin 제외.** admin은 의도적으로 별도 포탈 셸을 가짐(`2460168`). 소비자용 에디토리얼 룩을 강제하면 셸과 충돌하므로 이번 범위 밖.

## 실사 결과 (2026-07-19 코드 대조 — 스펙 1차안 수정 근거)

Tier 1 파일을 전수 확인한 결과, 1차안의 가정보다 상태가 좋다:

- **이미 잘 된 것**: 콘텐츠 형태 스켈레톤(`.card animate-pulse` — guides/trips/explore/traveler-bookings), 카드형 빈 상태 + gradient 아이콘 타일(trips/explore/traveler-bookings), `GuideCard`·trips 목록의 `card-hover`, profile 히어로 배너, bookings/[id] 카드 위계.
- **진짜 갭**:
  1. **헤더 불일치** — accent bar 헤더는 guides·companions만. trips/explore/traveler-bookings는 plain(back+title).
  2. **`animate-fade-up` 전무** — 랜딩 외 어디에도 진입 애니메이션 없음.
  3. **companions 페이지** — 로딩이 `…` 텍스트, 빈 상태가 bare `<p>`. Tier 1 중 최악.
  4. **guides 빈 상태** — 카드형 아님(plain div) — 나머지 페이지와 불일치.
  5. **explore 장소 카드** — `card cursor-pointer hover:shadow-md` ad-hoc 조합(`card-hover` 미사용).

→ 작업의 본질은 "리디자인"이 아니라 **기존에 이미 좋은 패턴의 정규화 + 진입 애니메이션 부여**다.

## 폴리시 레시피 (페이지별 "완료" 정의)

1. **페이지 헤더** — `PageHeader` 컴포넌트로 gradient accent bar + title + subtitle(+back/action) 통일.
2. **진입 애니메이션** — 페이지 콘텐츠 컨테이너에 `animate-fade-up`. `prefers-reduced-motion`은 globals.css가 이미 가드.
3. **카드 위계** — 클릭 가능한 카드 = `.card-hover`, 정적 = `.card`. ad-hoc hover 조합 수렴.
4. **빈 상태** — `EmptyState` 컴포넌트(기존 3곳의 검증된 패턴 추출: `.card` + gradient 아이콘 타일 + title/message/CTA)로 통일.
5. **로딩** — ~~PageLoading 컴포넌트~~ **만들지 않는다.** 기존 콘텐츠 형태 스켈레톤이 제네릭 컴포넌트보다 낫다. companions만 `…` 텍스트를 guides식 스켈레톤으로 교체.

## 구조적 레버 — 공유 컴포넌트 신규 추출 (2개)

### `components/PageHeader.tsx` (신규)
```
type Props = {
  title: React.ReactNode;        // guides처럼 제목 옆 배지 버튼 허용
  subtitle?: string;
  accent?: "sky" | "emerald";    // 기본 sky. companions = emerald
  back?: { href: string; label: string };  // 기존 btn-ghost back 링크
  action?: React.ReactNode;      // 우측 정렬 (선택)
};
```
렌더: `animate-fade-up mb-5` 래퍼에 accent bar(`h-1.5 w-10 rounded-full bg-gradient-to-r`) → (back +) title → subtitle. guides·companions의 기존 마크업을 그대로 추출. eyebrow는 신규 i18n 0 원칙에 따라 미지원(불필요 확인됨 — accent bar + title로 충분).

### `components/EmptyState.tsx` (신규)
```
type Props = {
  icon: React.ReactNode;         // 이모지 또는 아이콘
  title?: string;                // bold 제목 (선택 — traveler/bookings 패턴)
  message: string;
  action?: React.ReactNode;      // CTA (선택)
  accent?: "sky" | "emerald";
};
```
trips/explore/traveler-bookings에서 3번 반복된 마크업(`.card p-8 py-16 text-center` + `h-14 w-14 rounded-2xl gradient` 타일)을 추출.

> **레버리지 주의:** `guides/[id]`·`companions/[id]` → **`ProfileDetailView`**, `messages/[id]`·`chat` → `ChatRoom`에 위임. 페이지 파일이 아니라 렌더되는 컴포넌트를 다듬어야 실제로 폴리시된다.

## Tier 1 인벤토리 (실사 반영)

| 대상 | 작업 |
|------|-----|
| `app/guides/page.tsx` | PageHeader, 빈 상태→EmptyState, fade-up |
| `app/companions/page.tsx` | PageHeader(emerald), 로딩→스켈레톤, 빈 상태→EmptyState, fade-up |
| `app/explore/page.tsx` | PageHeader(back), 장소 카드→card-hover, 빈 상태 2곳→EmptyState, fade-up |
| `app/trips/page.tsx` | PageHeader(back), 빈 상태→EmptyState, fade-up |
| `app/traveler/bookings/page.tsx` | PageHeader(back), 빈 상태→EmptyState, fade-up |
| `app/bookings/[id]/page.tsx` | fade-up만 (카드 위계 이미 좋음; back+상태배지 행은 특수 헤더라 유지) |
| `app/profile/page.tsx` | fade-up만 (히어로 배너 이미 좋음) |
| `app/trips/[id]/page.tsx` | fade-up만 (빌더 헤더는 특수; 드래그 UI 무변경) |
| `components/ProfileDetailView.tsx` | 진입부 fade-up + 명백한 위계 불일치만 (전면 개편 안 함) |

## 범위 밖 (Non-goals)

- Tier 2·Tier 3 페이지 (별도 플랜).
- admin 포탈.
- 신규 i18n 문자열.
- 기능/데이터/라우팅 변경 — **순수 프레젠테이션.** 백엔드 무변경.
- 빌더 내부 드래그 수학·`ChatRoom` 메시지 로직 등 검증된 상호작용 로직 (헤더/셸만 손봄).
- 새 npm 패키지.

## 검증

- `npx tsc --noEmit` 0, `next lint` 클린(기존 `explore/page.tsx` 경고 1건 외).
- **`npm run dev`로 Tier 1 각 페이지 눈으로 확인** — grep 재검색으로 "완료" 선언하지 않는다. 3언어(ko/en/zh) 렌더 깨짐 없음, 모바일/데스크탑 반응형 확인.
- 기존 동작 회귀 없음(필터/정렬/예약/드래그 등 상호작용 그대로).

## 열린 질문 / 리스크

- `PageHeader` eyebrow를 "기존 키 재사용"으로 제한하면 일부 페이지는 eyebrow 없이 갈 수 있음 — 의도된 우아한 degrade(accent bar + title로 충분).
- `ProfileDetailView`는 큼(1070L) — 헤더/섹션 위계만 손보고 전면 리팩터는 안 함(범위 밖).
