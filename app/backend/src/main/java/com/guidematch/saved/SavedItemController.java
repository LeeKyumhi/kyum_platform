package com.guidematch.saved;

import com.guidematch.saved.dto.SaveRequest;
import com.guidematch.saved.dto.SavedIdsResponse;
import com.guidematch.saved.dto.SavedListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/saved")
public class SavedItemController {

    /** counts 배치 조회 ids 상한 — 공개 라우트의 시퀀셜 스캔 남용 방지. 목록 페이지 1회 조회엔 충분. */
    private static final int MAX_COUNT_IDS = 100;

    private final SavedItemService savedItemService;

    public SavedItemController(SavedItemService savedItemService) {
        this.savedItemService = savedItemService;
    }

    /** 찜 저장 (idempotent). */
    @PostMapping
    public ResponseEntity<Void> save(@AuthenticationPrincipal Long userId, @RequestBody SaveRequest req) {
        SavedItemType type = parseType(req.itemType());
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
        return ResponseEntity.ok().build();
    }

    /** 찜 해제 (idempotent). */
    @DeleteMapping
    public ResponseEntity<Void> unsave(@AuthenticationPrincipal Long userId,
                                       @RequestParam String itemType,
                                       @RequestParam(required = false) Long refId,
                                       @RequestParam(required = false) String placeRef) {
        savedItemService.unsave(userId, parseType(itemType), refId, placeRef);
        return ResponseEntity.noContent().build();
    }

    /** 내 저장 목록 3종 (/saved 페이지). */
    @GetMapping
    public SavedListResponse myList(@AuthenticationPrincipal Long userId) {
        return savedItemService.myList(userId);
    }

    /** 내가 저장한 참조 3종 (카드 ♡ 초기화, 경량). */
    @GetMapping("/ids")
    public SavedIdsResponse myIds(@AuthenticationPrincipal Long userId) {
        return savedItemService.myIds(userId);
    }

    /** 저장수 배치 (공개 — 소셜 프루프). PLACE는 미지원(스펙 §2.3). 공개 엔드포인트라 ids 개수 상한 필수. */
    @GetMapping("/counts")
    public Map<Long, Long> counts(@RequestParam String type, @RequestParam List<Long> ids) {
        SavedItemType t = parseType(type);
        if (t != SavedItemType.COURSE) throw new IllegalArgumentException("저장수는 코스만 지원합니다.");
        if (ids != null && ids.size() > MAX_COUNT_IDS) {
            throw new IllegalArgumentException("ids는 최대 " + MAX_COUNT_IDS + "개까지 조회할 수 있습니다.");
        }
        return savedItemService.counts(t, ids);
    }

    private SavedItemType parseType(String raw) {
        try {
            return SavedItemType.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 저장 대상입니다: " + raw);
        }
    }

    private Long requireRefId(Long refId) {
        if (refId == null) throw new IllegalArgumentException("대상 id가 없습니다.");
        return refId;
    }
}
