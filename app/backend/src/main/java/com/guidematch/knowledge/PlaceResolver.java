package com.guidematch.knowledge;

import com.guidematch.geo.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 해결 사다리 — 들어온 단서가 <b>어느 장소인지</b> 결정하는 유일한 지점.
 *
 * <pre>
 *   1단  외부 ID 일치 (kakao_place_id / tour_api_content_id)   → 확정
 *   2단  이름(또는 별칭) 일치 AND 좌표가 임계 반경 이내         → 확정 + 별칭 기록
 *   3단  그 외                                                → 미해결 보관함
 * </pre>
 *
 * <p><b>왜 이렇게까지 보수적인가</b>: 잘못된 병합은 되돌릴 수 없다. 두 장소의 인사이트가
 * 한 노드에 섞이면 어느 사실이 어느 장소 것이었는지 복원할 방법이 없다. 반대로 미해결은
 * 원본을 그대로 보관하므로 나중에 언제든 해결할 수 있다. 그래서 애매하면 무조건 미해결이다.
 *
 * <p><b>신규 노드 생성 정책</b>: 외부 ID가 있는 소스(Kakao·TourAPI)만 장소를 새로 만들 수 있다.
 * 이름만 있는 소스(블로그)가 노드를 만들 수 있게 두면, 같은 장소가 표기마다 새 노드로 쪼개져
 * 자산이 영원히 복리가 안 된다.
 */
@Component
public class PlaceResolver {

    private final PlaceRepository placeRepo;
    private final PlaceAliasRepository aliasRepo;

    /**
     * 이름이 같을 때 "같은 장소"로 볼 최대 거리(m).
     *
     * <p>상수로 두면 안 된다. 홍대·성수는 같은 건물 다른 층에 별개 카페가 20m 안에 있어
     * 넓으면 과병합되고, 서울숲·경복궁은 한 장소가 400m 넘게 퍼져 있어 좁으면 미병합된다.
     * 하나의 값으로 둘 다 만족시킬 수 없으므로 튜닝 가능해야 한다.
     */
    private final double radiusMeters;

    /**
     * 이름이 같지만 반경 밖일 때, "새 노드를 만들어도 안전한 거리"의 하한(m).
     *
     * <p>이 사이 구간은 병합도 신규 생성도 하지 않고 <b>미해결 보관함</b>으로 보낸다.
     * 둘 다 틀릴 수 있고 <b>틀린 쪽의 비용이 비대칭</b>이기 때문이다 — 중복 노드는 되돌릴 수
     * 없지만(그 뒤 모든 단서가 ambiguous 거절이거나 오병합이 된다) 미해결은 원본을 그대로
     * 보관하므로 언제든 되살릴 수 있다.
     *
     * <p>2km를 넘으면 새 노드로 둔다. 다른 도시의 동명 장소·체인점은 진짜로 별개이고,
     * 이걸 보관함으로 보내면 정상 시딩이 전부 그쪽으로 새 나간다.
     */
    private final double suspectRadiusMeters;

    public PlaceResolver(PlaceRepository placeRepo,
                         PlaceAliasRepository aliasRepo,
                         @Value("${ingest.resolver.radius-meters:200}") double radiusMeters,
                         @Value("${ingest.resolver.suspect-radius-meters:2000}") double suspectRadiusMeters) {
        this.placeRepo = placeRepo;
        this.aliasRepo = aliasRepo;
        this.radiusMeters = radiusMeters;
        this.suspectRadiusMeters = suspectRadiusMeters;
    }

    /**
     * 해결 결과 — 확정된 장소이거나, 왜 확정 못 했는지의 사유이거나 둘 중 하나다.
     *
     * @param needsSave 호출부가 {@code placeRepo.save()}를 불러야 하는가.
     *                  스냅샷에서 온 엔티티는 detached라 {@code save()}가 {@code merge}로
     *                  <b>행마다 SELECT를 한 번씩</b> 낸다(Sydney 왕복 250ms). 바뀐 게 없으면
     *                  저장할 이유도 없으므로, 재적재는 이 플래그 덕에 읽기·쓰기 모두 0이 된다.
     *                  새로 만든 장소는 이미 저장됐으므로 false다.
     */
    public record Resolution(Place place, String unresolvedReason, boolean needsSave) {

        public boolean isResolved() {
            return place != null;
        }

        public static Resolution resolved(Place place, boolean needsSave) {
            return new Resolution(place, null, needsSave);
        }

        /** 이미 저장된(혹은 저장이 불필요한) 장소. */
        public static Resolution resolved(Place place) {
            return new Resolution(place, null, false);
        }

        public static Resolution unresolved(String reason) {
            return new Resolution(null, reason, false);
        }
    }

    public Resolution resolve(PlaceClue clue) {
        return resolve(clue, RegistrySnapshot.empty());
    }

    /**
     * 스냅샷이 로드돼 있으면 읽기 왕복이 0이다.
     *
     * <p>⚠ <b>스냅샷 미스에 DB로 폴백하지 않는다.</b> {@link RegistrySnapshot#loadAll}이
     * 범위 필터 없이 전체를 읽으므로 스냅샷의 미스는 곧 DB의 미스이고, 폴백을 넣으면 신규
     * 장소마다 헛왕복이 생겨 절약이 사라진다. <b>이 전제는 전체 로딩에 전적으로 의존한다</b> —
     * 나중에 범위 로딩으로 바꾸려면 반드시 폴백을 같이 넣어야 한다.
     */
    public Resolution resolve(PlaceClue clue, RegistrySnapshot snapshot) {
        // ── 1단: 외부 ID는 곧 신원이다 ──────────────────────────────
        Optional<Place> byExternalId = findByExternalId(clue, snapshot);
        if (byExternalId.isPresent()) {
            Place p = byExternalId.get();
            boolean changed = enrich(p, clue);
            recordAliases(p, clue);
            return Resolution.resolved(p, changed);
        }

        // ── 2단: 이름 + 좌표 ──────────────────────────────────────
        // name_normalized는 추출기가 보낸 값을 쓰지 않는다. 매칭 키를 외부 에이전트가
        // 정하게 두면 프롬프트가 바뀌는 순간 같은 장소가 다른 키로 들어온다.
        String normalized = PlaceNames.normalize(clue.nameRaw());
        List<Place> candidates = candidatesByName(normalized, snapshot);
        if (candidates.isEmpty()) {
            // 2차 조회: 괄호절을 뺀 키로 한 번 더. "간송미술관(서울 보화각)"이 "간송미술관"에 붙는다.
            // 저장 컬럼에는 언제나 정확 키가 들어가고 완화 키는 조회에만 쓴다 —
            // 매칭을 늘리기만 하고 기존 구분을 없애지 않는다.
            String relaxed = PlaceNames.normalize(stripParenthetical(clue.nameRaw()));
            if (!relaxed.isEmpty() && !relaxed.equals(normalized)) {
                candidates = candidatesByName(relaxed, snapshot);
            }
        }

        if (!candidates.isEmpty()) {
            if (!clue.hasCoordinates()) {
                return maybeCreate(clue, "name matched but clue has no coordinates to verify", snapshot);
            }
            List<Place> within = candidates.stream()
                    .filter(p -> p.getLat() != null && p.getLng() != null)
                    .filter(p -> distanceMeters(clue, p) <= radiusMeters)
                    .toList();

            if (within.size() == 1) {
                Place p = within.get(0);
                boolean changed = enrich(p, clue);
                recordAliases(p, clue);
                return Resolution.resolved(p, changed);
            }
            if (within.size() > 1) {
                // 절대 추측하지 않는다 — 전국에 같은 이름의 체인점이 널려 있다
                return Resolution.unresolved(
                        "ambiguous: " + within.size() + " places share this name within radius");
            }
            // 이름은 맞는데 반경 밖이다. 얼마나 밖인지가 판단을 가른다.
            double nearest = candidates.stream()
                    .filter(p -> p.getLat() != null && p.getLng() != null)
                    .mapToDouble(p -> distanceMeters(clue, p))
                    .min().orElse(Double.MAX_VALUE);

            if (nearest <= suspectRadiusMeters) {
                // ★ 의심 구간 — 같은 장소의 다른 입구일 수도, 다른 장소일 수도 있다.
                // 여기서 새 노드를 만들면 name_normalized가 같은 행이 둘이 되고, 그 다음부터
                // 이 장소에 오는 모든 단서가 ambiguous 거절이거나 오병합이 된다. 되돌릴 수 없고
                // 조용하다. 역방향 시딩(Kakao 장소명으로 TourAPI 조회)은 정확 이름 일치를
                // 최대화하는 기법이라 이 경로를 상시로 밟는다 — 경복궁처럼 한 부지가 400m 넘게
                // 퍼진 곳이 딱 그렇다. 조용한 중복보다 보이는 미해결이 낫다.
                return Resolution.unresolved(String.format(
                        "suspect: name matches but %.0fm away (merge radius %.0fm, suspect band %.0fm)",
                        nearest, radiusMeters, suspectRadiusMeters));
            }
            return maybeCreate(clue, String.format(
                    "name matched but nearest candidate is beyond radius (%.0fm)", radiusMeters), snapshot);
        }

        // ── 3단: 아무것도 못 찾음 ──────────────────────────────────
        return maybeCreate(clue, "no external id and no name match", snapshot);
    }

    /**
     * 이름으로 못 붙었을 때 — 외부 ID가 있으면 새 노드를 만들고, 없으면 미해결로 보낸다.
     * 이 분기가 레지스트리 오염을 막는 마지막 방어선이다.
     */
    private Resolution maybeCreate(PlaceClue clue, String reasonIfNotCreated, RegistrySnapshot snapshot) {
        if (!clue.hasExternalId()) {
            return Resolution.unresolved(reasonIfNotCreated);
        }
        Place created = placeRepo.save(new Place(
                clue.nameRaw(), clue.city(), clue.district(),
                clue.lat(), clue.lng(),
                blankToNull(clue.kakaoPlaceId()), blankToNull(clue.tourApiContentId()),
                clue.category(), blankToNull(clue.addressRaw())));
        // 장소를 처음 보는 순간이 곧 별칭을 배우는 순간이다. 여기서 안 남기면
        // 권위 있는 소스(Kakao·TourAPI)가 준 표기 변형을 통째로 버리게 된다.
        recordAliases(created, clue);
        // 같은 파일 안의 두 번째 등장이 새 노드가 되지 않도록 즉시 보이게 한다
        snapshot.add(created);
        return Resolution.resolved(created);
    }

    private Optional<Place> findByExternalId(PlaceClue clue, RegistrySnapshot snapshot) {
        if (notBlank(clue.kakaoPlaceId())) {
            Optional<Place> p = snapshot.isLoaded()
                    ? snapshot.byKakaoId(clue.kakaoPlaceId())
                    : placeRepo.findByKakaoPlaceId(clue.kakaoPlaceId());
            if (p.isPresent()) return p;
        }
        if (notBlank(clue.tourApiContentId())) {
            return snapshot.isLoaded()
                    ? snapshot.byTourApiId(clue.tourApiContentId())
                    : placeRepo.findByTourApiContentId(clue.tourApiContentId());
        }
        return Optional.empty();
    }

    /** 직접 이름이 같은 장소 + 별칭을 통해 도달하는 장소. 중복은 id 기준으로 제거한다. */
    private List<Place> candidatesByName(String normalized, RegistrySnapshot snapshot) {
        if (normalized.isEmpty()) return List.of();

        if (snapshot.isLoaded()) {
            // 스냅샷은 장소와 별칭을 같은 이름 인덱스에 합쳐 두었다 — 중복은 id로 제거한다
            Map<Long, Place> hits = new LinkedHashMap<>();
            for (Place p : snapshot.byName(normalized)) hits.put(keyOf(p), p);
            return new ArrayList<>(hits.values());
        }

        Map<Long, Place> merged = new LinkedHashMap<>();
        for (Place p : placeRepo.findByNameNormalized(normalized)) {
            merged.put(keyOf(p), p);
        }

        List<Long> aliasPlaceIds = aliasRepo.findByAliasNormalized(normalized).stream()
                .map(PlaceAlias::getPlaceId)
                .filter(id -> id != null && !merged.containsKey(id))
                .distinct()
                .toList();
        if (!aliasPlaceIds.isEmpty()) {
            for (Place p : placeRepo.findAllById(aliasPlaceIds)) {
                merged.put(keyOf(p), p);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 아직 저장 전인 엔티티(id=null)도 후보가 될 수 있어 identityHashCode로 대체 키를 만든다. */
    private static Long keyOf(Place p) {
        return p.getId() != null ? p.getId() : (long) -System.identityHashCode(p);
    }

    private double distanceMeters(PlaceClue clue, Place p) {
        return GeoUtils.distanceKm(clue.lat(), clue.lng(), p.getLat(), p.getLng()) * 1000.0;
    }

    private boolean enrich(Place p, PlaceClue clue) {
        return p.enrichMissing(clue.lat(), clue.lng(),
                blankToNull(clue.kakaoPlaceId()), blankToNull(clue.tourApiContentId()),
                clue.category(), clue.city(), clue.district(), blankToNull(clue.addressRaw()));
    }

    /**
     * 표기 흔들림을 다음 실행에 물려준다 — 한 번 "Onion Seongsu = 어니언 성수"를 배우면
     * 다음부터는 그 표기만으로도 곧바로 붙는다. <b>돌수록 매칭이 좋아지는 부분이다.</b>
     *
     * <p>별칭의 출처는 추출기가 실은 {@code aliases} 배열이다. {@code nameRaw} 자체는
     * 정규화하면 이미 이 장소의 이름과 같아지므로(2단이 그걸로 매칭했다) 새로 배울 게 없다.
     */
    private void recordAliases(Place p, PlaceClue clue) {
        if (p.getId() == null) return; // 아직 저장 전이면 FK를 걸 수 없다
        for (String raw : clue.aliases()) {
            String normalized = PlaceNames.normalize(raw);
            if (normalized.isEmpty() || normalized.equals(p.getNameNormalized())) continue;
            if (aliasRepo.existsByPlaceIdAndAliasNormalized(p.getId(), normalized)) continue;
            aliasRepo.save(new PlaceAlias(p.getId(), raw, clue.sourceKind()));
        }
    }

    /**
     * 괄호절 제거 — {@code 간송미술관(서울 보화각)} → {@code 간송미술관}.
     *
     * <p><b>{@link PlaceNames#normalize} 자체는 바꾸지 않는다.</b> 두 가지 이유가 있다.
     * ① {@code name_normalized}는 <b>저장된 컬럼</b>이라 함수를 바꾸면 기존 행의 키가 전부
     * 어긋나 매칭이 도리어 나빠진다. ② 괄호절을 정규화 단계에서 접으면
     * {@code 스타벅스(명동점)}과 {@code 스타벅스(을지로점)}이 같은 키가 되어, 안전한
     * ambiguous 거절 대신 <b>오병합</b>이 난다. 그래서 조회에만 쓰는 별도 함수로 둔다.
     */
    private static String stripParenthetical(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[（(\\[][^）)\\]]*[）)\\]]", " ").trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }
}
