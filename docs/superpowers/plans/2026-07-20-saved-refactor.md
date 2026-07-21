# 저장됨 리팩터 (Phase 0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 저장됨에서 가이드를 제거(코스·장소만)하고, 사이드바에서 `/saved`를 빼고, 하단 프로필(`/profile`)에서 인스타처럼 저장물을 확인하게 한다.

**Architecture:** `SavedItemType.GUIDE` 상수·기존 행은 **보존**하고(파괴적 삭제 금지, `@Enumerated(STRING)`이라 ordinal 손상 없음), 읽기/쓰기 경로에서만 GUIDE를 제외한다. 프론트는 `lib/saved.ts` 타입에서 GUIDE를 빼고 가이드 저장 UI를 제거하며, `/saved` 3탭 페이지를 `/profile`의 `저장됨` 탭(코스·장소 그리드)으로 흡수하고 `/saved`는 리다이렉트로 남긴다.

**Tech Stack:** Spring Boot 3.3.5 / Java 21 / JUnit5+Mockito (백엔드), Next.js 15 / React 19 / TypeScript / Tailwind (프론트). 프론트는 유닛 테스트 하네스가 없어 검증은 `tsc --noEmit` + `next lint` + 수동 스모크.

## Global Constraints

- **No new npm packages** — 프론트 번들 유지.
- **Never drop DB columns/tables/enum constants** without user confirmation — `SavedItemType.GUIDE` 상수와 기존 GUIDE 행은 보존.
- **i18n 모든 키 ko/en/zh 3개 언어 필수** (`app/frontend/src/lib/i18n.ts`).
- **Backend Java 21**: `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` 후 `gradle` 실행.
- 프론트 검증 = `cd app/frontend && npx tsc --noEmit && npx next lint`.

---

### Task 1: 백엔드 — GUIDE 저장 쓰기 거부 + 읽기에서 제외

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/saved/SavedItemController.java` (`save`, `counts`)
- Modify: `app/backend/src/main/java/com/guidematch/saved/dto/SavedIdsResponse.java` (guideIds 제거)
- Modify: `app/backend/src/main/java/com/guidematch/saved/dto/SavedListResponse.java` (guides 제거)
- Modify: `app/backend/src/main/java/com/guidematch/saved/SavedItemService.java` (`myIds`, `myList`에서 GUIDE 스킵)
- Test: `app/backend/src/test/java/com/guidematch/saved/SavedItemServiceTest.java`

**Interfaces:**
- Produces: `SavedIdsResponse(List<Long> courseIds, List<String> placeRefs)` — 프론트 `SavedIds` 계약.
- Produces: `SavedListResponse(List<TourCourseResponse> courses, List<SavedPlaceResponse> places)` — `/saved` 목록 계약.
- Produces: `POST /api/saved`에 `itemType=GUIDE` → 400; `GET /api/saved/counts?type=GUIDE` → 400.

- [ ] **Step 1: 실패하는 테스트로 수정** — `SavedItemServiceTest`에서 GUIDE 제외 계약을 검증하도록 세 테스트를 바꾼다.

`myIds_타입별_분류`를 다음으로 교체 (guideIds accessor 제거 반영):

```java
    @Test
    void myIds_가이드는_제외하고_코스_장소만() {
        SavedItem g = new SavedItem(1L, SavedItemType.GUIDE, 10L);   // 레거시 행 — 제외돼야 함
        SavedItem c = new SavedItem(1L, SavedItemType.COURSE, 20L);
        SavedItem p = new SavedItem(1L, "kakao:123", "장소", null, null, null, null, null);
        when(savedItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(g, c, p));

        SavedIdsResponse ids = service.myIds(1L);

        assertEquals(List.of(20L), ids.courseIds());
        assertEquals(List.of("kakao:123"), ids.placeRefs());
    }
```

`myList_사라진_가이드와_비활성_코스는_제외`에서 가이드 관련 stub·단언을 제거하고 코스·장소만 남긴다:

```java
    @Test
    void myList_비활성_코스는_제외하고_장소는_포함() {
        SavedItem c = new SavedItem(1L, SavedItemType.COURSE, 20L);  // 비활성
        SavedItem p = new SavedItem(1L, "gyeongbokgung", "경복궁", "명소", null, 37.5, 126.9, null);
        when(savedItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(c, p));

        TourCourse inactive = new TourCourse(5L, "코스", null, "서울", 3, 50000, "KRW", 4, null, null);
        ReflectionTestUtils.setField(inactive, "id", 20L);
        inactive.setActive(false);
        when(courseRepository.findAllById(List.of(20L))).thenReturn(List.of(inactive));

        SavedListResponse list = service.myList(1L);

        assertTrue(list.courses().isEmpty());
        assertEquals(1, list.places().size());
        assertEquals("경복궁", list.places().get(0).name());
    }
```

`myList_활성_코스는_가이드_이름과_함께_포함`에서 `list.guides()` 단언이 있으면 제거(코스·장소 단언만 유지).

**GUIDE 저장 경로 완전 제거**(dead code 방지): `SavedItemService.saveGuide(...)` 메서드를 삭제하고, 그에 딸린 서비스 테스트 3개(`가이드_저장_성공`, `가이드_중복_저장은_무시`, `없는_가이드_저장은_예외`)도 삭제한다. `unsave`/`저장_해제`/`없는_항목_해제는_무시` 테스트가 `SavedItemType.GUIDE`를 참조하는 것은 enum 상수가 보존되므로 그대로 둔다(레거시 행 해제 경로 유지). `saveGuide`가 참조하던 `GuideProfileRepository`가 서비스에서 더는 안 쓰이면 생성자·필드에서 제거하고, 안 쓰이게 된 import도 정리한다. (`myList`의 코스 guideName 조회에 여전히 `profileRepository`가 쓰이면 유지 — 실행자가 현재 본문을 읽고 판단.)

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests '*SavedItemServiceTest' 2>&1 | tail -20
```
Expected: 컴파일 실패 — `SavedIdsResponse`에 여전히 `guideIds`가 있고, 테스트가 `ids.guideIds()`를 부르지 않으면서 `SavedListResponse.guides()` 참조가 제거돼 불일치. (수정 전이라 FAIL)

- [ ] **Step 3: DTO에서 GUIDE 필드 제거**

`SavedIdsResponse.java`:

```java
public record SavedIdsResponse(
        List<Long> courseIds,
        List<String> placeRefs
) {}
```

`SavedListResponse.java` (미사용 import `FollowingGuideResponse` 제거):

```java
package com.guidematch.saved.dto;

import com.guidematch.guide.dto.TourCourseResponse;

import java.util.List;

/** /saved 페이지용 — 저장 목록 2종 일괄 (각각 최신순). 가이드 저장은 폐기(Phase 0). */
public record SavedListResponse(
        List<TourCourseResponse> courses,
        List<SavedPlaceResponse> places
) {}
```

- [ ] **Step 4: 서비스 `myIds`/`myList`에서 GUIDE 스킵 + 생성자 시그니처 반영**

`SavedItemService.myIds`를 GUIDE 미수집으로 수정 (courseIds/placeRefs만 채우고 `new SavedIdsResponse(courseIds, placeRefs)` 반환). `myList`를 guides 미조립으로 수정 (GUIDE 아이템은 순회에서 무시, `new SavedListResponse(courses, places)` 반환 — `profileRepository`/`userRepository`를 가이드 이름 조회에만 쓰던 경로가 있으면 코스의 guideName 조회용으로만 유지). 실행자는 현재 메서드 본문을 읽고 GUIDE 분기·guides 리스트 조립부만 제거한다.

- [ ] **Step 5: 컨트롤러에서 GUIDE 쓰기/카운트 거부**

`SavedItemController.save`의 switch에서 GUIDE를 거부로 교체:

```java
        switch (type) {
            case GUIDE -> throw new IllegalArgumentException("가이드는 더 이상 저장할 수 없습니다.");
            case COURSE -> savedItemService.saveCourse(userId, requireRefId(req.refId()));
            case PLACE -> {
                if (req.place() == null) throw new IllegalArgumentException("장소 정보가 없습니다.");
                var p = req.place();
                savedItemService.savePlace(userId, p.ref(), p.name(), p.category(),
                        p.address(), p.lat(), p.lng(), p.image());
            }
        }
```

`counts`에서 GUIDE도 거부(장소와 동일하게):

```java
        SavedItemType t = parseType(type);
        if (t != SavedItemType.COURSE) throw new IllegalArgumentException("저장수는 코스만 지원합니다.");
```

- [ ] **Step 6: 테스트 통과 + 컴파일 확인**

```bash
cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test --tests '*SavedItemServiceTest' compileJava 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, SavedItemServiceTest 전부 PASS.

- [ ] **Step 7: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/saved app/backend/src/test/java/com/guidematch/saved
git commit -m "feat(saved): drop GUIDE from saved write/read paths (constant+rows preserved)"
```

---

### Task 2: 프론트 — saved 라이브러리/버튼에서 GUIDE 제거 + 가이드 저장 UI 제거 + 사이드바 정리

**Files:**
- Modify: `app/frontend/src/lib/saved.ts` (`SavedIds`, `SaveTarget`, `fetchSaveCounts`)
- Modify: `app/frontend/src/components/SaveButton.tsx` (GUIDE 분기 제거)
- Modify: `app/frontend/src/components/GuideCard.tsx:47` (SaveButton 제거)
- Modify: `app/frontend/src/app/guides/page.tsx` (`fetchSaveCounts("GUIDE")` + `guideSaveCounts` state 제거, 저장수 뱃지 제거)
- Modify: `app/frontend/src/components/Sidebar.tsx` (line 141 `/saved` 메뉴 제거 — traveler·guest 블록 모두)

**Interfaces:**
- Consumes: Task 1의 `SavedIds = {courseIds, placeRefs}` 계약.
- Produces: `SaveTarget = { itemType: "COURSE"; refId } | { itemType: "PLACE"; place }`, `fetchSaveCounts(type: "COURSE", ids)`.

- [ ] **Step 1: `lib/saved.ts` 타입 정리**

```ts
export type SavedIds = { courseIds: number[]; placeRefs: string[] };
const EMPTY: SavedIds = { courseIds: [], placeRefs: [] };

export type SaveTarget =
  | { itemType: "COURSE"; refId: number }
  | { itemType: "PLACE"; place: SavedPlaceSnapshot };
```

`fetchSaveCounts` 시그니처의 타입 유니언을 `"COURSE"`만으로:

```ts
export async function fetchSaveCounts(
  type: "COURSE",
  ids: number[]
): Promise<Record<string, number>> {
```

- [ ] **Step 2: `SaveButton.tsx` GUIDE 분기 제거** — line 30의 `else if (target.itemType === "GUIDE") ...`를 삭제하고, 초기 상태 판정을 COURSE/PLACE만 남긴다 (PLACE 먼저 판정 후 나머지는 COURSE):

```tsx
        if (target.itemType === "PLACE") setSaved(ids.placeRefs.includes(target.place.ref));
        else setSaved(ids.courseIds.includes(target.refId)); // COURSE
```

- [ ] **Step 3: 가이드 저장 UI 제거**
  - `GuideCard.tsx:47`의 `<SaveButton target={{ itemType: "GUIDE", ... }} .../>` 줄과, 사용 안 하게 된 `SaveButton` import 제거.
  - `guides/page.tsx`: `guideSaveCounts` state, `fetchSaveCounts("GUIDE", ...)` 호출 2곳(line 104·149), 가이드 카드에 저장수 뱃지를 넘기던 prop 제거. `import { fetchSaveCounts }`는 COURSE 카운트에 계속 쓰이면 유지.

- [ ] **Step 4: 사이드바에서 `/saved` 제거** — `Sidebar.tsx` line 141의 `it("/saved", "❤️", n.saved, under("/saved"))` 항목을 traveler·guest 메뉴 배열에서 삭제.

- [ ] **Step 5: 타입/린트 검증**

```bash
cd app/frontend && npx tsc --noEmit && npx next lint 2>&1 | tail -15
```
Expected: 타입 에러 0. (기존 `explore/page.tsx` 경고 1건 외 클린)

- [ ] **Step 6: 커밋**

```bash
git add app/frontend/src/lib/saved.ts app/frontend/src/components/SaveButton.tsx app/frontend/src/components/GuideCard.tsx app/frontend/src/app/guides/page.tsx app/frontend/src/components/Sidebar.tsx
git commit -m "feat(saved): remove guide save UI + /saved nav entry"
```

---

### Task 3: 프론트 — 프로필 `저장됨` 탭(인스타 그리드) + `/saved` 리다이렉트

**Files:**
- Create: `app/frontend/src/components/SavedGrid.tsx` (코스·장소 인스타 그리드 — `/saved`와 프로필 탭 공용)
- Modify: `app/frontend/src/app/profile/page.tsx` (게시글|저장됨 탭 섹션 추가)
- Modify: `app/frontend/src/app/saved/page.tsx` (`/profile?tab=saved`로 리다이렉트, 또는 `SavedGrid` 재사용한 얇은 페이지)
- Modify: `app/frontend/src/lib/i18n.ts` (`t.profilePage.savedTab`, `t.profilePage.postsTab` ko/en/zh)

**Interfaces:**
- Consumes: `GET /api/saved` → `SavedListResponse` = `{ courses, places }` (Task 1).
- Produces: `<SavedGrid />` — 인증 사용자의 저장 코스·장소를 정사각 썸네일 그리드로 렌더, `SAVED_CHANGED_EVENT` 구독 재조회.

- [ ] **Step 1: `SavedGrid.tsx` 작성** — `GET /api/saved`(auth) 호출해 `courses`·장소를 2~3열 인스타 그리드로. 코스 타일은 코스 제목+대표 이미지(있으면), 장소 타일은 `image`(없으면 카테고리 이모지 폴백)+이름. 각 타일에 해제(♡) 오버레이(`SaveButton` 재사용, COURSE/PLACE만). `SAVED_CHANGED_EVENT` 리스너로 재조회. 빈 상태는 `EmptyState` 재사용. 타입:

```tsx
type SavedPlace = {
  placeRef: string; name: string; category: string | null; address: string | null;
  latitude: number | null; longitude: number | null; image: string | null; createdAt: string;
};
type SavedList = { courses: CourseCardData[]; places: SavedPlace[] };
```

- [ ] **Step 2: 프로필 페이지에 탭 섹션 추가** — `profile/page.tsx` 하단에 `게시글`|`저장됨` 탭 상태(`const [tab, setTab] = useState<"posts"|"saved">("posts")`). `저장됨` 선택 시 `<SavedGrid />` 렌더. 초기 탭은 `?tab=saved` 쿼리로 진입 가능하게(`useSearchParams`). `게시글` 탭은 기존 프로필 콘텐츠/사용자 게시글(있으면) 유지.

- [ ] **Step 3: `/saved` 라우트를 리다이렉트로 축소** — `saved/page.tsx`를 `useRouter().replace("/profile?tab=saved")`만 수행하는 얇은 클라이언트 리다이렉트로 교체(기존 3탭·가이드 탭 로직 제거). 딥링크·기존 링크 보존.

- [ ] **Step 4: i18n 키 추가** — `i18n.ts`의 `profilePage` 그룹에 3언어 추가:

```ts
// ko
postsTab: "게시글", savedTab: "저장됨", savedEmpty: "아직 저장한 코스·장소가 없어요.",
// en
postsTab: "Posts", savedTab: "Saved", savedEmpty: "No saved courses or places yet.",
// zh
postsTab: "帖子", savedTab: "已保存", savedEmpty: "还没有保存的路线或地点。",
```

- [ ] **Step 5: 타입/린트 검증**

```bash
cd app/frontend && npx tsc --noEmit && npx next lint 2>&1 | tail -15
```
Expected: 타입 에러 0.

- [ ] **Step 6: 수동 스모크** — `npm run dev` 후: (1) 하단 프로필 → `저장됨` 탭에 코스·장소 그리드, (2) 그리드 ♡ 해제 즉시 반영, (3) 사이드바에 `/saved` 없음, (4) `/saved` 접속 시 프로필 저장됨 탭으로 리다이렉트, (5) 가이드 카드/목록에 저장 버튼·저장수 뱃지 없음.

- [ ] **Step 7: 커밋**

```bash
git add app/frontend/src/components/SavedGrid.tsx app/frontend/src/app/profile/page.tsx app/frontend/src/app/saved/page.tsx app/frontend/src/lib/i18n.ts
git commit -m "feat(saved): instagram-style saved grid under profile + /saved redirect"
```

---

## Self-Review

- **Spec coverage** — Phase 0 요구사항 전부 매핑: GUIDE 쓰기 거부(T1-S5)·읽기 제외(T1-S3/4)·상수/행 보존(삭제 없음)·`lib/saved.ts`·`SaveButton` GUIDE 제거(T2)·사이드바 `/saved` 제거(T2-S4)·프로필 인스타 저장뷰(T3)·`/saved` 리다이렉트(T3-S3). ✅
- **Placeholder scan** — 모든 코드 스텝에 실제 코드/시그니처 존재. 프론트 검증은 유닛 하네스 부재로 tsc+lint+수동 스모크(정직한 반영). ✅
- **Type consistency** — `SavedIds{courseIds,placeRefs}`·`SavedListResponse(courses,places)`·`fetchSaveCounts("COURSE")`가 백엔드(T1)↔프론트(T2/T3) 일치. `SavedGrid`의 `SavedList` 타입이 `/api/saved` 응답과 일치. ✅
