# 가이드 시급 직접 설정 — Design

**작성일**: 2026-07-28
**상태**: 사용자 승인 완료 (범위 1,000~1,000,000원, ±50% 확인 포함)

## 문제

가이드가 자기 시급을 바꿀 방법이 없다. 시급은 가이드 프로필 생성(`POST /api/guide-profiles`) 때 한 번 정해지고 그 뒤로 수정 경로가 아예 없다. `/profile`과 `/guide/manage` 두 화면이 시급을 **읽기 전용으로 보여주기만** 한다.

## 목표

가이드가 `/profile`(하단 탭 👤 프로필)과 `/guide/manage`(프로필관리) 양쪽에서 자기 시급을 직접 바꾼다.

## 이미 보장되는 것 (설계 전제)

기존 예약 금액은 시급을 바꿔도 변하지 않는다. `BookingService`가 예약 생성 시점의 시급을 `hourly_rate_snapshot`으로 복사해두기 때문이다(`BookingService.java:81`, `int rateSnapshot = guide.getHourlyRate();`). 시급 변경은 **앞으로 생성될 예약부터** 적용된다.

이건 우연이 아니라 프로젝트 제약이다 — CLAUDE.md: "Price is snapshotted at booking time — `hourly_rate_snapshot` must never be derived live". 회귀 테스트로 고정한다.

## 설계

### 백엔드

**엔드포인트**: `PATCH /api/guide-profiles/me/hourly-rate` → `GuideProfileResponse`

기존 단일 필드 PATCH들(`/me/active`, `/me/instant-booking`, `/me/personality`, `/me/service-categories`, `/me/location`)과 같은 패턴을 따른다. 범용 프로필 수정 엔드포인트를 새로 만들지 않는다 — 이 기능 하나 때문에 지금 없는 추상화를 들이지 않는다.

**서비스**: `GuideProfileService.updateHourlyRate(Long userId, Integer rate)`

- 본인(`userId`)의 프로필만 조회·수정
- **범위 검증: 1,000원 이상 1,000,000원 이하.** 벗어나면 `IllegalArgumentException`(기존 에러 처리 규약대로 400 + `{"error": ...}`)
- `currency`는 건드리지 않는다

**검증의 권위는 서버에 있다.** 프론트 검증은 UX용이며, 서버가 독립적으로 다시 막는다.

### 프론트

**공용 컴포넌트**: `components/HourlyRateEditor.tsx`

`/profile`과 `/guide/manage`가 같이 쓴다. 검증 규칙과 확인 문구가 한 곳에만 존재해야 한다 — 두 벌로 갈라지면 한쪽만 고쳐지는 사고가 난다.

- Props: 현재 시급, 통화, 저장 성공 콜백
- 편집 방식: `/profile`의 기존 닉네임 인라인 편집과 같은 모양(연필 버튼 → 입력창 + 저장/취소)
- 범위 밖이면 저장 시 입력창 아래 빨간 문구로 이유를 알린다(저장 버튼을 미리 비활성화하지 않는다 — 왜 안 되는지 보여주는 편이 낫다)
- **확인 한 번**: 새 시급이 기존 시급 대비 50%를 초과해 오르거나 내릴 때(즉 `|new - old| > old × 0.5`) 확인을 받는다. 기존 금액과 새 금액을 둘 다 보여준다("50,000원 → 120,000원으로 바꿉니다. 계속할까요?"). 기존 코드가 쓰는 `confirm()` 방식을 따른다
- i18n 키는 ko/en/zh 3개 언어 전부 채운다 (프로젝트 제약)

### 범위 밖 (하지 않는 것)

- **통화 편집 없음.** 지금 KRW 고정이고, 통화를 바꾸면 기존 예약·정산 금액의 해석이 흔들린다
- **변경 이력·알림 없음.** 누가 언제 얼마로 바꿨는지 기록하거나 팔로워에게 알리지 않는다. 필요해지면 그때 별건으로
- **관리자 승인 없음.** 가이드가 바꾸면 즉시 반영된다

## 테스트

**백엔드 (단위)**
- 경계값: 999 거부 / 1,000 통과 / 1,000,000 통과 / 1,000,001 거부
- 남의 프로필은 수정 불가
- 프로필 없는 사용자 → 명확한 예외
- **회귀: 시급을 바꿔도 기존 예약의 `hourly_rate_snapshot`과 `total_price`가 변하지 않는다**

**프론트**
테스트 인프라가 없으므로 `npx tsc --noEmit` + `npm run build`로 검증한다.

## 열린 질문

없음.
