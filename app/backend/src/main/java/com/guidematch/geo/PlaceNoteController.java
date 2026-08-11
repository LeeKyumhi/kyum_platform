package com.guidematch.geo;

import com.guidematch.knowledge.PlaceNote;
import com.guidematch.knowledge.PlaceNoteService;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 장소 노트 쓰기. 읽기는 {@link PlaceController}(목록)와 {@code GET /api/places/notes}(상세)가 맡는다.
 *
 * <p>검증 실패({@code IllegalArgumentException})는 여기서 잡지 않는다 — {@code GlobalExceptionHandler}가
 * 전역에서 400 + {@code {"error": ...}}로 변환한다. 로컬 try/catch를 다시 추가하지 말 것
 * (본문 키가 "error"가 아닌 다른 값으로 갈라지고, 이미 있는 처리기와 중복된다).
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
}
