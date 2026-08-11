package com.guidematch.knowledge;

import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
    private final PlaceRepository placeRepo;
    private final UserRepository userRepository;

    public PlaceMediaLookup(PlaceNoteRepository repo, PlaceRepository placeRepo,
                            UserRepository userRepository) {
        this.repo = repo;
        this.placeRepo = placeRepo;
        this.userRepository = userRepository;
    }

    /** 목록 카드용 — 대표 썸네일 1장과 사진 개수. 팁은 목록에 싣지 않는다. */
    public record Cover(String thumbUrl, int photoCount) {}

    /** 상세 모달용. {@code authorHandle}은 {@code User.getHandle()} 단일 소스. */
    public record NoteView(Long id, String photoUrl, String photoThumbUrl, String tip,
                           String authorHandle, String createdAt) {}

    /**
     * kakao id들의 대표 사진을 한 번에. 요청 건수와 무관하게 <b>쿼리 2~3회 고정</b>
     * (place 조회 1 + kakao id 노트 조회 1, 레지스트리에 걸리는 place가 있으면 +1) —
     * 목록 크기(N)가 늘어나도 이 횟수는 늘지 않는다.
     *
     * @return kakaoPlaceId → Cover. <b>사진이 0장인 장소는 키 자체가 없다</b> —
     *         "0장"을 담은 항목을 만들면 프론트가 그걸 렌더할 여지가 생긴다.
     */
    public Map<String, Cover> coversByKakaoIds(Collection<String> kakaoIds) {
        List<String> ids = kakaoIds.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        Set<String> wanted = Set.copyOf(ids);

        // 1회차: kakao id → 우리 place id. 이 지도가 있어야 place_id로만 붙은 노트를
        // 어느 요청 id에 귀속시킬지 <b>추측 없이</b> 정할 수 있다.
        // (PlaceInsightLookup이 쓰는 것과 같은 2회 패턴)
        Map<Long, String> placeIdToKakao = new LinkedHashMap<>();
        for (Place p : placeRepo.findAllByKakaoPlaceIdIn(ids)) {
            placeIdToKakao.put(p.getId(), p.getKakaoPlaceId());
        }

        // 2회차: 두 키로 각각. 한 방 OR로 합치지 않는다 — 빈 컬렉션과 null 파라미터 타입
        // 문제를 동시에 피하려면 호출을 나누는 편이 안전하다(플라이휠 교훈).
        List<PlaceNote> notes = new ArrayList<>(repo.findVisibleByKakaoIdIn(ids));
        if (!placeIdToKakao.isEmpty()) {
            notes.addAll(repo.findVisibleByPlaceIdIn(placeIdToKakao.keySet()));
        }

        Map<String, List<PlaceNote>> byKakao = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (PlaceNote n : notes) {
            // 두 키가 다 채워진 노트는 양쪽 쿼리에 다 걸린다 — 한 번만 센다.
            if (!seen.add(n.getId())) continue;
            String key = (n.getKakaoPlaceId() != null && wanted.contains(n.getKakaoPlaceId()))
                    ? n.getKakaoPlaceId()
                    : placeIdToKakao.get(n.getPlaceId());
            if (key == null) continue;   // 요청 범위 밖 — 여기 오면 쿼리가 잘못된 것이다
            byKakao.computeIfAbsent(key, k -> new ArrayList<>()).add(n);
        }

        Map<String, Cover> out = new LinkedHashMap<>();
        byKakao.forEach((kakaoId, notesForKakao) -> {
            List<PlaceNote> withPhoto = notesForKakao.stream()
                    .filter(n -> n.getPhotoThumbUrl() != null).toList();
            if (withPhoto.isEmpty()) return;   // 규칙: 0은 표시하지 않는다
            // 대표 썸네일은 최신 사진이어야 한다 — 두 쿼리 결과를 이 순서로 concat하기 때문에
            // get(0)을 쓰면 kakao 출신 사진이 항상 이겨버린다. 이 클래스가 있는 이유(두 출신을
            // 대칭적으로 합치는 것)와 정반대라 max로 명시적으로 고른다.
            PlaceNote newest = withPhoto.stream()
                    .max(Comparator.comparing(PlaceNote::getCreatedAt)).orElseThrow();
            out.put(kakaoId, new Cover(newest.getPhotoThumbUrl(), withPhoto.size()));
        });
        return out;
    }

    /**
     * 상세 모달용 — 그 장소의 노트 전체(최신순). 요청 건마다 <b>쿼리 최대 4회</b>
     * (kakao id 노트 1 + place id 미해소 시 해소용 place 조회 1 + place id 노트 1 + 작성자 배치 1) —
     * 상세는 대상이 장소 1곳뿐이라 이 4는 상수이고 늘 늘어나지 않는다.
     */
    public List<NoteView> notesFor(Long placeId, String kakaoPlaceId) {
        // 상세는 요청 id가 1개뿐이라 귀속 문제가 없다 — 두 키로 긁어 합치고 중복만 제거한다.
        // kakao id가 없는 레지스트리 장소도 있으므로(placeId=44 "개화") 양쪽 다 지원해야 한다.
        List<PlaceNote> notes = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (kakaoPlaceId != null && !kakaoPlaceId.isBlank()) {
            for (PlaceNote n : repo.findVisibleByKakaoIdIn(List.of(kakaoPlaceId))) {
                if (seen.add(n.getId())) notes.add(n);
            }
        }
        Long resolved = placeId;
        if (resolved == null && kakaoPlaceId != null && !kakaoPlaceId.isBlank()) {
            resolved = placeRepo.findAllByKakaoPlaceIdIn(List.of(kakaoPlaceId))
                    .stream().findFirst().map(Place::getId).orElse(null);
        }
        if (resolved != null) {
            for (PlaceNote n : repo.findVisibleByPlaceIdIn(List.of(resolved))) {
                if (seen.add(n.getId())) notes.add(n);
            }
        }
        if (notes.isEmpty()) return List.of();
        notes.sort(Comparator.comparing(PlaceNote::getCreatedAt).reversed());

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
