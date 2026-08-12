package com.guidematch.knowledge;

import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /**
     * 목록 카드용 — 대표 썸네일 1장과 사진 개수. 팁은 목록에 싣지 않는다.
     *
     * @param thumbUrl          대표 사진. 여행자 사진이 있으면 그 최신, 없으면 공식 사진.
     * @param photoCount        <b>여행자</b> 사진 수. 0이면 null이다 — "0장"을 담아 보내면
     *                          프론트가 그걸 렌더할 여지가 생긴다.
     * @param officialUrl       공식 사진(TourAPI). 발행처와 <b>쌍으로만</b> 실린다.
     * @param officialPublisher 공식 사진 발행처. 없으면 사진도 없다(계약 §16).
     */
    public record Cover(String thumbUrl, Integer photoCount,
                        String officialUrl, String officialPublisher) {}

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
        // 공식 사진(시드)도 같은 조회에서 함께 꺼낸다 — 쿼리를 늘리지 않는다.
        // 노트가 하나도 없는 장소가 절대다수이고(여행자가 아직 안 왔다), 그 장소들은
        // 이게 없으면 목록에서 영원히 아이콘으로 남는다.
        Map<String, String[]> officialByKakao = new LinkedHashMap<>();
        for (Place p : placeRepo.findAllByKakaoPlaceIdIn(ids)) {
            placeIdToKakao.put(p.getId(), p.getKakaoPlaceId());
            // 출처를 못 밝히는 사진은 띄우지 않는다 — 둘 다 있을 때만 싣는다(계약 §16).
            if (notBlank(p.getImageUrl()) && notBlank(p.getImagePublisher())) {
                officialByKakao.put(p.getKakaoPlaceId(),
                        new String[]{ https(p.getImageUrl()), p.getImagePublisher() });
            }
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
        // 노트가 없는 장소도 공식 사진이 있으면 표시 대상이다 — 그래서 노트가 아니라
        // "노트 ∪ 공식"을 순회한다. 노트 쪽만 돌면 시드 사진은 영원히 도달하지 못한다.
        Set<String> keys = new LinkedHashSet<>(byKakao.keySet());
        keys.addAll(officialByKakao.keySet());

        for (String kakaoId : keys) {
            List<PlaceNote> withPhoto = byKakao.getOrDefault(kakaoId, List.of()).stream()
                    .filter(n -> n.getPhotoThumbUrl() != null).toList();
            String[] official = officialByKakao.get(kakaoId);
            if (withPhoto.isEmpty() && official == null) continue;   // 규칙: 0은 표시하지 않는다

            // 대표는 여행자 사진이 우선이다 — 우리가 가진 것보다 방금 다녀온 사람의 사진이 낫다.
            // 공식 사진은 상세 스트립 맨 앞에서 계속 보인다.
            //
            // 대표 썸네일은 그중 최신이어야 한다. 두 쿼리 결과를 순서대로 concat하기 때문에
            // get(0)을 쓰면 kakao 출신 사진이 항상 이겨버린다 — 두 출신을 대칭적으로 합친다는
            // 이 클래스의 존재 이유와 정반대라 max로 명시적으로 고른다.
            String thumb = withPhoto.isEmpty()
                    ? official[0]
                    : withPhoto.stream().max(Comparator.comparing(PlaceNote::getCreatedAt))
                            .orElseThrow().getPhotoThumbUrl();

            out.put(kakaoId, new Cover(
                    thumb,
                    withPhoto.isEmpty() ? null : withPhoto.size(),
                    official == null ? null : official[0],
                    official == null ? null : official[1]));
        }
        return out;
    }

    /**
     * 레지스트리 장소 id들의 대표 사진. <b>코스 추천 정차지용</b>이다.
     *
     * <p>왜 kakao 경로로 안 되나: 레지스트리 전용 장소는 kakao id가 아예 없다(실측 19곳 중 11곳).
     * 추천은 그런 정차지를 그대로 내보내므로, kakao 키로만 조회하면 그 장소들의 사진은
     * <b>구조적으로 도달 불가</b>다 — 화면에는 "사진이 아직 없네"로만 보인다.
     *
     * <p>규칙(여행자 사진 우선 · 발행처 쌍 · https 승격 · 0은 키 없음)은
     * {@link #coversByKakaoIds}와 <b>같은 것을 쓴다</b>. 두 벌로 갈라지면 같은 장소가 화면마다
     * 다른 사진을 보여주게 된다.
     *
     * <p>쿼리 <b>2회 고정</b>(장소 배치 1 + 노트 배치 1) — 정차지 수와 무관하다.
     */
    public Map<Long, Cover> coversByPlaceIds(Collection<Long> placeIds) {
        List<Long> ids = placeIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        Map<Long, String[]> officialByPlace = new LinkedHashMap<>();
        for (Place p : placeRepo.findAllById(ids)) {
            if (notBlank(p.getImageUrl()) && notBlank(p.getImagePublisher())) {
                officialByPlace.put(p.getId(), new String[]{ https(p.getImageUrl()), p.getImagePublisher() });
            }
        }

        Map<Long, List<PlaceNote>> byPlace = new LinkedHashMap<>();
        for (PlaceNote n : repo.findVisibleByPlaceIdIn(ids)) {
            if (n.getPhotoThumbUrl() == null) continue;
            byPlace.computeIfAbsent(n.getPlaceId(), k -> new ArrayList<>()).add(n);
        }

        Map<Long, Cover> out = new LinkedHashMap<>();
        Set<Long> keys = new LinkedHashSet<>(byPlace.keySet());
        keys.addAll(officialByPlace.keySet());
        for (Long placeId : keys) {
            List<PlaceNote> withPhoto = byPlace.getOrDefault(placeId, List.of());
            String[] official = officialByPlace.get(placeId);
            if (withPhoto.isEmpty() && official == null) continue;
            String thumb = withPhoto.isEmpty()
                    ? official[0]
                    : withPhoto.stream().max(Comparator.comparing(PlaceNote::getCreatedAt))
                            .orElseThrow().getPhotoThumbUrl();
            out.put(placeId, new Cover(
                    thumb,
                    withPhoto.isEmpty() ? null : withPhoto.size(),
                    official == null ? null : official[0],
                    official == null ? null : official[1]));
        }
        return out;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /**
     * TourAPI는 이미지 URL을 <b>http로</b> 준다(실측: {@code tong.visitkorea.or.kr}).
     * 배포는 https라 그대로 내보내면 브라우저가 mixed content로 차단한다 — 서버는 정상이고
     * 화면에서만 조용히 깨진다. 같은 URL이 https로도 200을 주는 것을 확인하고 올린다.
     */
    private static String https(String url) {
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
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
