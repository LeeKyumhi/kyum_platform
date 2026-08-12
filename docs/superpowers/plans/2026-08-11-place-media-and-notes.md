# 장소 사진·정보 축적 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 장소에 사진과 한줄팁이 쌓이고, 목록·상세에서 그것이 보이게 한다.

**Architecture:** 새 테이블 `place_notes`가 **두 개의 장소 식별자(`place_id` / `kakao_place_id`)를 둘 다 저장**하고, 읽는 시점에 SQL OR로 합친다. 업로드는 Thumbnailator로 EXIF를 제거하고 2가지 크기를 만든 뒤 기존 Supabase Storage에 올린다. 조회는 `PlaceInsightLookup`과 같은 **배치 전용 진입점**으로만 노출해 N+1을 구조적으로 막는다.

**Tech Stack:** Spring Boot 3.3.5 / Java 21 / JPA(`ddl-auto: update`) / Thumbnailator 0.4.20 / Supabase Storage / Next.js 15 + Tailwind

**선행 스펙:** `docs/superpowers/specs/2026-08-11-place-media-and-notes-design.md`

## Global Constraints

- **`ddl-auto: update`는 additive-ONLY.** 새 nullable 컬럼·새 테이블만. NOT NULL 완화·이름 변경·타입 변경은 반영되지 않는다.
- **enum을 JPA 컬럼으로 쓰지 말 것.** Hibernate가 CHECK 제약을 생성 시점 값으로 고정하고 나중에 고쳐주지 않는다(`places_place_kind_check`로 실측). `status`·`targetType`은 **String 저장 + 서비스 검증**.
- **원격 DB N+1 금지.** Supabase 풀러가 시드니, 왕복 ~250ms. 목록 경로에서 `stream().map()` 안에 단건 쿼리 금지. 배치(`IN (...)`, `findAllById`)만.
- **`@AuthenticationPrincipal Long userId`는 public 엔드포인트에서 null을 반환한다.** 절대 401을 던지지 말 것.
- **`SecurityConfig`의 `/api/places` permitAll은 경로 정확 일치**(`SecurityConfig.java:79`). 새 GET 경로는 명시 등록 필요.
- **i18n 키는 ko/en/zh 3개 언어 전부 필수** (`app/frontend/src/lib/i18n.ts`).
- **출처를 밝힐 수 없는 사진은 띄우지 않는다.** TourAPI `attribution_required: true`.
- **0은 표시하지 않는다.** 사진·팁이 없으면 그 영역 자체를 렌더하지 않는다.
- **새 npm 패키지 금지** (사용자 승인 없이). Gradle은 Thumbnailator 0.4.20이 이미 추가돼 있다.
- 백엔드 테스트: `cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && gradle test`
- 프론트 타입체크: `cd app/frontend && npx tsc --noEmit`
- 커밋 메시지는 한국어, 태스크별 1커밋.

---

## File Structure

**백엔드 — 신규 (`app/backend/src/main/java/com/guidematch/knowledge/`)**

| 파일 | 책임 |
|---|---|
| `PlaceNote.java` | 엔티티. 이중 키 + 사진 URL 2개 + 팁 + status |
| `PlaceNoteRepository.java` | 이중 키 OR 조회 · 사용자별 장소별 카운트 |
| `PlaceMediaLookup.java` | **배치 전용** 읽기 진입점 (목록용 대표사진, 상세용 전체) |
| `PlaceImageProcessor.java` | EXIF 제거 + Orientation 회전 + 2크기 생성. 순수 함수, DB·네트워크 없음 |
| `PlaceNoteService.java` | 검증 + 업로드 + 저장 + 삭제 |
| `PlaceNoteBackfill.java` | `kakao_place_id` → `place_id` 흡수 러너 |

**백엔드 — 신규 (`geo/`)**
| `PlaceNoteController.java` | `POST /api/places/notes` · `DELETE` · `GET /api/places/notes` |

**백엔드 — 수정**
| 파일 | 변경 |
|---|---|
| `knowledge/Place.java` | `imageUrl`·`imagePublisher` 컬럼 (nullable) |
| `knowledge/PlaceClue.java` | `imageUrl`·`imagePublisher` 필드 |
| `knowledge/PlaceResolver.java` | 새 필드 반영 |
| `geo/KakaoLocalClient.java` | `Place` record에 `coverPhotoUrl`·`photoCount` |
| `geo/PlaceController.java` | 목록에 배치 조회 1회 추가 |
| `safety/SafetyService.java` | `VALID_TARGET_TYPES` += `PLACE_NOTE` |
| `config/SecurityConfig.java` | `GET /api/places/notes` permitAll |
| `docs/ingest/schema/place.schema.json` | `image_url`·`image_source_url` |
| `docs/ingest/codex-ingest-prompt.md` | insight-v4 → v5 |

**프론트 — 수정**
| `components/PlaceDetailModal.tsx` | 사진 스트립 + 팁 섹션 + 업로드 버튼 |
| `components/PlaceNoteComposer.tsx` | **신규** — 사진·팁 입력 모달 |
| `components/TimetableBuilder.tsx` · `app/explore/page.tsx` | 카드 썸네일 |
| `lib/i18n.ts` | `placeNotes.*` ko/en/zh |

**의존 순서:** Task 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13 → 14 → 15
시드(§2 = Task 12~14)를 뒤에 둔 이유: 실제 데이터가 채워지려면 **사용자의 v5 재수집 실행**이 필요하다. 앞에 두면 그 대기 때문에 전체가 멈춘다. UGC(Task 1~11)는 그것과 무관하게 완주·검증된다.

---

## Task 1: `PlaceNote` 엔티티 + 이중 키 조회

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceNote.java`
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceNoteRepository.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceNoteTest.java`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `PlaceNote(Long placeId, String kakaoPlaceId, String placeNameSnapshot, Long userId, String photoUrl, String photoThumbUrl, String tip)` — 생성자. `status`는 `"VISIBLE"`로 초기화
  - `PlaceNote#getId/getPlaceId/getKakaoPlaceId/getPlaceNameSnapshot/getUserId/getPhotoUrl/getPhotoThumbUrl/getTip/getStatus/getCreatedAt`
  - `PlaceNote#hide()` → `status = "HIDDEN"`
  - `PlaceNote#linkPlaceId(Long)` → `place_id` 채우기 (Task 14가 씀)
  - `PlaceNoteRepository#findVisibleByKakaoIds(Collection<String> kakaoIds)` → `List<PlaceNote>`
  - `PlaceNoteRepository#countByUserForPlace(Long userId, Long placeId, String kakaoPlaceId)` → `long`
  - `PlaceNoteRepository#findUnlinkedKakaoIds()` → `List<String>`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceNoteTest.java`:

```java
package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 노트는 <b>두 개의 장소 식별자를 둘 다</b> 들고 있다.
 *
 * <p>해소해서 하나로 접으면(= {@code SignalRecorder}가 하는 방식) 레지스트리에 없는 장소에
 * 올린 사진이 place_id=null로 남아 <b>어느 장소 사진인지 영영 알 수 없게 된다.</b>
 * 레지스트리는 53건이고 Kakao 검색 결과는 수천 건이라, 대부분의 사진이 그렇게 사라진다.
 */
class PlaceNoteTest {

    @Test
    void 레지스트리_유래는_place_id를_갖는다() {
        PlaceNote n = new PlaceNote(17L, null, "덕수궁", 3L, "u/full.jpg", "u/thumb.jpg", null);

        assertThat(n.getPlaceId()).isEqualTo(17L);
        assertThat(n.getKakaoPlaceId()).isNull();
        assertThat(n.getStatus()).isEqualTo("VISIBLE");
        assertThat(n.getCreatedAt()).isNotNull();
    }

    @Test
    void Kakao_유래는_kakao_place_id를_버리지_않는다() {
        PlaceNote n = new PlaceNote(null, "9982341", "동네카페", 3L, null, null, "2층 창가 자리가 좋아요");

        assertThat(n.getPlaceId()).isNull();
        assertThat(n.getKakaoPlaceId()).isEqualTo("9982341");
        assertThat(n.getTip()).isEqualTo("2층 창가 자리가 좋아요");
    }

    @Test
    void 식별자가_하나도_없으면_만들_수_없다() {
        // 나중에 어떤 장소인지 알 수 없는 사진은 자산이 아니라 쓰레기다.
        // SignalRecorder:96이 빈 행을 안 만드는 것과 같은 판단이다.
        assertThatThrownBy(() -> new PlaceNote(null, null, "무명", 3L, "u/full.jpg", "u/thumb.jpg", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PlaceNote(null, "   ", "무명", 3L, "u/full.jpg", "u/thumb.jpg", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 사진도_팁도_없으면_만들_수_없다() {
        assertThatThrownBy(() -> new PlaceNote(17L, null, "덕수궁", 3L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PlaceNote(17L, null, "덕수궁", 3L, null, null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 숨기면_status가_바뀐다() {
        PlaceNote n = new PlaceNote(17L, null, "덕수궁", 3L, "u/full.jpg", "u/thumb.jpg", null);
        n.hide();
        assertThat(n.getStatus()).isEqualTo("HIDDEN");
    }

    @Test
    void 나중에_레지스트리에_수집되면_place_id가_채워진다() {
        PlaceNote n = new PlaceNote(null, "9982341", "동네카페", 3L, null, null, "좋아요");
        n.linkPlaceId(88L);

        assertThat(n.getPlaceId()).isEqualTo(88L);
        // kakao id는 지우지 않는다 — 두 키 합집합 조회가 계속 이걸 쓴다.
        assertThat(n.getKakaoPlaceId()).isEqualTo("9982341");
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
cd app/backend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
gradle test --tests 'com.guidematch.knowledge.PlaceNoteTest'
```
Expected: 컴파일 실패 — `cannot find symbol: class PlaceNote`

- [ ] **Step 3: 엔티티를 구현한다**

`PlaceNote.java`:

```java
package com.guidematch.knowledge;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 사용자가 장소에 남긴 사진 한 장 또는 한줄팁 — 축적되는 자산.
 *
 * <p><b>왜 식별자가 두 개인가.</b> 화면의 장소는 두 출신이 섞여 있고 각자 다른 신분증을 쥔다.
 * 레지스트리 유래는 {@code places.id}를, Kakao 실시간 검색 유래는 {@code kakao_place_id}만 안다.
 * 저장 시점에 한쪽으로 해소하면(= {@code SignalRecorder:67}) 레지스트리에 없는 장소에서
 * {@code place_id=null}이 되어 <b>신원을 영구히 잃는다.</b> 그래서 가진 것을 그대로 적고,
 * 합치는 일은 읽는 쪽({@link PlaceMediaLookup})이 한다.
 *
 * <p><b>왜 status가 String인가.</b> Java enum을 JPA 컬럼으로 쓰면 Hibernate가 CHECK 제약을
 * 테이블 생성 시점의 값으로 고정하고 {@code ddl-auto: update}가 나중에 고쳐주지 않는다
 * ({@code places_place_kind_check}로 실측). {@code Report.targetType}과 같이 String으로 둔다.
 */
@Entity
@Table(name = "place_notes")
public class PlaceNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 레지스트리 유래면 채워진다. Kakao 유래는 null이고, 나중에 백필이 채운다. */
    @Column(name = "place_id")
    private Long placeId;

    /** Kakao 유래면 채워진다. 절대 버리지 않는다 — 백필의 매칭 키다. */
    @Column(name = "kakao_place_id", length = 50)
    private String kakaoPlaceId;

    /** 두 키를 다 못 믿게 됐을 때의 최후 표시용. 번역하지 않는다. */
    @Column(name = "place_name_snapshot", nullable = false, length = 200)
    private String placeNameSnapshot;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "photo_url", columnDefinition = "text")
    private String photoUrl;

    /** 목록용 400px. photoUrl과 항상 함께 채워진다. */
    @Column(name = "photo_thumb_url", columnDefinition = "text")
    private String photoThumbUrl;

    @Column(name = "tip", length = 140)
    private String tip;

    /** VISIBLE / HIDDEN — 검증은 이 클래스와 서비스가 한다(DB CHECK에 의존하지 않는다). */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "VISIBLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PlaceNote() {}

    public PlaceNote(Long placeId, String kakaoPlaceId, String placeNameSnapshot, Long userId,
                     String photoUrl, String photoThumbUrl, String tip) {
        if (placeId == null && !notBlank(kakaoPlaceId)) {
            throw new IllegalArgumentException("장소 식별자가 최소 하나는 있어야 한다 — 없으면 되찾을 수 없다");
        }
        if (!notBlank(photoUrl) && !notBlank(tip)) {
            throw new IllegalArgumentException("사진 또는 팁 중 최소 하나는 있어야 한다");
        }
        this.placeId = placeId;
        this.kakaoPlaceId = notBlank(kakaoPlaceId) ? kakaoPlaceId.trim() : null;
        this.placeNameSnapshot = placeNameSnapshot;
        this.userId = userId;
        this.photoUrl = photoUrl;
        this.photoThumbUrl = photoThumbUrl;
        this.tip = notBlank(tip) ? tip.trim() : null;
    }

    public void hide() { this.status = "HIDDEN"; }

    /** 뒤늦게 레지스트리에 수집된 장소를 연결한다. kakao id는 지우지 않는다. */
    public void linkPlaceId(Long resolved) { this.placeId = resolved; }

    public Long getId()                 { return id; }
    public Long getPlaceId()            { return placeId; }
    public String getKakaoPlaceId()     { return kakaoPlaceId; }
    public String getPlaceNameSnapshot(){ return placeNameSnapshot; }
    public Long getUserId()             { return userId; }
    public String getPhotoUrl()         { return photoUrl; }
    public String getPhotoThumbUrl()    { return photoThumbUrl; }
    public String getTip()              { return tip; }
    public String getStatus()           { return status; }
    public Instant getCreatedAt()       { return createdAt; }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
```

`PlaceNoteRepository.java`:

```java
package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlaceNoteRepository extends JpaRepository<PlaceNote, Long> {

    /**
     * <b>두 키를 합쳐서</b> 가져온다 — 이 프로젝트의 핵심 조회.
     *
     * <p>목록 화면은 kakao id만 쥐고 있다. 그런데 같은 장소에 레지스트리 경로로 올린 노트는
     * {@code place_id}에만 붙어 있다. {@code places}를 조인해 양쪽을 한 번에 긁는다 —
     * 조인이 하나 늘 뿐 <b>쿼리는 1회</b>다. 두 키 중 하나만 보면 노트가 갈라져 보이고,
     * 화면에는 "사진이 좀 적네"로만 나타나 결함인지 알 수 없다.
     */
    @Query("""
           select n from PlaceNote n
             left join Place p on p.id = n.placeId
           where n.status = 'VISIBLE'
             and (n.kakaoPlaceId in :kakaoIds or p.kakaoPlaceId in :kakaoIds)
           order by n.createdAt desc
           """)
    List<PlaceNote> findVisibleByKakaoIds(@Param("kakaoIds") Collection<String> kakaoIds);

    /** 레지스트리 장소를 직접 열었을 때 (kakao id가 없는 장소도 있다 — placeId=44 "개화"). */
    @Query("""
           select n from PlaceNote n
           where n.status = 'VISIBLE' and n.placeId = :placeId
           order by n.createdAt desc
           """)
    List<PlaceNote> findVisibleByPlaceId(@Param("placeId") Long placeId);

    /**
     * 도배 상한을 세는 쿼리. <b>두 키를 다 본다</b> — 같은 장소를 place_id로 3개,
     * kakao_place_id로 3개 올려 상한을 우회하는 걸 막아야 한다(안 막으면 실질 상한이 6이 된다).
     */
    @Query("""
           select count(n) from PlaceNote n
             left join Place p on p.id = n.placeId
           where n.status = 'VISIBLE' and n.userId = :userId
             and ((:placeId is not null and (n.placeId = :placeId or p.id = :placeId))
                  or (:kakaoPlaceId is not null
                      and (n.kakaoPlaceId = :kakaoPlaceId or p.kakaoPlaceId = :kakaoPlaceId)))
           """)
    long countByUserForPlace(@Param("userId") Long userId,
                             @Param("placeId") Long placeId,
                             @Param("kakaoPlaceId") String kakaoPlaceId);

    /** 백필 대상 — kakao id만 있고 아직 레지스트리에 연결되지 않은 노트들의 kakao id. */
    @Query("select distinct n.kakaoPlaceId from PlaceNote n where n.placeId is null and n.kakaoPlaceId is not null")
    List<String> findUnlinkedKakaoIds();

    List<PlaceNote> findByKakaoPlaceIdAndPlaceIdIsNull(String kakaoPlaceId);
}
```

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceNoteTest'
```
Expected: PASS (6개)

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/PlaceNote.java \
        app/backend/src/main/java/com/guidematch/knowledge/PlaceNoteRepository.java \
        app/backend/src/test/java/com/guidematch/knowledge/PlaceNoteTest.java
git commit -m "feat(knowledge): 장소 노트 엔티티 — 두 식별자를 다 저장한다"
```

---

## Task 2: `PlaceImageProcessor` — EXIF 제거 + Orientation + 2크기

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceImageProcessor.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceImageProcessorTest.java`

**Interfaces:**
- Consumes: 없음 (DB·네트워크 없는 순수 변환)
- Produces:
  - `PlaceImageProcessor.Processed(byte[] full, byte[] thumb)` — record
  - `PlaceImageProcessor#process(byte[] raw)` → `Processed`. 디코딩 실패 시 `IllegalArgumentException`
  - 상수: `FULL_MAX_PX = 1600`, `THUMB_MAX_PX = 400`, `QUALITY = 0.82`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceImageProcessorTest.java`:

```java
package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업로드 이미지 전처리.
 *
 * <p><b>EXIF 제거는 프라이버시 약속이고, 이 테스트가 그 약속의 유일한 증거다.</b>
 * 라이브러리가 알아서 해줄 것이라고 믿는 것과, 저장될 바이트에 실제로 없음을 확인한 것은 다르다.
 * 사용자는 자기 사진에 GPS와 기기 정보가 박혀 있다는 걸 대체로 모른다.
 */
class PlaceImageProcessorTest {

    private final PlaceImageProcessor processor = new PlaceImageProcessor();

    /** 지정 크기의 JPEG을 만든다. */
    private byte[] jpeg(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /**
     * JPEG에 EXIF APP1 세그먼트를 손으로 끼운다. 라이브러리 없이 EXIF 유무를 판별하려면
     * 마커를 직접 다루는 것이 가장 확실하다 — "Exif\0\0" 문자열의 존재로 판정한다.
     */
    private byte[] withExif(byte[] jpeg) {
        byte[] payload = "Exif  GPSLatitude=37.5796;Make=iPhone".getBytes(StandardCharsets.ISO_8859_1);
        int len = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg[0]); out.write(jpeg[1]);          // SOI (FF D8)
        out.write(0xFF); out.write(0xE1);               // APP1 마커
        out.write((len >> 8) & 0xFF); out.write(len & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);            // 원본 나머지
        return out.toByteArray();
    }

    private boolean containsExifMarker(byte[] data) {
        String s = new String(data, StandardCharsets.ISO_8859_1);
        return s.contains("Exif  ") || s.contains("GPSLatitude") || s.contains("iPhone");
    }

    @Test
    void EXIF와_GPS가_저장물에서_사라진다() throws IOException {
        byte[] raw = withExif(jpeg(2400, 1800));
        assertThat(containsExifMarker(raw)).as("입력에는 EXIF가 있어야 테스트가 의미를 가진다").isTrue();

        PlaceImageProcessor.Processed p = processor.process(raw);

        assertThat(containsExifMarker(p.full())).as("full에 EXIF가 남아 있다").isFalse();
        assertThat(containsExifMarker(p.thumb())).as("thumb에 EXIF가 남아 있다").isFalse();
    }

    @Test
    void 긴_변이_1600과_400으로_줄어든다() throws IOException {
        PlaceImageProcessor.Processed p = processor.process(jpeg(2400, 1200));

        BufferedImage full = ImageIO.read(new ByteArrayInputStream(p.full()));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(p.thumb()));

        assertThat(full.getWidth()).isEqualTo(PlaceImageProcessor.FULL_MAX_PX);
        assertThat(full.getHeight()).isEqualTo(800);      // 비율 유지 2:1
        assertThat(thumb.getWidth()).isEqualTo(PlaceImageProcessor.THUMB_MAX_PX);
        assertThat(thumb.getHeight()).isEqualTo(200);
    }

    @Test
    void 원본이_더_작으면_확대하지_않는다() throws IOException {
        PlaceImageProcessor.Processed p = processor.process(jpeg(300, 200));

        BufferedImage full = ImageIO.read(new ByteArrayInputStream(p.full()));
        assertThat(full.getWidth()).isEqualTo(300);
        assertThat(full.getHeight()).isEqualTo(200);
    }

    @Test
    void 용량이_실제로_줄어든다() throws IOException {
        // 폰 원본 4~8MB → 수백 KB. 목록에 15장을 깔 수 있게 되는 근거다.
        byte[] raw = jpeg(4000, 3000);
        PlaceImageProcessor.Processed p = processor.process(raw);

        assertThat(p.full().length).isLessThan(raw.length);
        assertThat(p.thumb().length).isLessThan(p.full().length);
    }

    @Test
    void 디코딩할_수_없으면_거부한다() {
        // content-type 헤더는 클라이언트가 마음대로 보낼 수 있지만
        // 실제로 디코딩되는지는 속일 수 없다 — 이게 위장 파일의 실질 관문이다.
        byte[] notAnImage = "MZ  this is an executable".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> processor.process(notAnImage))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> processor.process(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceImageProcessorTest'
```
Expected: 컴파일 실패 — `cannot find symbol: class PlaceImageProcessor`

- [ ] **Step 3: 구현한다**

`PlaceImageProcessor.java`:

```java
package com.guidematch.knowledge;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 업로드 이미지를 저장 가능한 형태로 바꾼다. DB·네트워크를 만지지 않는 순수 변환이라
 * 테스트가 쉽고, 실패가 업로드 트랜잭션 밖에서 끝난다.
 *
 * <p><b>세 가지를 동시에 해결한다.</b>
 * <ol>
 *   <li><b>EXIF 제거</b> — 폰 사진에는 GPS·촬영시각·기기명이 박혀 있다. 사용자는 대체로 모르고,
 *       공개 URL로 올라가면 촬영 이력이 그대로 노출된다. 재인코딩하면 통째로 사라진다.</li>
 *   <li><b>Orientation 반영</b> — 그런데 EXIF를 지우면 회전 정보도 같이 사라져 아이폰 사진이
 *       옆으로 눕는다. Thumbnailator는 Orientation을 <b>먼저 읽어 회전을 적용한 뒤</b> 쓴다.
 *       이것이 JDK 내장 ImageIO만으로 안 되는 이유이자 이 의존성을 넣은 이유다.</li>
 *   <li><b>2크기 생성</b> — 목록에 1600px을 15장 깔면 3~6MB라 오히려 느려진다. 목록은 thumb를 쓴다.</li>
 * </ol>
 */
@Component
public class PlaceImageProcessor {

    public static final int FULL_MAX_PX = 1600;
    public static final int THUMB_MAX_PX = 400;
    public static final double QUALITY = 0.82;

    /** 저장할 두 가지 바이트. 항상 JPEG이다 — 입력이 PNG여도 출력 포맷을 통일한다. */
    public record Processed(byte[] full, byte[] thumb) {}

    public Processed process(byte[] raw) {
        if (raw == null || raw.length == 0) {
            throw new IllegalArgumentException("빈 파일은 이미지가 아니다");
        }
        return new Processed(scale(raw, FULL_MAX_PX), scale(raw, THUMB_MAX_PX));
    }

    private byte[] scale(byte[] raw, int maxPx) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Thumbnails.of(new ByteArrayInputStream(raw))
                    // 긴 변을 maxPx로. 원본이 더 작으면 그대로 둔다(확대하면 화질만 버린다).
                    .size(maxPx, maxPx)
                    .keepAspectRatio(true)
                    .useExifOrientation(true)   // ★ 눕는 사진을 막는 유일한 스위치
                    .outputFormat("jpg")
                    .outputQuality(QUALITY)
                    .toOutputStream(out);
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            // Thumbnailator는 디코딩 불가를 IOException("Could not obtain image from stream")으로 던진다.
            // 여기서 400으로 바꿔주지 않으면 위장 파일이 500이 되어 서버 오류로 보인다.
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.", e);
        }
        if (out.size() == 0) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }
        return out.toByteArray();
    }
}
```

> **주의**: Thumbnailator의 `.size(w, h)`는 기본적으로 축소만 한다(`Thumbnails`는 원본보다 큰 크기를 요청해도 확대하지 않는 것이 기본 동작이 아니다 — 확대를 막으려면 결과 크기를 확인해야 한다). `원본이_더_작으면_확대하지_않는다` 테스트가 이 동작을 고정한다. 테스트가 실패하면 `.size()` 대신 아래로 바꾼다:
> ```java
> int longest = Math.max(srcWidth, srcHeight);   // ImageIO.read로 먼저 크기를 읽어야 한다
> double factor = Math.min(1.0, (double) maxPx / longest);
> Thumbnails.of(...).scale(factor)...
> ```

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceImageProcessorTest'
```
Expected: PASS (5개)

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/PlaceImageProcessor.java \
        app/backend/src/test/java/com/guidematch/knowledge/PlaceImageProcessorTest.java
git commit -m "feat(knowledge): 업로드 이미지 전처리 — EXIF 제거·회전 보정·2크기"
```

---

## Task 3: `PlaceNoteService` — 검증 + 저장

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceNoteService.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceNoteServiceTest.java`

**Interfaces:**
- Consumes: `PlaceNote`, `PlaceNoteRepository`(Task 1), `PlaceImageProcessor`(Task 2), `SupabaseStorageClient#uploadPublic(String bucket, String path, byte[] data, String contentType)`
- Produces:
  - `PlaceNoteService#create(Long userId, Long placeId, String kakaoPlaceId, String placeName, MultipartFile photo, String tip)` → `PlaceNote`
  - `PlaceNoteService#delete(Long userId, Long noteId)` → `void`
  - `PlaceNoteService#hide(Long noteId)` → `void` (관리자용, Task 5가 씀)
  - 상수 `MAX_NOTES_PER_USER_PER_PLACE = 3`, `MAX_TIP_LENGTH = 140`
  - 검증 실패는 `IllegalArgumentException` (컨트롤러가 400으로 변환)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceNoteServiceTest.java`:

```java
package com.guidematch.knowledge;

import com.guidematch.storage.SupabaseStorageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 노트 작성 검증. <b>여기서 막지 못한 것은 되돌릴 수 없다</b> — 식별자 없는 사진은
 * 어느 장소인지 알 수 없고, 도배는 화면을 무너뜨린다.
 */
class PlaceNoteServiceTest {

    private final PlaceNoteRepository repo = mock(PlaceNoteRepository.class);
    private final SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    private final PlaceImageProcessor processor = new PlaceImageProcessor();
    private final PlaceNoteService service =
            new PlaceNoteService(repo, storage, processor, "posts");

    private byte[] jpegBytes() throws IOException {
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private MockMultipartFile photo() throws IOException {
        return new MockMultipartFile("photo", "p.jpg", "image/jpeg", jpegBytes());
    }

    private void stubSaveReturnsArg() {
        when(repo.save(any(PlaceNote.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 사진을_올리면_두_크기가_저장되고_노트가_생긴다() throws IOException {
        stubSaveReturnsArg();
        when(storage.uploadPublic(any(), any(), any(), any())).thenReturn("https://sb/x.jpg");

        PlaceNote n = service.create(3L, null, "9982341", "동네카페", photo(), null);

        // full·thumb 두 번 올라간다
        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).uploadPublic(eq("posts"), paths.capture(), any(), eq("image/jpeg"));
        assertThat(paths.getAllValues()).anyMatch(p -> p.contains("_full.jpg"));
        assertThat(paths.getAllValues()).anyMatch(p -> p.contains("_thumb.jpg"));
        assertThat(paths.getAllValues()).allMatch(p -> p.startsWith("place-notes/3/"));

        assertThat(n.getPhotoUrl()).isNotNull();
        assertThat(n.getPhotoThumbUrl()).isNotNull();
        assertThat(n.getKakaoPlaceId()).isEqualTo("9982341");
    }

    @Test
    void 팁만_올리면_업로드는_일어나지_않는다() {
        stubSaveReturnsArg();

        PlaceNote n = service.create(3L, 17L, null, "덕수궁", null, "돌담길이 예뻐요");

        verifyNoInteractions(storage);
        assertThat(n.getTip()).isEqualTo("돌담길이 예뻐요");
        assertThat(n.getPhotoUrl()).isNull();
    }

    @Test
    void 식별자가_없으면_거부한다() throws IOException {
        assertThatThrownBy(() -> service.create(3L, null, null, "무명", photo(), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
        verify(repo, never()).save(any());
    }

    @Test
    void 사진도_팁도_없으면_거부한다() {
        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void 팁이_140자를_넘으면_거부한다() {
        String tooLong = "가".repeat(141);
        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", null, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 허용하지_않는_형식은_거부한다() {
        MockMultipartFile gif = new MockMultipartFile("photo", "a.gif", "image/gif", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", gif, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 위장_파일은_디코딩_단계에서_거부된다() {
        // 확장자·content-type은 jpeg인데 내용이 이미지가 아니다.
        MockMultipartFile fake = new MockMultipartFile("photo", "a.jpg", "image/jpeg",
                "MZ not an image".getBytes());
        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", fake, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 한_사용자가_한_장소에_3개까지만_올릴_수_있다() {
        when(repo.countByUserForPlace(3L, 17L, null)).thenReturn(3L);

        assertThatThrownBy(() -> service.create(3L, 17L, null, "덕수궁", null, "네번째"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void 상한은_두_키를_함께_센다() {
        // place_id로 3개, kakao_place_id로 3개를 따로 세면 실질 상한이 6이 된다.
        // 리포지토리에 두 키를 다 넘겨야 한다.
        stubSaveReturnsArg();
        when(repo.countByUserForPlace(anyLong(), any(), any())).thenReturn(0L);

        service.create(3L, 17L, "8113954", "덕수궁", null, "좋아요");

        verify(repo).countByUserForPlace(3L, 17L, "8113954");
    }

    @Test
    void 남의_노트는_지울_수_없다() {
        PlaceNote mine = new PlaceNote(17L, null, "덕수궁", 3L, null, null, "내 팁");
        ReflectionTestUtils.setField(mine, "id", 5L);
        when(repo.findById(5L)).thenReturn(java.util.Optional.of(mine));

        assertThatThrownBy(() -> service.delete(99L, 5L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void 본인_노트는_지울_수_있다() {
        PlaceNote mine = new PlaceNote(17L, null, "덕수궁", 3L, null, null, "내 팁");
        ReflectionTestUtils.setField(mine, "id", 5L);
        when(repo.findById(5L)).thenReturn(java.util.Optional.of(mine));

        service.delete(3L, 5L);

        verify(repo).delete(mine);
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceNoteServiceTest'
```
Expected: 컴파일 실패 — `cannot find symbol: class PlaceNoteService`

- [ ] **Step 3: 구현한다**

`PlaceNoteService.java`:

```java
package com.guidematch.knowledge;

import com.guidematch.storage.SupabaseStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * 노트 작성·삭제. <b>검증은 전부 여기 한 곳에서 한다</b> — 컨트롤러와 나눠 놓으면
 * 새 진입점이 생길 때 한쪽만 검사하게 된다.
 */
@Service
public class PlaceNoteService {

    /** 한 명이 한 장소를 도배하는 걸 막는 최소 장치. 없으면 첫 스팸에 화면이 무너진다. */
    public static final int MAX_NOTES_PER_USER_PER_PLACE = 3;
    public static final int MAX_TIP_LENGTH = 140;

    /**
     * WebP를 넣지 않은 이유: stock ImageIO에 WebP 리더가 없어 어차피 디코딩이 실패한다.
     * 받으려면 디코더 의존성이 하나 더 필요하고, 폰 카메라 업로드는 사실상 전부 JPEG이다
     * (iOS Safari가 HEIC를 JPEG로 변환해 올린다).
     */
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final PlaceNoteRepository repo;
    private final SupabaseStorageClient storage;
    private final PlaceImageProcessor processor;
    private final String bucket;

    public PlaceNoteService(PlaceNoteRepository repo,
                            SupabaseStorageClient storage,
                            PlaceImageProcessor processor,
                            @Value("${supabase.bucket}") String bucket) {
        this.repo = repo;
        this.storage = storage;
        this.processor = processor;
        this.bucket = bucket;
    }

    @Transactional
    public PlaceNote create(Long userId, Long placeId, String kakaoPlaceId, String placeName,
                            MultipartFile photo, String tip) {
        boolean hasPhoto = photo != null && !photo.isEmpty();
        String cleanTip = tip == null ? null : tip.trim();
        boolean hasTip = cleanTip != null && !cleanTip.isEmpty();

        // 순서가 중요하다: 싼 검증을 먼저 해서 업로드 왕복을 낭비하지 않는다.
        if (placeId == null && (kakaoPlaceId == null || kakaoPlaceId.isBlank())) {
            throw new IllegalArgumentException("장소 정보가 없습니다.");
        }
        if (!hasPhoto && !hasTip) {
            throw new IllegalArgumentException("사진 또는 한줄팁 중 하나는 입력해야 합니다.");
        }
        if (hasTip && cleanTip.length() > MAX_TIP_LENGTH) {
            throw new IllegalArgumentException("한줄팁은 " + MAX_TIP_LENGTH + "자까지 쓸 수 있습니다.");
        }
        if (hasPhoto && !ALLOWED_TYPES.contains(photo.getContentType())) {
            throw new IllegalArgumentException("JPG 또는 PNG 이미지만 올릴 수 있습니다.");
        }

        // 상한은 두 키를 함께 센다 — 한쪽만 세면 우회로 실질 상한이 두 배가 된다.
        long mine = repo.countByUserForPlace(userId, placeId, blankToNull(kakaoPlaceId));
        if (mine >= MAX_NOTES_PER_USER_PER_PLACE) {
            throw new IllegalArgumentException(
                    "한 장소에는 " + MAX_NOTES_PER_USER_PER_PLACE + "개까지 올릴 수 있습니다.");
        }

        String fullUrl = null;
        String thumbUrl = null;
        if (hasPhoto) {
            byte[] raw;
            try {
                raw = photo.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException("이미지를 읽을 수 없습니다.", e);
            }
            // 디코딩 실패는 IllegalArgumentException으로 나온다 → 400. 위장 파일의 실질 관문이다.
            PlaceImageProcessor.Processed p = processor.process(raw);

            String base = "place-notes/" + userId + "/" + UUID.randomUUID();
            fullUrl = storage.uploadPublic(bucket, base + "_full.jpg", p.full(), "image/jpeg");
            thumbUrl = storage.uploadPublic(bucket, base + "_thumb.jpg", p.thumb(), "image/jpeg");
        }

        return repo.save(new PlaceNote(placeId, blankToNull(kakaoPlaceId), placeName, userId,
                fullUrl, thumbUrl, hasTip ? cleanTip : null));
    }

    /**
     * 작성자 본인만 삭제할 수 있다.
     *
     * <p><b>Storage 객체는 지우지 않는다.</b> 삭제 왕복이 실패하면 요청 전체가 깨지는데,
     * 고아 객체는 공개 URL을 아는 사람만 볼 수 있어 피해가 제한적이다. 정리 러너는 별건이다.
     */
    @Transactional
    public void delete(Long userId, Long noteId) {
        PlaceNote note = repo.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("노트를 찾을 수 없습니다."));
        if (!note.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인이 올린 것만 삭제할 수 있습니다.");
        }
        repo.delete(note);
    }

    /** 관리자 숨김. 행은 남기고 조회에서만 빠진다. */
    @Transactional
    public void hide(Long noteId) {
        repo.findById(noteId).ifPresent(n -> { n.hide(); repo.save(n); });
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
```

> **확인 필요**: `@Value("${supabase.bucket}")` 프로퍼티 키가 실제 `application.yml`과 맞는지 확인한다. `GuidePostService`의 생성자가 어떤 키를 쓰는지 보고 **같은 키를 쓴다**. 다르면 그 키로 바꾼다.

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceNoteServiceTest'
```
Expected: PASS (11개)

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/PlaceNoteService.java \
        app/backend/src/test/java/com/guidematch/knowledge/PlaceNoteServiceTest.java
git commit -m "feat(knowledge): 노트 작성 검증 — 식별자·상한·형식·위장파일"
```

---

## Task 4: `PlaceNoteController` + 보안 설정

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/geo/PlaceNoteController.java`
- Modify: `app/backend/src/main/java/com/guidematch/config/SecurityConfig.java` (79행 근처)
- Test: `app/backend/src/test/java/com/guidematch/geo/PlaceNoteControllerTest.java`

**Interfaces:**
- Consumes: `PlaceNoteService#create/delete`(Task 3), `PlaceMediaLookup`은 **아직 없다** — 이 태스크는 쓰기만 다룬다
- Produces:
  - `POST /api/places/notes` (multipart) → `PlaceNoteController.NoteResponse(Long id, String photoUrl, String photoThumbUrl, String tip, String authorHandle, String createdAt)`
  - `DELETE /api/places/notes/{id}` → 204
  - 검증 실패 → 400 + `{"message": "..."}`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceNoteControllerTest.java`:

```java
package com.guidematch.geo;

import com.guidematch.knowledge.PlaceNote;
import com.guidematch.knowledge.PlaceNoteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 쓰기 엔드포인트. <b>비로그인은 여기 오지 않는다</b>(SecurityConfig가 막는다) —
 * 그래서 userId가 null이면 그건 설정 오류이고, 조용히 저장하는 것보다 400이 낫다.
 */
class PlaceNoteControllerTest {

    private final PlaceNoteService service = mock(PlaceNoteService.class);
    private final com.guidematch.auth.UserRepository userRepo =
            mock(com.guidematch.auth.UserRepository.class);
    private final PlaceNoteController controller = new PlaceNoteController(service, userRepo);

    private PlaceNote note(long id) {
        PlaceNote n = new PlaceNote(17L, null, "덕수궁", 3L, "https://sb/f.jpg", "https://sb/t.jpg", "팁");
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Test
    void 노트를_만들면_201과_본문을_돌려준다() {
        when(service.create(eq(3L), eq(17L), isNull(), eq("덕수궁"), any(), eq("팁")))
                .thenReturn(note(5L));

        ResponseEntity<?> res = controller.create(3L, 17L, null, "덕수궁", null, "팁");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isInstanceOf(PlaceNoteController.NoteResponse.class);
        PlaceNoteController.NoteResponse body = (PlaceNoteController.NoteResponse) res.getBody();
        assertThat(body.id()).isEqualTo(5L);
        assertThat(body.photoThumbUrl()).isEqualTo("https://sb/t.jpg");
    }

    @Test
    void 검증_실패는_400과_메시지다() {
        when(service.create(anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("사진 또는 한줄팁 중 하나는 입력해야 합니다."));

        ResponseEntity<?> res = controller.create(3L, 17L, null, "덕수궁", null, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().toString()).contains("한줄팁");
    }

    @Test
    void 인증_없이_들어오면_400이다() {
        // SecurityConfig가 이미 막지만, 규칙이 바뀌어 새면 저장하지 않고 거부해야 한다.
        ResponseEntity<?> res = controller.create(null, 17L, null, "덕수궁", null, "팁");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void 삭제는_204다() {
        ResponseEntity<?> res = controller.delete(3L, 5L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(3L, 5L);
    }

    @Test
    void 사진이_multipart로_들어오면_서비스로_그대로_넘긴다() {
        MockMultipartFile photo = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[]{1});
        when(service.create(eq(3L), isNull(), eq("9982341"), eq("카페"), same(photo), isNull()))
                .thenReturn(note(6L));

        ResponseEntity<?> res = controller.create(3L, null, "9982341", "카페", photo, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.geo.PlaceNoteControllerTest'
```
Expected: 컴파일 실패 — `cannot find symbol: class PlaceNoteController`

- [ ] **Step 3: 구현한다**

`PlaceNoteController.java`:

```java
package com.guidematch.geo;

import com.guidematch.auth.User;
import com.guidematch.auth.UserRepository;
import com.guidematch.knowledge.PlaceNote;
import com.guidematch.knowledge.PlaceNoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 장소 노트 쓰기. 읽기는 {@link PlaceController}(목록)와 {@code GET /api/places/notes}(상세)가 맡는다.
 */
@RestController
public class PlaceNoteController {

    private final PlaceNoteService service;
    private final UserRepository userRepository;

    public PlaceNoteController(PlaceNoteService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    /** {@code authorHandle} = nickname ?? 이메일 로컬파트 ({@code User.getHandle()} 단일 소스). */
    public record NoteResponse(Long id, String photoUrl, String photoThumbUrl, String tip,
                               String authorHandle, String createdAt) {}

    @PostMapping("/api/places/notes")
    public ResponseEntity<?> create(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) String kakaoPlaceId,
            @RequestParam String placeName,
            @RequestParam(required = false) MultipartFile photo,
            @RequestParam(required = false) String tip
    ) {
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            PlaceNote saved = service.create(userId, placeId, kakaoPlaceId, placeName, photo, tip);
            String handle = userRepository.findById(userId).map(User::getHandle).orElse(null);
            return ResponseEntity.status(HttpStatus.CREATED).body(new NoteResponse(
                    saved.getId(), saved.getPhotoUrl(), saved.getPhotoThumbUrl(), saved.getTip(),
                    handle, saved.getCreatedAt().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/places/notes/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            service.delete(userId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
```

`SecurityConfig.java` — 79행 근처를 수정한다. **`GET /api/places/notes`만** 공개하고 POST·DELETE는 건드리지 않는다(그러면 자동으로 authenticated):

```java
// 변경 전
.requestMatchers(HttpMethod.GET, "/api/cities", "/api/places", "/api/places/nearby").permitAll()

// 변경 후 — 노트 읽기는 비로그인 탐색에서도 보여야 한다.
// ⚠ 경로 정확 일치다. "/api/places/**"로 넓히면 POST가 아니라 GET만 열리긴 하지만
//   앞으로 추가되는 GET 하위 경로가 의도치 않게 전부 공개된다 — 하나씩 명시한다.
.requestMatchers(HttpMethod.GET, "/api/cities", "/api/places", "/api/places/nearby",
                 "/api/places/notes").permitAll()
```

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test --tests 'com.guidematch.geo.PlaceNoteControllerTest'
gradle compileJava
```
Expected: PASS (5개), 컴파일 성공

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/geo/PlaceNoteController.java \
        app/backend/src/main/java/com/guidematch/config/SecurityConfig.java \
        app/backend/src/test/java/com/guidematch/geo/PlaceNoteControllerTest.java
git commit -m "feat(geo): 노트 쓰기 엔드포인트 + 읽기 공개 경로 등록"
```

---

## Task 5: 신고·숨김 배선

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/safety/SafetyService.java:21-22`
- Test: `app/backend/src/test/java/com/guidematch/safety/SafetyServicePlaceNoteTest.java`

**Interfaces:**
- Consumes: `SafetyService#report(Long reporterUserId, String targetType, Long targetId, String reason, String detail)`
- Produces: `targetType = "PLACE_NOTE"`이 유효해진다

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`SafetyServicePlaceNoteTest.java`:

```java
package com.guidematch.safety;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 노트 신고. 사전 검수 큐 없이 시작하는 판단의 전제가 <b>신고가 실제로 접수된다</b>는 것이다 —
 * 이게 안 되면 사후 대응이라는 설계 자체가 빈말이 된다.
 */
class SafetyServicePlaceNoteTest {

    private final BlockRepository blockRepo = mock(BlockRepository.class);
    private final ReportRepository reportRepo = mock(ReportRepository.class);
    private final com.guidematch.auth.UserRepository userRepo =
            mock(com.guidematch.auth.UserRepository.class);

    /**
     * ⚠ SafetyService의 실제 생성자 인자 순서·개수를 확인해서 맞춘다
     * (`grep -n "public SafetyService" SafetyService.java`). 아래는 3-인자 형태를 가정한 것이다.
     */
    private final SafetyService service = new SafetyService(blockRepo, reportRepo, userRepo);

    @Test
    void PLACE_NOTE를_신고할_수_있다() {
        when(reportRepo.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepo.existsByReporterUserIdAndTargetTypeAndTargetId(anyLong(), any(), anyLong()))
                .thenReturn(false);

        var result = service.report(3L, "PLACE_NOTE", 5L, "INAPPROPRIATE", "부적절한 사진");

        assertThat(result).isNotNull();
        verify(reportRepo).save(any(Report.class));
    }

    @Test
    void 없는_대상종류는_여전히_거부된다() {
        assertThatThrownBy(() -> service.report(3L, "SOMETHING_ELSE", 5L, "SPAM", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.safety.SafetyServicePlaceNoteTest'
```
Expected: `PLACE_NOTE를_신고할_수_있다`가 `IllegalArgumentException`으로 FAIL

> 컴파일이 안 되면 `SafetyService` 생성자 시그니처와 `ReportRepository`의 중복 확인 메서드명을 실제 코드에서 확인해 테스트를 맞춘다. **테스트를 통과시키려고 프로덕션 코드를 바꾸지 말 것** — 이 태스크의 변경은 Step 3 한 줄뿐이다.

- [ ] **Step 3: 한 줄 추가한다**

`SafetyService.java:21-22`:

```java
// 변경 전
private static final Set<String> VALID_TARGET_TYPES =
        Set.of("USER", "CONVERSATION", "POST", "REVIEW", "BOOKING");

// 변경 후 — PLACE_NOTE: 사용자가 장소에 올린 사진·한줄팁.
// 사전 검수 큐 없이 사후 대응으로 시작하므로 이 경로가 유일한 안전장치다.
private static final Set<String> VALID_TARGET_TYPES =
        Set.of("USER", "CONVERSATION", "POST", "REVIEW", "BOOKING", "PLACE_NOTE");
```

`targetType`이 **String 컬럼**이라 DB 마이그레이션이 필요 없다 — enum이었다면 CHECK 제약 때문에 `DROP CONSTRAINT`가 필요했다.

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test --tests 'com.guidematch.safety.*'
```
Expected: PASS (기존 safety 테스트도 전부 그대로)

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/safety/SafetyService.java \
        app/backend/src/test/java/com/guidematch/safety/SafetyServicePlaceNoteTest.java
git commit -m "feat(safety): 장소 노트 신고 대상 추가"
```

---

## Task 6: `PlaceMediaLookup` — 배치 전용 읽기

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceMediaLookup.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceMediaLookupTest.java`

**Interfaces:**
- Consumes: `PlaceNoteRepository#findVisibleByKakaoIds/findVisibleByPlaceId`(Task 1), `com.guidematch.auth.UserRepository`
- Produces:
  - `PlaceMediaLookup.NoteView(Long id, String photoUrl, String photoThumbUrl, String tip, String authorHandle, String createdAt)`
  - `PlaceMediaLookup.Cover(String thumbUrl, int photoCount)`
  - `PlaceMediaLookup#coversByKakaoIds(Collection<String> kakaoIds)` → `Map<String, Cover>` (없는 키는 아예 없음)
  - `PlaceMediaLookup#notesFor(Long placeId, String kakaoPlaceId)` → `List<NoteView>`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceMediaLookupTest.java`:

```java
package com.guidematch.knowledge;

import com.guidematch.auth.User;
import com.guidematch.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 노트 읽기. <b>배치만 노출한다</b> — 소비자가 리포지토리를 직접 쓰면 루프 안에서 단건 조회를
 * 하기 쉽고, Supabase가 시드니라 왕복이 250ms다. 목록 15곳이면 그것만으로 3.75초다.
 */
class PlaceMediaLookupTest {

    private final PlaceNoteRepository repo = mock(PlaceNoteRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final PlaceMediaLookup lookup = new PlaceMediaLookup(repo, userRepo);

    private PlaceNote note(long id, Long placeId, String kakaoId, String thumb, String tip) {
        PlaceNote n = new PlaceNote(placeId, kakaoId, "장소", 3L,
                thumb == null ? null : thumb.replace("_thumb", "_full"), thumb, tip);
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Test
    void 두_출신의_노트가_한_장소로_합쳐진다() {
        // 이 테스트가 설계 §1 전체의 유일한 판별기다.
        // 깨지면 사진이 갈라져 쌓이는데 화면에는 "사진이 좀 적네"로만 보인다.
        Place registryPlace = new Place("덕수궁", "Seoul", "중구", 37.5, 126.9,
                "8113954", null, "관광명소", "서울 중구");
        ReflectionTestUtils.setField(registryPlace, "id", 17L);

        when(repo.findVisibleByKakaoIds(anyCollection())).thenReturn(List.of(
                note(1L, 17L, null, "https://sb/a_thumb.jpg", null),        // 레지스트리 경로로 올림
                note(2L, null, "8113954", "https://sb/b_thumb.jpg", null)   // Kakao 경로로 올림
        ));

        Map<String, PlaceMediaLookup.Cover> covers = lookup.coversByKakaoIds(List.of("8113954"));

        assertThat(covers).containsKey("8113954");
        assertThat(covers.get("8113954").photoCount())
                .as("두 출신의 사진이 합쳐져야 한다").isEqualTo(2);
    }

    @Test
    void 사진이_없으면_키가_아예_없다() {
        // "0장"을 담은 항목을 만들면 프론트가 "사진 0장"을 렌더할 여지가 생긴다.
        when(repo.findVisibleByKakaoIds(anyCollection())).thenReturn(List.of(
                note(1L, 17L, null, null, "팁만 있음")
        ));

        Map<String, PlaceMediaLookup.Cover> covers = lookup.coversByKakaoIds(List.of("8113954"));

        assertThat(covers).doesNotContainKey("8113954");
    }

    @Test
    void 조회는_장소_수와_무관하게_1회다() {
        when(repo.findVisibleByKakaoIds(anyCollection())).thenReturn(List.of());

        lookup.coversByKakaoIds(List.of("1", "2", "3", "4", "5", "6", "7", "8"));

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repo, times(1)).findVisibleByKakaoIds(captor.capture());
        assertThat(captor.getValue()).hasSize(8);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void 빈_입력은_쿼리를_내지_않는다() {
        assertThat(lookup.coversByKakaoIds(List.of())).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    void 상세는_사진과_팁을_모두_최신순으로_준다() {
        when(repo.findVisibleByKakaoIds(anyCollection())).thenReturn(List.of(
                note(2L, null, "8113954", "https://sb/b_thumb.jpg", null),
                note(1L, null, "8113954", null, "돌담길이 예뻐요")
        ));
        User u = mock(User.class);
        when(u.getId()).thenReturn(3L);
        when(u.getHandle()).thenReturn("seoul_lover");
        when(userRepo.findAllById(anyCollection())).thenReturn(List.of(u));

        List<PlaceMediaLookup.NoteView> views = lookup.notesFor(null, "8113954");

        assertThat(views).hasSize(2);
        assertThat(views.get(0).id()).isEqualTo(2L);
        assertThat(views).allMatch(v -> "seoul_lover".equals(v.authorHandle()));
    }

    @Test
    void 작성자_조회도_배치_1회다() {
        when(repo.findVisibleByKakaoIds(anyCollection())).thenReturn(List.of(
                note(1L, null, "8113954", "https://sb/a_thumb.jpg", null),
                note(2L, null, "8113954", "https://sb/b_thumb.jpg", null)
        ));
        when(userRepo.findAllById(anyCollection())).thenReturn(List.of());

        lookup.notesFor(null, "8113954");

        verify(userRepo, times(1)).findAllById(anyCollection());
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceMediaLookupTest'
```
Expected: 컴파일 실패 — `cannot find symbol: class PlaceMediaLookup`

- [ ] **Step 3: 구현한다**

`PlaceMediaLookup.java`:

```java
package com.guidematch.knowledge;

import com.guidematch.auth.User;
import com.guidematch.auth.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 노트 조회 진입점 — {@link PlaceInsightLookup}과 같은 이유로 <b>배치만 노출한다.</b>
 *
 * <p>소비자가 리포지토리를 직접 쓰면 루프 안에서 단건 조회를 하기 쉽고, Supabase 풀러가
 * 시드니라 왕복이 250ms다. 목록 15곳이면 그것만으로 3.75초가 얹힌다.
 *
 * <p><b>두 식별자를 합치는 곳이 여기다.</b> 저장은 가진 신분증을 그대로 적고
 * ({@link PlaceNote}), 합치는 책임은 읽는 쪽이 진다. 이 클래스가 그 유일한 지점이다.
 */
@Service
public class PlaceMediaLookup {

    private final PlaceNoteRepository repo;
    private final UserRepository userRepository;

    public PlaceMediaLookup(PlaceNoteRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    /** 목록 카드용 — 대표 썸네일 1장과 사진 개수. 팁은 목록에 싣지 않는다. */
    public record Cover(String thumbUrl, int photoCount) {}

    /** 상세 모달용. {@code authorHandle}은 {@code User.getHandle()} 단일 소스. */
    public record NoteView(Long id, String photoUrl, String photoThumbUrl, String tip,
                           String authorHandle, String createdAt) {}

    /**
     * kakao id들의 대표 사진을 한 번에. 쿼리 1회 고정.
     *
     * @return kakaoPlaceId → Cover. <b>사진이 0장인 장소는 키 자체가 없다</b> —
     *         "0장"을 담은 항목을 만들면 프론트가 그걸 렌더할 여지가 생긴다.
     */
    public Map<String, Cover> coversByKakaoIds(Collection<String> kakaoIds) {
        List<String> ids = kakaoIds.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        Set<String> wanted = Set.copyOf(ids);
        Map<String, List<PlaceNote>> byKakao = new LinkedHashMap<>();

        // 쿼리가 두 키를 OR로 긁어오므로, 어느 요청 id에 속하는지는 여기서 되돌려 맞춘다.
        // 노트가 kakao id를 직접 들고 있으면 그것을 쓰고, place_id로만 붙은 것은
        // 리포지토리 조인이 이미 걸러줬으니 요청 id가 1개일 때는 그것으로 귀속시킨다.
        for (PlaceNote n : repo.findVisibleByKakaoIds(ids)) {
            String key = n.getKakaoPlaceId() != null && wanted.contains(n.getKakaoPlaceId())
                    ? n.getKakaoPlaceId()
                    : (ids.size() == 1 ? ids.get(0) : null);
            if (key == null) continue;
            byKakao.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(n);
        }

        Map<String, Cover> out = new LinkedHashMap<>();
        byKakao.forEach((kakaoId, notes) -> {
            List<PlaceNote> withPhoto = notes.stream()
                    .filter(n -> n.getPhotoThumbUrl() != null).toList();
            if (withPhoto.isEmpty()) return;   // 규칙: 0은 표시하지 않는다
            out.put(kakaoId, new Cover(withPhoto.get(0).getPhotoThumbUrl(), withPhoto.size()));
        });
        return out;
    }

    /** 상세 모달용 — 그 장소의 노트 전체(최신순). 쿼리 2회 고정(노트 + 작성자 배치). */
    public List<NoteView> notesFor(Long placeId, String kakaoPlaceId) {
        List<PlaceNote> notes;
        if (kakaoPlaceId != null && !kakaoPlaceId.isBlank()) {
            notes = repo.findVisibleByKakaoIds(List.of(kakaoPlaceId));
        } else if (placeId != null) {
            // kakao id가 없는 레지스트리 장소도 있다(placeId=44 "개화") — 그쪽 경로.
            notes = repo.findVisibleByPlaceId(placeId);
        } else {
            return List.of();
        }
        if (notes.isEmpty()) return List.of();

        // 작성자 핸들은 배치 1회로 가져온다. 노트마다 조회하면 여기서 N+1이 생긴다.
        Map<Long, String> handles = userRepository
                .findAllById(notes.stream().map(PlaceNote::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getHandle, (a, b) -> a));

        return notes.stream()
                .map(n -> new NoteView(n.getId(), n.getPhotoUrl(), n.getPhotoThumbUrl(), n.getTip(),
                        handles.get(n.getUserId()), n.getCreatedAt().toString()))
                .toList();
    }
}
```

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceMediaLookupTest'
```
Expected: PASS (6개)

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/PlaceMediaLookup.java \
        app/backend/src/test/java/com/guidematch/knowledge/PlaceMediaLookupTest.java
git commit -m "feat(knowledge): 노트 배치 조회 — 두 식별자를 읽기에서 합친다"
```

---

## Task 7: 목록에 썸네일 배선

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/geo/KakaoLocalClient.java:152-175` (`Place` record)
- Modify: `app/backend/src/main/java/com/guidematch/geo/PlaceController.java` (생성자 + `recommendFirst`)
- Test: `app/backend/src/test/java/com/guidematch/geo/PlaceControllerTest.java` (기존 파일에 추가)

**Interfaces:**
- Consumes: `PlaceMediaLookup#coversByKakaoIds`(Task 6)
- Produces:
  - `KakaoLocalClient.Place`에 `coverPhotoUrl`(String, nullable) · `photoCount`(Integer, nullable) 필드 추가
  - `Place#withMedia(String coverPhotoUrl, Integer photoCount)` → `Place`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PlaceControllerTest.java`에 추가:

```java
    @Test
    void 목록에_노트_썸네일이_붙는다() {
        when(kakaoClient.isEnabled()).thenReturn(true);
        when(kakaoClient.searchByCategory(any(), any(), any(), anyInt()))
                .thenReturn(List.of(place("8113954", "덕수궁")));
        when(mediaLookup.coversByKakaoIds(anyCollection()))
                .thenReturn(Map.of("8113954", new PlaceMediaLookup.Cover("https://sb/t.jpg", 4)));

        PlaceController.PlacesResponse res = controller.list("Seoul", "AT4", null, "ko");

        assertThat(res.places()).hasSize(1);
        assertThat(res.places().get(0).coverPhotoUrl()).isEqualTo("https://sb/t.jpg");
        assertThat(res.places().get(0).photoCount()).isEqualTo(4);
    }

    @Test
    void 사진이_없는_장소는_썸네일_필드가_null이다() {
        when(kakaoClient.isEnabled()).thenReturn(true);
        when(kakaoClient.searchByCategory(any(), any(), any(), anyInt()))
                .thenReturn(List.of(place("8113954", "덕수궁")));
        when(mediaLookup.coversByKakaoIds(anyCollection())).thenReturn(Map.of());

        PlaceController.PlacesResponse res = controller.list("Seoul", "AT4", null, "ko");

        // 0을 담은 값이 아니라 null이다 — 프론트가 "사진 0장"을 렌더할 여지를 없앤다.
        assertThat(res.places().get(0).coverPhotoUrl()).isNull();
        assertThat(res.places().get(0).photoCount()).isNull();
    }

    @Test
    void 노트_조회가_죽어도_목록은_나간다() {
        // 비로그인도 쓰는 공개 경로다. 여기서 예외가 새면 탐색 화면이 통째로 깨진다.
        when(kakaoClient.isEnabled()).thenReturn(true);
        when(kakaoClient.searchByCategory(any(), any(), any(), anyInt()))
                .thenReturn(List.of(place("8113954", "덕수궁")));
        when(mediaLookup.coversByKakaoIds(anyCollection()))
                .thenThrow(new RuntimeException("DB 연결 끊김"));

        PlaceController.PlacesResponse res = controller.list("Seoul", "AT4", null, "ko");

        assertThat(res.places()).hasSize(1);
        assertThat(res.places().get(0).coverPhotoUrl()).isNull();
    }

    @Test
    void 노트_조회도_장소_수와_무관하게_1회다() {
        when(kakaoClient.isEnabled()).thenReturn(true);
        when(kakaoClient.searchByCategory(any(), any(), any(), anyInt())).thenReturn(List.of(
                place("1", "가"), place("2", "나"), place("3", "다"), place("4", "라")));
        when(mediaLookup.coversByKakaoIds(anyCollection())).thenReturn(Map.of());

        controller.list("Seoul", "AT4", null, "ko");

        verify(mediaLookup, times(1)).coversByKakaoIds(anyCollection());
    }
```

> 기존 `PlaceControllerTest`의 `place(...)` 헬퍼와 mock 필드 이름·컨트롤러 생성자 호출을 실제 파일에서 확인해 맞춘다. `mediaLookup` mock 필드와 생성자 인자를 새로 추가해야 한다.

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.geo.PlaceControllerTest'
```
Expected: 컴파일 실패 — `coverPhotoUrl()` 메서드 없음

- [ ] **Step 3: 구현한다**

`KakaoLocalClient.java` — `Place` record를 확장한다:

```java
    /** 프론트에 돌려줄 장소 정보 (Kakao place 문서 정규화). */
    public record Place(
            String id,
            String name,
            String category,
            String categoryGroupCode,
            String phone,
            String address,
            Double latitude,
            Double longitude,
            String placeUrl,
            Integer distanceMeters,
            java.util.List<CourseReasons.Reason> reasons,
            /**
             * 사용자가 올린 대표 사진(400px 썸네일)과 장수. Kakao는 사진을 주지 않는다 —
             * {@link PlaceController}가 우리 노트를 조인해 채운다.
             * <b>사진이 없으면 둘 다 null이다</b>(0이 아니다) — "사진 0장"을 렌더할 여지를 없앤다.
             */
            String coverPhotoUrl,
            Integer photoCount
    ) {
        /** 근거만 갈아끼운 사본. record라 값을 고치는 대신 새로 만든다. */
        public Place withReasons(java.util.List<CourseReasons.Reason> newReasons) {
            return new Place(id, name, category, categoryGroupCode, phone, address,
                    latitude, longitude, placeUrl, distanceMeters, newReasons,
                    coverPhotoUrl, photoCount);
        }

        /** 노트 사진만 갈아끼운 사본. */
        public Place withMedia(String newCoverPhotoUrl, Integer newPhotoCount) {
            return new Place(id, name, category, categoryGroupCode, phone, address,
                    latitude, longitude, placeUrl, distanceMeters, reasons,
                    newCoverPhotoUrl, newPhotoCount);
        }
    }
```

> **주의**: `Place`를 생성하는 모든 지점(`KakaoLocalClient` 내부 매핑, `CoursePlanner`, 테스트들)이 인자 2개 늘어난 생성자 때문에 컴파일 에러가 난다. `gradle compileJava`와 `compileTestJava`로 전부 찾아 `null, null`을 추가한다. **11-인자 생성자를 남겨두는 편법을 쓰지 말 것** — 어느 것이 최신인지 모호해진다.

`PlaceController.java` — 생성자에 `PlaceMediaLookup`을 받고, `recommendFirst` 끝에 한 단계를 더한다:

```java
    // 생성자에 추가
    private final PlaceMediaLookup mediaLookup;

    // ... 생성자 파라미터에 PlaceMediaLookup mediaLookup 추가하고 this.mediaLookup = mediaLookup;

    /**
     * 노트 사진을 붙인다. <b>실패해도 목록은 나간다</b> — 사진은 부가 정보이고
     * 이 경로는 비로그인도 쓴다. {@code counts()}와 같은 격리 원칙이다.
     */
    private List<Place> attachMedia(List<Place> places) {
        if (places.isEmpty()) return places;
        List<String> ids = places.stream().map(Place::id).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return places;

        Map<String, PlaceMediaLookup.Cover> covers;
        try {
            covers = mediaLookup.coversByKakaoIds(ids);
        } catch (Exception e) {
            log.warn("장소 노트 사진 조회 실패 — 사진 없이 진행: {}", e.toString());
            return places;
        }
        return places.stream().map(p -> {
            PlaceMediaLookup.Cover c = covers.get(p.id());
            // 없으면 null 유지 — 0을 담은 값을 만들지 않는다.
            return c == null ? p : p.withMedia(c.thumbUrl(), c.photoCount());
        }).toList();
    }
```

그리고 `recommendFirst`의 `return` 직전에 적용한다:

```java
        List<Place> ranked = PlaceRanking.sort(withReasons, p -> new PlaceRanking.Signals(
                guideCounts.getOrDefault(p.id(), 0),
                travelerCounts.getOrDefault(p.id(), 0),
                p.reasons().stream().anyMatch(r -> "official".equals(r.kind()))));
        return attachMedia(ranked);
```

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test
```
Expected: 전체 PASS. 기존 테스트가 `Place` 생성자 때문에 깨지면 `null, null`을 추가해 고친다.

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/geo/ app/backend/src/test/java/com/guidematch/geo/
git commit -m "feat(geo): 장소 목록에 노트 대표 사진 배치 배선"
```

---

## Task 8: 상세 조회 엔드포인트

**Files:**
- Modify: `app/backend/src/main/java/com/guidematch/geo/PlaceNoteController.java`
- Test: `app/backend/src/test/java/com/guidematch/geo/PlaceNoteControllerTest.java` (추가)

**Interfaces:**
- Consumes: `PlaceMediaLookup#notesFor(Long placeId, String kakaoPlaceId)`(Task 6)
- Produces: `GET /api/places/notes?placeId=&kakaoPlaceId=` → `List<PlaceMediaLookup.NoteView>`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    @Test
    void 상세_조회는_비로그인도_된다() {
        when(mediaLookup.notesFor(null, "8113954")).thenReturn(List.of(
                new PlaceMediaLookup.NoteView(1L, "https://sb/f.jpg", "https://sb/t.jpg",
                        "돌담길이 예뻐요", "seoul_lover", "2026-08-11T00:00:00Z")));

        List<PlaceMediaLookup.NoteView> res = controller.list(null, "8113954");

        assertThat(res).hasSize(1);
        assertThat(res.get(0).authorHandle()).isEqualTo("seoul_lover");
    }

    @Test
    void 식별자가_없으면_빈_목록이다() {
        // 400이 아니라 빈 목록이다 — 공개 경로에서 400을 던지면 모달이 깨진 것처럼 보인다.
        List<PlaceMediaLookup.NoteView> res = controller.list(null, null);

        assertThat(res).isEmpty();
        verifyNoInteractions(mediaLookup);
    }

    @Test
    void 조회가_죽어도_빈_목록으로_degrade한다() {
        when(mediaLookup.notesFor(any(), any())).thenThrow(new RuntimeException("DB 끊김"));

        List<PlaceMediaLookup.NoteView> res = controller.list(17L, null);

        assertThat(res).isEmpty();
    }
```

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.geo.PlaceNoteControllerTest'
```
Expected: 컴파일 실패 — `controller.list(...)` 없음

- [ ] **Step 3: 구현한다**

`PlaceNoteController`에 `PlaceMediaLookup`을 주입하고 메서드를 추가한다:

```java
    /**
     * 장소의 노트 전체. <b>비로그인 공개 경로다</b>(SecurityConfig에 등록됨) —
     * 예외를 던지지 않고 빈 목록으로 degrade한다. 상세 모달에서 500이 나면
     * 사진이 없는 것과 서버가 죽은 것을 사용자가 구분할 수 없다.
     */
    @GetMapping("/api/places/notes")
    public List<PlaceMediaLookup.NoteView> list(
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) String kakaoPlaceId
    ) {
        if (placeId == null && (kakaoPlaceId == null || kakaoPlaceId.isBlank())) {
            return List.of();
        }
        try {
            return mediaLookup.notesFor(placeId, kakaoPlaceId);
        } catch (Exception e) {
            log.warn("장소 노트 조회 실패 — 빈 목록으로 진행: {}", e.toString());
            return List.of();
        }
    }
```

`Logger`를 추가한다:

```java
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PlaceNoteController.class);
```

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test --tests 'com.guidematch.geo.PlaceNoteControllerTest'
```
Expected: PASS (8개)

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/geo/PlaceNoteController.java \
        app/backend/src/test/java/com/guidematch/geo/PlaceNoteControllerTest.java
git commit -m "feat(geo): 장소 노트 상세 조회 (공개, 빈 목록 degrade)"
```

---

## Task 9: i18n + 모달 표시

**Files:**
- Modify: `app/frontend/src/lib/i18n.ts` (ko/en/zh 3곳)
- Modify: `app/frontend/src/components/PlaceDetailModal.tsx`

**Interfaces:**
- Consumes: `GET /api/places/notes`(Task 8)
- Produces:
  - `ModalPlace`에 `placeId?: number | null` 추가 (기존 `id`는 kakao id 문자열)
  - `PlaceNote` 타입: `{ id: number; photoUrl: string | null; photoThumbUrl: string | null; tip: string | null; authorHandle: string | null; createdAt: string }`

- [ ] **Step 1: i18n 키를 3개 언어에 추가한다**

`i18n.ts` — ko 블록의 최상위에 `placeNotes` 그룹을 추가하고, **en·zh 블록에도 같은 키를 전부** 넣는다:

```ts
// ko
placeNotes: {
  photosTitle: "여행자 사진",
  tipsTitle: "여행자 팁",
  addPhoto: "사진 올리기",
  addTip: "팁 남기기",
  photoCount: "사진 {n}장",
  more: "더 보기",
  official: "한국관광공사",
  composerTitle: "이 장소에 남기기",
  photoLabel: "사진 (JPG·PNG)",
  tipLabel: "한줄팁",
  tipPlaceholder: "예: 2층 창가 자리가 조용해요",
  tipCounter: "{n}/140",
  submit: "올리기",
  submitting: "올리는 중...",
  needSomething: "사진 또는 한줄팁 중 하나는 입력해야 합니다.",
  tooManyForPlace: "한 장소에는 3개까지 올릴 수 있습니다.",
  badFormat: "JPG 또는 PNG 이미지만 올릴 수 있습니다.",
  loginRequired: "로그인이 필요합니다.",
  deleteConfirm: "이 항목을 삭제할까요?",
  delete: "삭제",
  report: "신고",
},
```

```ts
// en
placeNotes: {
  photosTitle: "Traveler photos",
  tipsTitle: "Traveler tips",
  addPhoto: "Add photo",
  addTip: "Add tip",
  photoCount: "{n} photos",
  more: "Show more",
  official: "Korea Tourism Organization",
  composerTitle: "Share about this place",
  photoLabel: "Photo (JPG/PNG)",
  tipLabel: "One-line tip",
  tipPlaceholder: "e.g. The 2nd-floor window seats are quiet",
  tipCounter: "{n}/140",
  submit: "Post",
  submitting: "Posting...",
  needSomething: "Add a photo or a tip.",
  tooManyForPlace: "You can add up to 3 per place.",
  badFormat: "Only JPG or PNG images are allowed.",
  loginRequired: "Please log in.",
  deleteConfirm: "Delete this?",
  delete: "Delete",
  report: "Report",
},
```

```ts
// zh
placeNotes: {
  photosTitle: "旅行者照片",
  tipsTitle: "旅行者小贴士",
  addPhoto: "上传照片",
  addTip: "留下贴士",
  photoCount: "{n} 张照片",
  more: "查看更多",
  official: "韩国观光公社",
  composerTitle: "分享这个地方",
  photoLabel: "照片 (JPG·PNG)",
  tipLabel: "一句话贴士",
  tipPlaceholder: "例如：二楼靠窗的座位很安静",
  tipCounter: "{n}/140",
  submit: "上传",
  submitting: "上传中...",
  needSomething: "请至少填写照片或贴士其中一项。",
  tooManyForPlace: "每个地点最多可上传 3 条。",
  badFormat: "仅支持 JPG 或 PNG 图片。",
  loginRequired: "需要登录。",
  deleteConfirm: "要删除这一项吗？",
  delete: "删除",
  report: "举报",
},
```

- [ ] **Step 2: 타입체크로 키 파리티를 확인한다**

```bash
cd app/frontend && npx tsc --noEmit
```
Expected: 에러 0. (ko를 기준 타입으로 쓰므로 en/zh에 키가 빠지면 여기서 잡힌다)

- [ ] **Step 3: 모달에 표시 영역을 추가한다**

`PlaceDetailModal.tsx` — `ModalPlace`에 `placeId`를 추가하고, 노트를 불러와 렌더한다:

```tsx
export type ModalPlace = {
  id: string;
  /** 레지스트리 장소일 때만 있다. 노트 조회·작성에 kakao id와 함께 쓴다. */
  placeId?: number | null;
  name: string;
  // ... 기존 필드 그대로
};

type PlaceNote = {
  id: number;
  photoUrl: string | null;
  photoThumbUrl: string | null;
  tip: string | null;
  authorHandle: string | null;
  createdAt: string;
};
```

컴포넌트 안에 추가:

```tsx
  const pn = t.placeNotes;
  const [notes, setNotes] = useState<PlaceNote[]>([]);

  // 노트는 모달을 열 때 한 번만 가져온다. 실패는 조용히 넘긴다 —
  // 사진이 없는 것과 요청이 실패한 것을 사용자가 구분할 방법이 없고, 구분할 필요도 없다.
  useEffect(() => {
    const qs = new URLSearchParams();
    if (place.placeId != null) qs.set("placeId", String(place.placeId));
    if (place.id) qs.set("kakaoPlaceId", place.id);
    if (!qs.toString()) return;
    let alive = true;
    api<PlaceNote[]>(`/api/places/notes?${qs}`)
      .then((r) => { if (alive) setNotes(r ?? []); })
      .catch(() => {});
    return () => { alive = false; };
  }, [place.placeId, place.id]);

  const photos = notes.filter((n) => n.photoUrl);
  const tips = notes.filter((n) => n.tip);
```

렌더 부분 — `knownFacts` 블록 **아래**에 넣는다:

```tsx
          {/* 사진 스트립 — 0장이면 이 블록 자체가 없다 */}
          {photos.length > 0 && (
            <div>
              <p className="mb-2 text-xs font-bold text-stone-500">
                📷 {pn.photosTitle}
                <span className="ml-1 font-normal text-stone-400">
                  {pn.photoCount.replace("{n}", String(photos.length))}
                </span>
              </p>
              <div className="flex gap-2 overflow-x-auto pb-1">
                {photos.map((n) => (
                  <img key={n.id} src={n.photoUrl!} alt=""
                    className="h-28 w-28 flex-shrink-0 rounded-xl object-cover" />
                ))}
              </div>
            </div>
          )}

          {/* 여행자 팁 — 최대 5개, 각 줄에 @핸들(출처를 항상 밝힌다) */}
          {tips.length > 0 && (
            <div className="rounded-2xl border border-sky-100 bg-sky-50/60 px-4 py-3">
              <p className="mb-2 text-xs font-bold text-sky-800">✍️ {pn.tipsTitle}</p>
              <ul className="flex flex-col gap-2">
                {tips.slice(0, 5).map((n) => (
                  <li key={n.id} className="text-xs leading-relaxed text-stone-700">
                    {n.tip}
                    {n.authorHandle && (
                      <span className="ml-1 whitespace-nowrap text-[10px] text-stone-400">
                        · @{n.authorHandle}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
```

- [ ] **Step 4: 타입체크 + 빌드**

```bash
cd app/frontend && npx tsc --noEmit && npm run build
```
Expected: 둘 다 성공

- [ ] **Step 5: 커밋**

```bash
git add app/frontend/src/lib/i18n.ts app/frontend/src/components/PlaceDetailModal.tsx
git commit -m "feat(frontend): 장소 상세에 여행자 사진·팁 표시 (ko/en/zh)"
```

---

## Task 10: 업로드 UI (`PlaceNoteComposer`)

**Files:**
- Create: `app/frontend/src/components/PlaceNoteComposer.tsx`
- Modify: `app/frontend/src/components/PlaceDetailModal.tsx` (버튼 + 모달 연결)

**Interfaces:**
- Consumes: `POST /api/places/notes`(Task 4), `apiUpload`(`lib/api.ts`)
- Produces: `<PlaceNoteComposer placeId={} kakaoPlaceId={} placeName={} onClose={} onCreated={} />`

- [ ] **Step 1: 컴포넌트를 만든다**

`PlaceNoteComposer.tsx`:

```tsx
"use client";

import { useState } from "react";
import { apiUpload, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

/**
 * 장소에 사진·한줄팁을 남기는 모달.
 *
 * ⚠ 중앙 정렬은 `items-center`만으로 보장되지 않는다. 조상에 transform이 있으면
 * fixed가 그 조상 기준이 된다(globals.css의 animate-fade-up 사건). 이 컴포넌트는
 * PlaceDetailModal 안에서 렌더되고 그쪽은 이미 정상이므로 같은 패턴을 따른다.
 */
export default function PlaceNoteComposer({
  placeId, kakaoPlaceId, placeName, onClose, onCreated,
}: {
  placeId?: number | null;
  kakaoPlaceId?: string | null;
  placeName: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const { t } = useLanguage();
  const pn = t.placeNotes;
  const [photo, setPhoto] = useState<File | null>(null);
  const [tip, setTip] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = (photo !== null || tip.trim().length > 0) && !busy;

  async function submit() {
    if (!getToken()) { setError(pn.loginRequired); return; }
    if (!canSubmit) { setError(pn.needSomething); return; }
    setBusy(true);
    setError(null);
    try {
      const fd = new FormData();
      if (placeId != null) fd.append("placeId", String(placeId));
      if (kakaoPlaceId) fd.append("kakaoPlaceId", kakaoPlaceId);
      fd.append("placeName", placeName);
      if (photo) fd.append("photo", photo);
      if (tip.trim()) fd.append("tip", tip.trim());
      await apiUpload("/api/places/notes", fd);
      onCreated();
      onClose();
    } catch (e) {
      // 백엔드가 내려주는 한국어 메시지를 그대로 보여준다 — 상한·형식 안내가 여기 담겨 있다.
      setError(e instanceof Error ? e.message : pn.needSomething);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 flex max-h-[85dvh] w-full flex-col overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl sm:max-w-md">
        <div className="mb-3 flex items-start justify-between gap-2">
          <h2 className="text-base font-bold text-stone-900">{pn.composerTitle}</h2>
          <button onClick={onClose} className="text-stone-400 hover:text-stone-600">✕</button>
        </div>
        <p className="mb-4 text-xs text-stone-400">{placeName}</p>

        <label className="input-label">{pn.photoLabel}</label>
        <input type="file" accept="image/jpeg,image/png"
          onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
          className="mb-4 text-sm" />

        <label className="input-label">{pn.tipLabel}</label>
        <textarea value={tip} maxLength={140} rows={3}
          onChange={(e) => setTip(e.target.value)}
          placeholder={pn.tipPlaceholder} className="input mb-1 resize-none" />
        <p className="mb-4 text-right text-[11px] text-stone-400">
          {pn.tipCounter.replace("{n}", String(tip.length))}
        </p>

        {error && (
          <p className="mb-3 rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-xs text-red-600">
            {error}
          </p>
        )}

        <button onClick={submit} disabled={!canSubmit}
          className="btn-primary py-2 text-sm disabled:opacity-50">
          {busy ? pn.submitting : pn.submit}
        </button>
      </div>
    </div>
  );
}
```

> `apiUpload`의 시그니처를 `lib/api.ts`에서 확인한다. `method` 옵션이 있으므로 기본 POST로 호출되는지 보고 맞춘다. 에러 메시지가 `{message}` 본문에서 나오는지도 확인해 필요하면 파싱을 맞춘다.

- [ ] **Step 2: 모달에 버튼을 붙인다**

`PlaceDetailModal.tsx`:

```tsx
  const [composerOpen, setComposerOpen] = useState(false);

  function reloadNotes() {
    const qs = new URLSearchParams();
    if (place.placeId != null) qs.set("placeId", String(place.placeId));
    if (place.id) qs.set("kakaoPlaceId", place.id);
    api<PlaceNote[]>(`/api/places/notes?${qs}`).then((r) => setNotes(r ?? [])).catch(() => {});
  }
```

푸터 영역(카카오맵 링크 위)에 추가:

```tsx
          {getToken() && (
            <button type="button" onClick={() => setComposerOpen(true)}
              className="btn-secondary w-full py-2 text-sm">
              📷 {pn.addPhoto} · ✍️ {pn.addTip}
            </button>
          )}
```

컴포넌트 최상단 return 바로 안쪽(모달 본체 밖)에:

```tsx
      {composerOpen && (
        <PlaceNoteComposer
          placeId={place.placeId} kakaoPlaceId={place.id} placeName={place.name}
          onClose={() => setComposerOpen(false)} onCreated={reloadNotes} />
      )}
```

- [ ] **Step 3: 타입체크 + 빌드**

```bash
cd app/frontend && npx tsc --noEmit && npm run build
```
Expected: 둘 다 성공

- [ ] **Step 4: 브라우저 확인**

백엔드·프론트를 띄우고 `/explore`에서 장소 → 상세 → 사진·팁 등록 → 모달에 즉시 반영되는지 본다.
**아이폰 사진(회전 EXIF 포함)으로도 한 번 올려보고 눕지 않는지 확인한다** — Task 2의 테스트가
바이트 수준을 고정하지만, 실제 화면에서 확인한 적은 없다.

- [ ] **Step 5: 커밋**

```bash
git add app/frontend/src/components/PlaceNoteComposer.tsx \
        app/frontend/src/components/PlaceDetailModal.tsx
git commit -m "feat(frontend): 장소 사진·한줄팁 등록 UI"
```

---

## Task 11: 목록 카드 썸네일

**Files:**
- Modify: `app/frontend/src/app/explore/page.tsx`
- Modify: `app/frontend/src/components/TimetableBuilder.tsx` (`Place` 타입 + `PaletteCard`)

**Interfaces:**
- Consumes: `/api/places` 응답의 `coverPhotoUrl`·`photoCount`(Task 7)
- Produces: 없음 (표시만)

- [ ] **Step 1: 타입에 필드를 추가한다**

두 파일의 `Place` 타입에 추가한다:

```ts
  /** 사용자가 올린 대표 사진(400px). 없으면 undefined — "0장"은 존재하지 않는다. */
  coverPhotoUrl?: string | null;
  photoCount?: number | null;
```

- [ ] **Step 2: `/explore` 카드에 썸네일을 넣는다**

카드의 아이콘 자리를 사진이 있으면 사진으로 바꾼다:

```tsx
{p.coverPhotoUrl ? (
  <img src={p.coverPhotoUrl} alt=""
    className="h-12 w-12 flex-shrink-0 rounded-xl object-cover" />
) : (
  /* 기존 아이콘 타일 그대로 */
  <div className="...">{categoryIcon(p.name, p.category, false)}</div>
)}
```

사진이 2장 이상이면 개수를 겹쳐 표시한다:

```tsx
{p.photoCount != null && p.photoCount > 1 && (
  <span className="absolute bottom-0 right-0 rounded-tl-lg bg-black/60 px-1 text-[10px] text-white">
    {p.photoCount}
  </span>
)}
```

> `absolute`를 쓰려면 감싸는 요소에 `relative`가 있어야 한다. 없으면 추가한다.

- [ ] **Step 3: 팔레트 카드에도 같이 넣는다**

`TimetableBuilder.tsx`의 `PaletteCard`에 `photoUrl?: string` prop을 추가하고, 아이콘 대신 사진을 렌더한다. 호출부(장소 탭 618행·추천 탭 673행)에서 `photoUrl={p.coverPhotoUrl ?? undefined}`를 넘긴다.

- [ ] **Step 4: 타입체크 + 빌드 + 브라우저**

```bash
cd app/frontend && npx tsc --noEmit && npm run build
```
그리고 `/explore`·`/trips/[id]` 팔레트에서 썸네일이 뜨는지, **사진 없는 장소가 기존 아이콘으로 그대로 나오는지** 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add app/frontend/src/app/explore/page.tsx app/frontend/src/components/TimetableBuilder.tsx
git commit -m "feat(frontend): 장소 목록 카드에 노트 썸네일"
```

---

## Task 12: 시드 — 계약·엔티티에 이미지 필드

**Files:**
- Modify: `docs/ingest/schema/place.schema.json`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/Place.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/PlaceClue.java`
- Modify: `app/backend/src/main/java/com/guidematch/knowledge/PlaceResolver.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceResolverImageTest.java`

**Interfaces:**
- Produces:
  - `Place#getImageUrl()` · `Place#getImagePublisher()`
  - `PlaceClue`에 `imageUrl`·`imagePublisher` 필드 (record 컴포넌트 2개 추가)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공식 사진은 <b>발행처와 함께</b> 저장된다.
 *
 * <p>image_url만 저장하면 표시 시점에 발행처를 되찾을 방법이 없고, 그러면
 * "출처를 못 밝히는 사진은 띄우지 않는다"는 규칙이 원리상 지켜질 수 없다.
 * sources.yml 주석이 같은 말을 한다 — "나중에 출처를 되찾을 방법이 없으면 표시할 수도 없다".
 */
class PlaceResolverImageTest {

    @Test
    void 이미지와_발행처가_함께_저장된다() {
        Place p = new Place("남산케이블카", "Seoul", "중구", 37.55, 126.98,
                "123", "126508", "관광명소", "서울 중구");
        p.applyImage("https://tong.visitkorea.or.kr/x.jpg", "한국관광공사");

        assertThat(p.getImageUrl()).isEqualTo("https://tong.visitkorea.or.kr/x.jpg");
        assertThat(p.getImagePublisher()).isEqualTo("한국관광공사");
    }

    @Test
    void 발행처가_없으면_이미지도_저장하지_않는다() {
        Place p = new Place("남산케이블카", "Seoul", "중구", 37.55, 126.98,
                "123", "126508", "관광명소", "서울 중구");
        p.applyImage("https://x/y.jpg", null);

        // 띄울 수 없는 사진은 저장할 이유도 없다 — 나중에 "왜 안 보이지"의 원인이 된다.
        assertThat(p.getImageUrl()).isNull();
        assertThat(p.getImagePublisher()).isNull();
    }

    @Test
    void 이미_있는_이미지를_빈_값으로_덮지_않는다() {
        Place p = new Place("남산케이블카", "Seoul", "중구", 37.55, 126.98,
                "123", "126508", "관광명소", "서울 중구");
        p.applyImage("https://x/first.jpg", "한국관광공사");
        p.applyImage(null, null);

        assertThat(p.getImageUrl()).isEqualTo("https://x/first.jpg");
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceResolverImageTest'
```
Expected: 컴파일 실패 — `applyImage` 없음

- [ ] **Step 3: 구현한다**

`Place.java`에 필드 2개와 메서드를 추가한다:

```java
    /** TourAPI detailCommon2의 firstimage. 발행처와 <b>쌍으로만</b> 저장된다. */
    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    /**
     * 사진 발행처(예: "한국관광공사"). 출처를 못 밝히면 사진을 띄우지 않기로 했으므로
     * 이 값이 없는 이미지는 저장 자체를 하지 않는다 — 표시 시점에 되찾을 방법이 없다.
     */
    @Column(name = "image_publisher", length = 100)
    private String imagePublisher;

    public String getImageUrl()       { return imageUrl; }
    public String getImagePublisher() { return imagePublisher; }

    /** 둘 다 있을 때만 반영한다. 기존 값을 빈 값으로 덮지 않는다(재적재는 멱등해야 한다). */
    public void applyImage(String url, String publisher) {
        if (isBlank(url) || isBlank(publisher)) return;
        this.imageUrl = url;
        this.imagePublisher = publisher;
    }
```

`PlaceClue.java`에 record 컴포넌트를 추가한다 (`sourceKind` 뒤):

```java
        String sourceKind,
        /** TourAPI firstimage. 발행처는 source.publisher에서 온다. */
        String imageUrl,
        String imagePublisher
```

`PlaceResolver`가 `PlaceClue`를 `Place`에 반영하는 지점에서 `place.applyImage(clue.imageUrl(), clue.imagePublisher())`를 호출하고, `Resolution.needsSave` 판정에 이미지 변경도 포함시킨다.

> **주의**: `PlaceClue`는 record라 생성자 인자가 2개 늘어난다. `IngestService`·테스트 등 모든 생성 지점이 컴파일 에러가 난다. `gradle compileJava compileTestJava`로 전부 찾아 `null, null`을 추가한다.

`place.schema.json`의 `properties`에 추가한다:

```json
    "image_url": {
      "type": ["string", "null"],
      "description": "TourAPI detailCommon2의 firstimage. 발행처(source.publisher)와 쌍으로만 저장된다."
    },
    "image_source_url": {
      "type": ["string", "null"],
      "description": "이미지 원본 페이지. 출처 표시 링크용."
    },
```

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test
```
Expected: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
git add docs/ingest/schema/place.schema.json app/backend/src/main/java/com/guidematch/knowledge/ \
        app/backend/src/test/java/com/guidematch/knowledge/PlaceResolverImageTest.java
git commit -m "feat(knowledge): 공식 사진 + 발행처 필드 (계약·엔티티)"
```

---

## Task 13: 프롬프트 v5

**Files:**
- Modify: `docs/ingest/codex-ingest-prompt.md`
- Modify: `docs/ingest/CONTRACT.md` (§16 신설)

**Interfaces:** 없음 (문서)

- [ ] **Step 1: 프롬프트를 v5로 올린다**

`codex-ingest-prompt.md`에서 버전 문자열 `insight-v4`를 `insight-v5`로 바꾸고, 이미지 지시를 추가한다:

```markdown
### 사진 (v5 신설)

`detailCommon2`를 이미 호출하고 있다(overview 때문에). **그 같은 응답의 `firstimage`를
`image_url`에, `firstimage2`가 있으면 무시하고, 원본 페이지 URL을 `image_source_url`에 싣는다.**
API 호출을 추가하지 말 것 — 이미 받은 응답 안에 있다.

`firstimage`가 빈 문자열이면 `null`로 싣는다. TourAPI는 사진이 없을 때 `""`를 준다.

⚠ **발행처를 반드시 `source.publisher`에 싣는다**(`한국관광공사`). 적재기는 발행처가 없으면
이미지를 버린다 — `attribution_required: true`라 출처 없이 띄우면 그 자체로 계약 위반이고,
저장 시점에 버리지 않으면 나중에 "왜 사진이 안 보이지"의 원인이 된다.
```

- [ ] **Step 2: CONTRACT.md에 §16을 추가한다**

```markdown
## §16. 이미지 (v5)

- 출처: TourAPI `detailCommon2`의 `firstimage`. **추가 호출 없음** — overview와 같은 응답이다.
- 저장 조건: `image_url`과 발행처가 **둘 다** 있을 때만. 하나라도 없으면 버린다.
- 이유: 공공누리 조건부 + `attribution_required: true`. 출처를 표시할 수 없는 사진은
  띄울 수 없으므로 저장할 이유도 없다.
- 커버리지 한계: 관광지 위주다. 음식점·카페에는 거의 붙지 않는다 — 그 구역은 사용자 노트가 메운다.
```

- [ ] **Step 3: 커밋**

```bash
git add docs/ingest/codex-ingest-prompt.md docs/ingest/CONTRACT.md
git commit -m "docs(ingest): 프롬프트 insight-v5 — firstimage + 계약 §16"
```

- [ ] **Step 4: 사용자에게 재수집을 요청한다**

**이 태스크는 코드로 완결되지 않는다.** 시드가 실제로 채워지려면 사용자가 재수집을 1회 실행해야 한다:

```bash
codex exec --cd ~/peerup-ingest --skip-git-repo-check \
  --sandbox workspace-write -c sandbox_workspace_write.network_access=true \
  < docs/ingest/codex-ingest-prompt.md
```

⚠ `--cd` 없으면 쓰기 루트가 앱 리포가 되어 격리가 무너진다.
⚠ `app/backend/src/main`을 고쳤으면 `./scripts/ingest/build-jar.sh` 먼저 (안 하면 exit 3).

실행 후 확인: `select count(*) from places where image_url is not null;`이 0보다 커야 한다.
0이면 프롬프트가 필드를 안 싣은 것이므로 v5 지시문을 다시 본다.

---

## Task 14: 흡수 백필

**Files:**
- Create: `app/backend/src/main/java/com/guidematch/knowledge/PlaceNoteBackfill.java`
- Test: `app/backend/src/test/java/com/guidematch/knowledge/PlaceNoteBackfillTest.java`

**Interfaces:**
- Consumes: `PlaceNoteRepository#findUnlinkedKakaoIds/findByKakaoPlaceIdAndPlaceIdIsNull`(Task 1), `PlaceRepository#findAllByKakaoPlaceIdIn`
- Produces: `PlaceNoteBackfill#run()` → `int` (연결된 노트 수)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * "나중에 흡수된다"는 약속의 판별기.
 *
 * <p>Kakao 유래 노트는 place_id가 비어 있다. 그 장소가 뒤늦게 레지스트리에 수집되면
 * 연결해줘야 코스 추천·인사이트와 같은 축에 서게 된다. 이게 없으면 노트는 영구히
 * kakao id로만 떠 있고, 레지스트리 쪽 기능과 절대 만나지 않는다.
 */
class PlaceNoteBackfillTest {

    private final PlaceNoteRepository noteRepo = mock(PlaceNoteRepository.class);
    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    private final PlaceNoteBackfill backfill = new PlaceNoteBackfill(noteRepo, placeRepo, tx);

    private Place place(long id, String kakaoId) {
        Place p = new Place("덕수궁", "Seoul", "중구", 37.5, 126.9, kakaoId, null, "관광명소", "서울");
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void 수집된_장소의_노트에_place_id가_채워진다() {
        PlaceNote orphan = new PlaceNote(null, "8113954", "덕수궁", 3L, null, null, "좋아요");
        when(noteRepo.findUnlinkedKakaoIds()).thenReturn(List.of("8113954"));
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of(place(17L, "8113954")));
        when(noteRepo.findByKakaoPlaceIdAndPlaceIdIsNull("8113954")).thenReturn(List.of(orphan));

        int linked = backfill.run();

        assertThat(linked).isEqualTo(1);
        assertThat(orphan.getPlaceId()).isEqualTo(17L);
        assertThat(orphan.getKakaoPlaceId()).as("kakao id는 지우지 않는다").isEqualTo("8113954");
    }

    @Test
    void 아직_수집되지_않은_장소는_건드리지_않는다() {
        when(noteRepo.findUnlinkedKakaoIds()).thenReturn(List.of("9982341"));
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of());

        int linked = backfill.run();

        assertThat(linked).isZero();
        verify(noteRepo, never()).saveAll(anyCollection());
    }

    @Test
    void 연결할_것이_없으면_쿼리를_더_내지_않는다() {
        when(noteRepo.findUnlinkedKakaoIds()).thenReturn(List.of());

        assertThat(backfill.run()).isZero();
        verifyNoInteractions(placeRepo);
    }

    @Test
    void 실패해도_기동을_막지_않는다() {
        // 실제로 Supabase 소켓이 끊겨 앱이 못 뜬 적이 있다(레지스트리 사이클). 회귀로 고정한다.
        when(noteRepo.findUnlinkedKakaoIds()).thenThrow(new RuntimeException("소켓 끊김"));

        assertThatCode(() -> backfill.run()).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
gradle test --tests 'com.guidematch.knowledge.PlaceNoteBackfillTest'
```
Expected: 컴파일 실패

- [ ] **Step 3: 구현한다**

```java
package com.guidematch.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kakao 유래 노트를 뒤늦게 수집된 레지스트리 장소에 연결한다.
 *
 * <p><b>왜 TransactionTemplate인가</b>: {@code @Transactional}을 같은 클래스의 메서드에 붙이면
 * 자기 호출이라 프록시를 우회해 아무 효과가 없다({@link PlaceKindBackfill}에서 실측).
 *
 * <p><b>왜 예외를 삼키나</b>: 이 러너가 기동을 막으면 안 된다. 실제로 Supabase 소켓이 끊겨
 * 앱이 못 뜬 적이 있다. 연결은 다음 실행에 다시 시도하면 되고, 못 해도 노트는 안 사라진다.
 *
 * <p>생성자는 하나만 둔다 — 둘이면 Spring이 {@code No default constructor found}로 죽는다.
 */
@Component
public class PlaceNoteBackfill {

    private static final Logger log = LoggerFactory.getLogger(PlaceNoteBackfill.class);

    private final PlaceNoteRepository noteRepo;
    private final PlaceRepository placeRepo;
    private final TransactionTemplate tx;

    public PlaceNoteBackfill(PlaceNoteRepository noteRepo, PlaceRepository placeRepo,
                             TransactionTemplate tx) {
        this.noteRepo = noteRepo;
        this.placeRepo = placeRepo;
        this.tx = tx;
    }

    /** @return 연결된 노트 수 */
    public int run() {
        try {
            List<String> unlinked = noteRepo.findUnlinkedKakaoIds();
            if (unlinked.isEmpty()) return 0;

            Map<String, Long> resolved = placeRepo.findAllByKakaoPlaceIdIn(unlinked).stream()
                    .collect(Collectors.toMap(Place::getKakaoPlaceId, Place::getId, (a, b) -> a));
            if (resolved.isEmpty()) return 0;

            List<PlaceNote> changed = new ArrayList<>();
            resolved.forEach((kakaoId, placeId) -> {
                for (PlaceNote n : noteRepo.findByKakaoPlaceIdAndPlaceIdIsNull(kakaoId)) {
                    n.linkPlaceId(placeId);
                    changed.add(n);
                }
            });
            if (changed.isEmpty()) return 0;

            tx.executeWithoutResult(status -> noteRepo.saveAll(changed));
            log.info("장소 노트 {}건을 레지스트리에 연결했다", changed.size());
            return changed.size();
        } catch (Exception e) {
            log.warn("장소 노트 백필 실패 — 무시하고 진행: {}", e.toString());
            return 0;
        }
    }
}
```

- [ ] **Step 4: 통과를 확인한다**

```bash
gradle test
```
Expected: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
git add app/backend/src/main/java/com/guidematch/knowledge/PlaceNoteBackfill.java \
        app/backend/src/test/java/com/guidematch/knowledge/PlaceNoteBackfillTest.java
git commit -m "feat(knowledge): Kakao 유래 노트를 레지스트리에 흡수하는 백필"
```

---

## Task 15: 실 DB 스모크

**Files:**
- Create: `scripts/smoke/place-notes-smoke.sh`

**Interfaces:** 없음

- [ ] **Step 1: 스모크를 작성한다**

`scripts/smoke/registry-course-smoke.sh`의 구조(로그인 → 토큰 → 어서션 카운터)를 그대로 따른다. 어서션 6개:

```bash
#!/usr/bin/env bash
# 장소 노트 실 DB 스모크. 단위 테스트가 못 잡는 것만 본다 —
# 실제 Supabase Storage 업로드, ddl-auto가 만든 테이블, 두 키 합류의 실제 SQL 동작.
set -euo pipefail

EMAIL="${1:?사용법: $0 <이메일> <비번>}"
PASSWORD="${2:?}"
BASE="${BASE:-http://localhost:8080}"
PASS=0; FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1"; FAIL=$((FAIL+1)); }

TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
[ -n "$TOKEN" ] && ok "로그인" || bad "로그인"

# 1x1 JPEG을 만든다
TMP=$(mktemp -d)
python3 - "$TMP/t.jpg" <<'PY'
import sys, struct, zlib
# 최소 JPEG (1x1 흑색). Pillow 없이 만들기 위해 하드코딩한다.
data = bytes.fromhex(
 'ffd8ffe000104a46494600010100000100010000ffdb004300ffffffffffffffffffffffff'
 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff'
 'ffffffffffffffffffffffffffffffffffffffffffffffc00011080001000101011100ffc4'
 '001f0000010501010101010100000000000000000102030405060708090a0bffda0008010100'
 '003f00d2cf20ffd9')
open(sys.argv[1], 'wb').write(data)
PY

# ── 1. 팁만 등록 (kakao 유래) ──
R=$(curl -s -X POST "$BASE/api/places/notes" -H "Authorization: Bearer $TOKEN" \
  -F "kakaoPlaceId=smoke-9982341" -F "placeName=스모크카페" -F "tip=스모크 팁")
echo "$R" | grep -q '"id"' && ok "팁 등록" || bad "팁 등록: $R"
NOTE_ID=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("id",""))')

# ── 2. 비로그인 조회에 나온다 ──
curl -s "$BASE/api/places/notes?kakaoPlaceId=smoke-9982341" | grep -q '스모크 팁' \
  && ok "비로그인 조회에 노출" || bad "비로그인 조회에 노출 안 됨"

# ── 3. 사진 업로드 (Storage 실제 왕복 + EXIF 제거 경로) ──
R=$(curl -s -X POST "$BASE/api/places/notes" -H "Authorization: Bearer $TOKEN" \
  -F "kakaoPlaceId=smoke-9982341" -F "placeName=스모크카페" -F "photo=@$TMP/t.jpg;type=image/jpeg")
echo "$R" | grep -q 'photoThumbUrl' && ok "사진 업로드 + 썸네일 생성" || bad "사진 업로드: $R"

# ── 4. 위장 파일은 400 ──
echo "MZ not an image" > "$TMP/fake.jpg"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/places/notes" \
  -H "Authorization: Bearer $TOKEN" -F "kakaoPlaceId=smoke-1" -F "placeName=x" \
  -F "photo=@$TMP/fake.jpg;type=image/jpeg")
[ "$CODE" = "400" ] && ok "위장 파일 400" || bad "위장 파일이 $CODE"

# ── 5. 4번째는 상한으로 막힌다 ──
curl -s -X POST "$BASE/api/places/notes" -H "Authorization: Bearer $TOKEN" \
  -F "kakaoPlaceId=smoke-9982341" -F "placeName=스모크카페" -F "tip=세번째" > /dev/null
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/places/notes" \
  -H "Authorization: Bearer $TOKEN" -F "kakaoPlaceId=smoke-9982341" \
  -F "placeName=스모크카페" -F "tip=네번째")
[ "$CODE" = "400" ] && ok "장소별 3개 상한" || bad "4번째가 $CODE (상한 미작동)"

# ── 6. 삭제하면 사라진다 ──
curl -s -X DELETE "$BASE/api/places/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" > /dev/null
curl -s "$BASE/api/places/notes?kakaoPlaceId=smoke-9982341" | grep -q '스모크 팁' \
  && bad "삭제 후에도 남아 있다" || ok "삭제 반영"

# ── ⓘ 시드 커버리지 보고 (실패가 아니라 정보) ──
echo "  ⓘ 이 스모크는 시드(image_url)를 검증하지 않는다 — v5 재수집 실행 후 별도 확인 필요"

rm -rf "$TMP"
echo; echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
```

- [ ] **Step 2: 실행한다**

```bash
chmod +x scripts/smoke/place-notes-smoke.sh
bash scripts/smoke/place-notes-smoke.sh <이메일인증계정> <비번>
```
Expected: `PASS=7 FAIL=0`

> 스모크는 실 dev DB에 행을 남긴다. `place_notes`의 `smoke-*` 행과 Supabase Storage의
> `place-notes/{userId}/` 객체를 정리 대상으로 `HANDOFF.md`에 기록한다.

- [ ] **Step 3: 커밋**

```bash
git add scripts/smoke/place-notes-smoke.sh
git commit -m "test(smoke): 장소 노트 실 DB 스모크 (7개 어서션)"
```

---

## Self-Review 결과

**1. 스펙 커버리지**

| 스펙 섹션 | 태스크 |
|---|---|
| §1 데이터 모델 (이중 키) | Task 1 |
| §1 규칙3 흡수 백필 | Task 14 |
| §2 시드 (계약·엔티티·프롬프트) | Task 12, 13 |
| §3 검증 (식별자·상한·형식·140자) | Task 3 |
| §3 이미지 전처리 (EXIF·회전·2크기) | Task 2 |
| §3 엔드포인트 + 보안 | Task 4 |
| §3 검수 (신고·숨김) | Task 5 |
| §4 배치 조회 + 두 키 합류 | Task 6 |
| §4 목록 썸네일 | Task 7, 11 |
| §4 상세 조회 | Task 8 |
| §4 모달 표시 + i18n | Task 9 |
| §4 업로드 UI | Task 10 |
| §5 실 DB 스모크 | Task 15 |

**빠진 것 1개를 발견해 기록한다**: 스펙 §3의 "관리자 포털 숨김"은 `PlaceNoteService#hide()`(Task 3)와 `PLACE_NOTE` 신고(Task 5)까지만 다뤘고 **관리자 화면에 버튼을 붙이는 작업은 이 계획에 없다.** 관리자 포털 구조를 조사하지 않은 상태에서 코드를 적으면 추측이 된다. Task 5 완료 후 `app/admin/` 구조를 확인하고 별도 태스크로 추가할 것.

**2. 플레이스홀더 스캔** — "적절히 처리", "TBD", 코드 없는 코드 스텝: 없음. 단 아래 3곳은 **실제 코드 확인이 필요한 지점**으로 계획서 안에 인용문으로 명시했다(추측을 코드처럼 적지 않기 위해):
- Task 3: `@Value` 버킷 프로퍼티 키 → `GuidePostService` 생성자에서 확인
- Task 5: `SafetyService` 생성자 시그니처, `ReportRepository` 중복 확인 메서드명
- Task 10: `apiUpload` 시그니처와 에러 메시지 파싱

**3. 타입 일관성**
- `PlaceNote` 생성자 7-인자 → Task 1 정의, Task 3·6·14에서 동일하게 사용 ✓
- `PlaceMediaLookup.Cover(thumbUrl, photoCount)` → Task 6 정의, Task 7에서 `c.thumbUrl()`·`c.photoCount()` ✓
- `PlaceMediaLookup.NoteView` 6-컴포넌트 → Task 6 정의, Task 8 응답·Task 9 프론트 타입과 일치 ✓
- `KakaoLocalClient.Place`가 11-인자 → 13-인자로 변경 → Task 7이 모든 생성 지점 수정을 명시 ✓
- `PlaceClue`가 12-인자 → 14-인자 → Task 12가 모든 생성 지점 수정을 명시 ✓
- 프론트 `coverPhotoUrl`·`photoCount` 이름이 백엔드 record 컴포넌트명과 일치 ✓
