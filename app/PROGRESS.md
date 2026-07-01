# 개발 진행 상황 (이어서 작업용 메모)

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
