package com.guidematch.geo;

import com.guidematch.knowledge.PlaceMediaLookup;
import com.guidematch.knowledge.PlaceNote;
import com.guidematch.knowledge.PlaceNoteService;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 장소 노트 쓰기 + 상세 조회. 목록용 커버는 {@link PlaceController}가 맡는다.
 *
 * <p>쓰기 경로의 검증 실패({@code IllegalArgumentException})는 여기서 잡지 않는다 —
 * {@code GlobalExceptionHandler}가 전역에서 400 + {@code {"error": ...}}로 변환한다.
 * 로컬 try/catch를 다시 추가하지 말 것(본문 키가 "error"가 아닌 다른 값으로 갈라지고,
 * 이미 있는 처리기와 중복된다). 반대로 {@code GET /api/places/notes}(상세 조회)는
 * 비로그인 공개 경로라 예외를 절대 위로 던지지 않고 빈 목록으로 degrade한다.
 */
@RestController
public class PlaceNoteController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PlaceNoteController.class);

    private final PlaceNoteService service;
    private final UserRepository userRepository;
    private final PlaceMediaLookup mediaLookup;

    public PlaceNoteController(PlaceNoteService service, UserRepository userRepository,
                               PlaceMediaLookup mediaLookup) {
        this.service = service;
        this.userRepository = userRepository;
        this.mediaLookup = mediaLookup;
    }

    /** {@code authorHandle} = nickname ?? 이메일 로컬파트 ({@code User.getHandle()} 단일 소스). */
    public record NoteResponse(Long id, String photoUrl, String photoThumbUrl, String tip,
                               String authorHandle, String createdAt) {}

    @PostMapping("/api/places/notes")
    public ResponseEntity<NoteResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) String kakaoPlaceId,
            @RequestParam String placeName,
            @RequestParam(required = false) MultipartFile photo,
            @RequestParam(required = false) String tip
    ) {
        // SecurityConfig가 이미 비로그인을 막는다 — null은 설정 오류다. 401을 컨트롤러가
        // 직접 던지지 않고, 다른 검증 실패와 같은 400 경로(GlobalExceptionHandler)로 보낸다.
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        PlaceNote saved = service.create(userId, placeId, kakaoPlaceId, placeName, photo, tip);
        String handle = userRepository.findById(userId).map(User::getHandle).orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(new NoteResponse(
                saved.getId(), saved.getPhotoUrl(), saved.getPhotoThumbUrl(), saved.getTip(),
                handle, saved.getCreatedAt().toString()));
    }

    @DeleteMapping("/api/places/notes/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

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

    /** 사진이 하나도 없으면 전부 null인 200이다 — "없음"은 오류가 아니다. */
    public record MediaResponse(String coverPhotoUrl, Integer photoCount,
                                String officialPhotoUrl, String officialPhotoPublisher) {
        static final MediaResponse EMPTY = new MediaResponse(null, null, null, null);
    }

    /**
     * 장소 <b>한 곳</b>의 대표 사진. 목록은 {@link PlaceController}가 배치로 붙이므로
     * 이 경로는 <b>목록에서 온 값을 잃어버린 화면</b>을 위한 것이다.
     *
     * <p>구체적으로는 일정 빌더다. 시간표에 담긴 아이템은 이름·좌표·kakao id만 저장하므로,
     * 팔레트에서 보이던 공식 사진이 담는 순간(그리고 새로고침 후에는 영영) 사라진다.
     * 사진은 {@code places.image_url}에 있는 서버 데이터라 id로 다시 물어보는 수밖에 없다.
     *
     * <p>규칙은 만들지 않고 {@link PlaceMediaLookup}의 것을 그대로 쓴다 — 여행자 사진 우선,
     * 발행처와 쌍일 때만, https 승격, 0장은 없음. 같은 장소가 화면마다 다른 사진을 보여주면 안 된다.
     *
     * <p>노트 조회와 같은 이유로 <b>비로그인 공개 경로</b>이고 예외를 위로 던지지 않는다.
     */
    @GetMapping("/api/places/media")
    public MediaResponse media(
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) String kakaoPlaceId
    ) {
        boolean hasKakao = kakaoPlaceId != null && !kakaoPlaceId.isBlank();
        if (placeId == null && !hasKakao) {
            return MediaResponse.EMPTY;
        }
        try {
            PlaceMediaLookup.Cover cover = null;
            if (hasKakao) {
                cover = mediaLookup.coversByKakaoIds(List.of(kakaoPlaceId)).get(kakaoPlaceId);
            }
            // kakao 쪽이 비었을 때만 레지스트리 id로 한 번 더. 레지스트리 전용 장소는
            // kakao id가 아예 없고, 반대로 kakao 장소는 레지스트리에 없을 수 있다.
            if (cover == null && placeId != null) {
                cover = mediaLookup.coversByPlaceIds(List.of(placeId)).get(placeId);
            }
            return cover == null ? MediaResponse.EMPTY : new MediaResponse(
                    cover.thumbUrl(), cover.photoCount(),
                    cover.officialUrl(), cover.officialPublisher());
        } catch (Exception e) {
            log.warn("장소 대표 사진 조회 실패 — 사진 없이 진행: {}", e.toString());
            return MediaResponse.EMPTY;
        }
    }
}
