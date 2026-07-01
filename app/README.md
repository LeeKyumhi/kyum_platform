# Local Guide Match (가칭)

한국을 여행하는 외국인과 한국 현지인 가이드를 1:1로 잇는 **C2C 매칭 플랫폼**.

## 기술 스택

| 영역 | 기술 |
|---|---|
| 프론트엔드 | Next.js (React, TypeScript) + Tailwind CSS |
| 백엔드 | Spring Boot (Java 17, Gradle) — *예정* |
| DB / 저장소 | Supabase (PostgreSQL + Storage) |
| 인증 | Spring Security + JWT — *예정* |

## 폴더 구조

```
app/
├── frontend/   # Next.js 웹 앱
└── backend/    # Spring Boot API 서버 (예정)
```

## 프론트엔드 실행 방법

실행 후 브라우저에서 http://localhost:3000 접속.

## 진행 상황

- [x] 1. 데이터 모델 설계 (ERD)
- [~] 2. 프로젝트 뼈대 세팅 (프론트엔드 완료, 백엔드 예정)
- [ ] 3. 회원가입 · 로그인
- [ ] 4. 가이드 프로필 등록
- [ ] 5. 가이드 검색 · 목록 · 상세
- [ ] 6. 매칭 / 예약 요청
- [ ] 7. 실시간 채팅
- [ ] 8. 리뷰 + 결제
