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
