package com.guidematch.knowledge;

import com.guidematch.storage.SupabaseStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
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
    private final PlaceRepository placeRepository;
    private final SupabaseStorageClient storage;
    private final PlaceImageProcessor processor;
    private final String bucket;

    public PlaceNoteService(PlaceNoteRepository repo,
                            PlaceRepository placeRepository,
                            SupabaseStorageClient storage,
                            PlaceImageProcessor processor,
                            @Value("${supabase.storage.credentials-bucket}") String bucket) {
        this.repo = repo;
        this.placeRepository = placeRepository;
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
        // placeName은 PlaceNote 생성자도 막지만, 그 시점은 업로드가 끝난 뒤라 여기서 먼저 막는다
        // — 안 그러면 이름 없는 요청이 스토리지에 고아 객체를 두 개 남기고서야 거부된다.
        if (placeName == null || placeName.isBlank()) {
            throw new IllegalArgumentException("장소 이름이 없습니다.");
        }
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

        // 디코딩은 네트워크가 아니라 순수 로컬 CPU라 캡 체크(시드니 왕복 1~3회)보다 싸다.
        // 위장/손상 파일은 캡과 무관하게 어차피 거부되므로, 그 경우를 위해 DB를 먼저 부르지 않는다.
        PlaceImageProcessor.Processed processed = null;
        if (hasPhoto) {
            byte[] raw;
            try {
                raw = photo.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException("이미지를 읽을 수 없습니다.", e);
            }
            // 디코딩 실패는 IllegalArgumentException으로 나온다 → 400. 위장 파일의 실질 관문이다.
            processed = processor.process(raw);
        }

        // 상한은 두 키를 함께 센다 — 한쪽만 세면 우회로 실질 상한이 두 배가 된다.
        // 업로드는 상한 체크보다 뒤에 둔다 — 상한 초과는 거부되므로 그보다 앞서 올리면 고아 객체가 남는다.
        if (countMine(userId, placeId, blankToNull(kakaoPlaceId)) >= MAX_NOTES_PER_USER_PER_PLACE) {
            throw new IllegalArgumentException(
                    "한 장소에는 " + MAX_NOTES_PER_USER_PER_PLACE + "개까지 올릴 수 있습니다.");
        }

        String fullUrl = null;
        String thumbUrl = null;
        if (hasPhoto) {
            // full 성공 후 thumb이 실패하면 행은 저장되지 않고 full 객체만 스토리지에 고아로 남는다 —
            // 정리 러너가 다루는 별건이다(위 delete()의 이유와 같다).
            String base = "place-notes/" + userId + "/" + UUID.randomUUID();
            fullUrl = storage.uploadPublic(bucket, base + "_full.jpg", processed.full(), "image/jpeg");
            thumbUrl = storage.uploadPublic(bucket, base + "_thumb.jpg", processed.thumb(), "image/jpeg");
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

    /**
     * 관리자 숨김. 행은 남기고 조회에서만 빠진다.
     *
     * <p>사전 검수 큐가 없는 이 기능에서 신고 조치가 유일한 안전장치다 — 조용히 아무 일도
     * 안 하면 관리자는 성공했다고 믿고 신고를 닫지만 실제로는 아무것도 숨겨지지 않는다.
     * 그래서 {@code ModerationService#hidePost}와 같이 못 찾으면 던진다.
     */
    @Transactional
    public void hide(Long noteId) {
        PlaceNote n = repo.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 노트입니다."));
        n.hide();
        repo.save(n);
    }

    /**
     * 이 사용자가 이 장소에 이미 올린 노트 수.
     *
     * <p><b>키 하나만 세면 우회할 수 있다.</b> place_id로 3개를 올린 사용자가 같은 장소를
     * Kakao 검색 결과에서 열면 클라이언트는 kakao id만 보낸다 — 그때 kakao id로만 세면 0이
     * 나와 실질 상한이 6이 된다. 그래서 한쪽만 온 경우 <b>레지스트리에서 나머지 한쪽을 풀어</b>
     * 양쪽을 다 센다.
     *
     * <p>합집합 크기를 세는 이유: 두 키가 다 채워진 노트는 양쪽 쿼리에 걸린다. count를 더하면
     * 두 번 세어져 상한이 실제보다 빨리 걸린다.
     */
    private int countMine(Long userId, Long placeId, String kakaoPlaceId) {
        Long resolvedPlaceId = placeId;
        if (resolvedPlaceId == null && kakaoPlaceId != null) {
            resolvedPlaceId = placeRepository.findAllByKakaoPlaceIdIn(List.of(kakaoPlaceId))
                    .stream().findFirst().map(Place::getId).orElse(null);
        }
        Set<Long> ids = new HashSet<>();
        if (resolvedPlaceId != null) ids.addAll(repo.idsByUserAndPlaceId(userId, resolvedPlaceId));
        if (kakaoPlaceId != null)    ids.addAll(repo.idsByUserAndKakaoPlaceId(userId, kakaoPlaceId));
        return ids.size();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
