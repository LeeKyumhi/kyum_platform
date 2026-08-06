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

    public PlaceResolver(PlaceRepository placeRepo,
                         PlaceAliasRepository aliasRepo,
                         @Value("${ingest.resolver.radius-meters:200}") double radiusMeters) {
        this.placeRepo = placeRepo;
        this.aliasRepo = aliasRepo;
        this.radiusMeters = radiusMeters;
    }

    /** 해결 결과 — 확정된 장소이거나, 왜 확정 못 했는지의 사유이거나 둘 중 하나다. */
    public record Resolution(Place place, String unresolvedReason) {

        public boolean isResolved() {
            return place != null;
        }

        public static Resolution resolved(Place place) {
            return new Resolution(place, null);
        }

        public static Resolution unresolved(String reason) {
            return new Resolution(null, reason);
        }
    }

    public Resolution resolve(PlaceClue clue) {
        // ── 1단: 외부 ID는 곧 신원이다 ──────────────────────────────
        Optional<Place> byExternalId = findByExternalId(clue);
        if (byExternalId.isPresent()) {
            Place p = byExternalId.get();
            enrich(p, clue);
            recordAliases(p, clue);
            return Resolution.resolved(p);
        }

        // ── 2단: 이름 + 좌표 ──────────────────────────────────────
        // name_normalized는 추출기가 보낸 값을 쓰지 않는다. 매칭 키를 외부 에이전트가
        // 정하게 두면 프롬프트가 바뀌는 순간 같은 장소가 다른 키로 들어온다.
        String normalized = PlaceNames.normalize(clue.nameRaw());
        List<Place> candidates = candidatesByName(normalized);

        if (!candidates.isEmpty()) {
            if (!clue.hasCoordinates()) {
                return maybeCreate(clue, "name matched but clue has no coordinates to verify");
            }
            List<Place> within = candidates.stream()
                    .filter(p -> p.getLat() != null && p.getLng() != null)
                    .filter(p -> distanceMeters(clue, p) <= radiusMeters)
                    .toList();

            if (within.size() == 1) {
                Place p = within.get(0);
                enrich(p, clue);
                recordAliases(p, clue);
                return Resolution.resolved(p);
            }
            if (within.size() > 1) {
                // 절대 추측하지 않는다 — 전국에 같은 이름의 체인점이 널려 있다
                return Resolution.unresolved(
                        "ambiguous: " + within.size() + " places share this name within radius");
            }
            return maybeCreate(clue, String.format(
                    "name matched but nearest candidate is beyond radius (%.0fm)", radiusMeters));
        }

        // ── 3단: 아무것도 못 찾음 ──────────────────────────────────
        return maybeCreate(clue, "no external id and no name match");
    }

    /**
     * 이름으로 못 붙었을 때 — 외부 ID가 있으면 새 노드를 만들고, 없으면 미해결로 보낸다.
     * 이 분기가 레지스트리 오염을 막는 마지막 방어선이다.
     */
    private Resolution maybeCreate(PlaceClue clue, String reasonIfNotCreated) {
        if (!clue.hasExternalId()) {
            return Resolution.unresolved(reasonIfNotCreated);
        }
        Place created = placeRepo.save(new Place(
                clue.nameRaw(), clue.city(), clue.district(),
                clue.lat(), clue.lng(),
                blankToNull(clue.kakaoPlaceId()), blankToNull(clue.tourApiContentId()),
                clue.category()));
        // 장소를 처음 보는 순간이 곧 별칭을 배우는 순간이다. 여기서 안 남기면
        // 권위 있는 소스(Kakao·TourAPI)가 준 표기 변형을 통째로 버리게 된다.
        recordAliases(created, clue);
        return Resolution.resolved(created);
    }

    private Optional<Place> findByExternalId(PlaceClue clue) {
        if (notBlank(clue.kakaoPlaceId())) {
            Optional<Place> p = placeRepo.findByKakaoPlaceId(clue.kakaoPlaceId());
            if (p.isPresent()) return p;
        }
        if (notBlank(clue.tourApiContentId())) {
            return placeRepo.findByTourApiContentId(clue.tourApiContentId());
        }
        return Optional.empty();
    }

    /** 직접 이름이 같은 장소 + 별칭을 통해 도달하는 장소. 중복은 id 기준으로 제거한다. */
    private List<Place> candidatesByName(String normalized) {
        if (normalized.isEmpty()) return List.of();

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

    private void enrich(Place p, PlaceClue clue) {
        p.enrichMissing(clue.lat(), clue.lng(),
                blankToNull(clue.kakaoPlaceId()), blankToNull(clue.tourApiContentId()),
                clue.category(), clue.city(), clue.district());
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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }
}
