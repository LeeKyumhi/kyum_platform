# 트랙 월드 분리 구현 계획 (2026-07-18)

Spec: `docs/superpowers/specs/2026-07-18-track-worlds-design.md`
Branch: `feat/track-worlds` (base = main 17bef6b). 태스크별 커밋, main 머지는 사용자 요청 시에만.
실행: 코디네이터 직접(인라인). 검증 = `npx tsc --noEmit`(app/frontend) + `npx next lint` + 스윕.

## Global Constraints
- i18n 신규 키는 ko/en/zh 3블록 동일 세트 (구조적 타입이 누락을 tsc 에러로 강제)
- 새 npm 패키지 금지, 백엔드 변경 금지
- 동행 컨텍스트 카피에 가이드/Guide/导游 금지
- 스테이징은 태스크가 만진 파일만 (`git add -A` 금지)

## Tasks
1. **T1 — lib/track.ts + i18n**: Track 타입/스토리지/법적동의 헬퍼 + `trackChooser`/`tourLegal`/`legalPage`/`nav.legal`/`nav.switchService` 키 3개 언어.
2. **T2 — TrackGate + layout**: chooser 오버레이(z-90) + 법적 스텝 + 암묵 트랙 동기화 + 제외 라우트. `layout.tsx`에 `<TrackGate />` 마운트.
3. **T3 — Sidebar**: track state+이벤트, 메뉴 필터(동행: guides/courses 제거, 투어: companions 제거), 모바일 find 탭 교체, ⇄ 전환 버튼, 투어 세계 ⚖️ legal 링크.
4. **T4 — 랜딩 분리 + 흡수**: `CompanionLanding.tsx` 신규, page.tsx 분기, TrackEntryCards 제거(랜딩·여행자홈·컴포넌트 삭제), `/find` redirect화, trips dayCta → `/companions`.
5. **T5 — /legal 페이지**: 4개 섹션 정적 페이지(공개).
6. **T6 — 검증/문서**: tsc·lint·스윕 + 시나리오 점검, PROGRESS/HANDOFF 짧은 갱신.
