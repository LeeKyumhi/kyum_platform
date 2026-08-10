package com.guidematch.geo;

import com.guidematch.knowledge.Place;
import com.guidematch.knowledge.PlaceKind;
import com.guidematch.knowledge.PlaceKinds;
import com.guidematch.knowledge.PlaceNames;
import com.guidematch.knowledge.PlaceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 코스 정차지를 고른다 — <b>우리 레지스트리가 먼저, Kakao는 모자란 슬롯만.</b>
 *
 * <p><b>왜 뒤집었나</b>: 정차지를 Kakao 실시간 검색이 정하면 지식베이스는 "Kakao가 반환한 것"만
 * 주석할 수 있다. 경복궁·간송미술관처럼 TourAPI에만 있는 문화재·전시관은 영원히 정차지가 못 되는데,
 * 그게 TourAPI를 쓰는 이유 그 자체다. 인사이트 커버리지 상한이 남의 검색 결과에 갇힌다.
 *
 * <p>얇은 범위(아직 수집이 안 된 구)에서는 Kakao가 채운다. 수집이 늘수록 Kakao 의존이
 * 자연히 줄어든다.
 */
@Service
public class CoursePlanner {

    private static final int CITY_RADIUS_METERS = 10000;    // 도시 중심 — 코스용이라 20km보다 좁게
    private static final int DISTRICT_RADIUS_METERS = 4000; // 구 단위 — 도보 동선 위주

    /** 슬롯당 무작위 선택 폭 — 가까운 후보 상위 N 중 랜덤 (동선 유지 + "다시 추천" 다양성) */
    private static final int PICK_POOL = 3;

    /** 같은 장소로 볼 거리 — 중복 제거 3단. */
    private static final double DEDUPE_METERS = 100;

    /** facet 키 → Kakao 카테고리 그룹 코드 (PlaceController와 동일 규약) */
    private static final Map<String, String> CATEGORY_CODES = Map.of(
            "attraction", "AT4", "food", "FD6", "cafe", "CE7", "culture", "CT1");

    /** facet 키 → keyword 검색어 (카테고리 코드가 없는 경우) */
    private static final Map<String, String> KEYWORDS = Map.of("market", "전통시장");

    /**
     * facet 키 → 레지스트리에서 뽑을 장소 종류.
     * <b>여기 없는 종류는 정차지가 될 수 없다</b> — SHOP·LODGING·EVENT·OTHER가 빠지는 지점이고,
     * 축제가 코스에 오르지 않는 근거다.
     */
    private static final Map<String, Set<PlaceKind>> FACET_KINDS = Map.of(
            "attraction", Set.of(PlaceKind.ATTRACTION, PlaceKind.NATURE),
            "culture",    Set.of(PlaceKind.CULTURE),
            "food",       Set.of(PlaceKind.FOOD),
            "cafe",       Set.of(PlaceKind.CAFE),
            "market",     Set.of(PlaceKind.MARKET));

    private final PlaceRepository placeRepo;
    private final KakaoLocalClient kakaoClient;

    public CoursePlanner(PlaceRepository placeRepo, KakaoLocalClient kakaoClient) {
        this.placeRepo = placeRepo;
        this.kakaoClient = kakaoClient;
    }

    /**
     * 정차지 하나. {@code source}가 이 기능이 실제로 일하는지 밖에서 확인하는 유일한 관측 지점이다 —
     * 백필을 빠뜨리면 후보가 0건이 되고 Kakao 폴백이 전부 채워 <b>결과가 예전과 똑같아 보인다.</b>
     */
    public record PlannedStop(
            Long placeId, String kakaoPlaceId,
            String name, String category, String address,
            double lat, double lng, String placeUrl,
            String source) {

        public static PlannedStop ofRegistry(Place p) {
            return new PlannedStop(p.getId(), p.getKakaoPlaceId(), p.getNameKo(),
                    displayCategory(p), p.getAddressKo(), p.getLat(), p.getLng(),
                    kakaoUrlOf(p.getKakaoPlaceId()), "registry");
        }

        /**
         * TourAPI 장소의 {@code category_raw}는 {@code A02>A0206>A02060500} 같은 분류코드다.
         * 그대로 내보내면 <b>사용자에게 코드가 보이고 번역 캐시에도 코드가 쌓인다</b>
         * (컨트롤러의 {@code " > "} 자르기는 공백이 없어 통째로 통과시킨다).
         * 그런 경우에만 종류 라벨로 바꾼다 — Kakao 경로는 읽을 수 있으므로 원문을 유지한다.
         */
        private static String displayCategory(Place p) {
            String raw = p.getCategory();
            if (PlaceKinds.isTourApiCategoryCode(raw)) {
                return p.getPlaceKind() != null ? p.getPlaceKind().koLabel() : "";
            }
            return raw;
        }

        /**
         * 레지스트리 장소도 Kakao id가 있으면 장소 링크를 복원한다.
         * 없으면 null이고, 프론트가 좌표로 지도 링크를 만든다({@code kakaoMapUrl}).
         */
        private static String kakaoUrlOf(String kakaoPlaceId) {
            return kakaoPlaceId == null || kakaoPlaceId.isBlank()
                    ? null : "https://place.map.kakao.com/" + kakaoPlaceId;
        }

        public static PlannedStop ofKakao(KakaoLocalClient.Place p) {
            return new PlannedStop(null, p.id(), p.name(), p.category(), p.address(),
                    p.latitude(), p.longitude(), p.placeUrl(), "kakao");
        }
    }

    public record Plan(List<PlannedStop> stops, double anchorLat, double anchorLng,
                       String resolvedDistrict) {}

    /**
     * Kakao 키가 설정돼 있는가. 컨트롤러가 응답의 {@code kakaoEnabled}를 채우는 데 쓴다 —
     * 프론트는 이 값으로 지도·장소 링크 노출을 판단하므로 <b>실제 값이어야 한다.</b>
     * (컨트롤러가 {@link KakaoLocalClient}를 직접 주입받지 않게 하려고 여기로 위임한다.)
     */
    public boolean isKakaoEnabled() {
        return kakaoClient.isEnabled();
    }

    public Plan plan(KoreanCity city, String district, List<String> slots) {
        boolean validDistrict = district != null && !district.isBlank()
                && KoreanCity.districtsOf(city.key()).stream().anyMatch(d -> d.ko().equals(district));
        String scopeDistrict = validDistrict ? district : null;

        // 레지스트리 조회는 1회. 필요한 종류를 전부 합쳐 가져와 메모리에서 facet별로 나눈다.
        // 슬롯마다 조회하면 Sydney 왕복 250ms가 그대로 응답 시간에 얹힌다.
        Set<PlaceKind> wanted = new HashSet<>();
        for (String facet : slots) wanted.addAll(FACET_KINDS.getOrDefault(facet, Set.of()));
        List<Place> registry = wanted.isEmpty() ? List.of()
                : placeRepo.findCandidates(city.key(), scopeDistrict, wanted);

        double[] anchor = anchorOf(city, scopeDistrict, registry);
        int radius = scopeDistrict != null ? DISTRICT_RADIUS_METERS : CITY_RADIUS_METERS;

        Map<String, List<Place>> registryPool = new HashMap<>();
        for (String facet : new HashSet<>(slots)) {
            Set<PlaceKind> kinds = FACET_KINDS.getOrDefault(facet, Set.of());
            registryPool.put(facet, registry.stream()
                    .filter(p -> kinds.contains(p.getPlaceKind())).toList());
        }

        // Kakao 폴백 풀은 게으르게 채운다 — 레지스트리가 채운 facet은 검색조차 하지 않는다.
        // 수집이 늘수록 Kakao 호출이 자연히 0으로 수렴한다.
        Map<String, List<KakaoLocalClient.Place>> kakaoPool = new HashMap<>();

        List<PlannedStop> picked = new ArrayList<>();
        Set<String> usedKakaoIds = new HashSet<>();
        Set<Long> usedPlaceIds = new HashSet<>();
        Set<String> usedNames = new LinkedHashSet<>();
        double prevLat = anchor[0], prevLng = anchor[1];

        for (String facet : slots) {
            PlannedStop chosen = pickFromRegistry(registryPool.getOrDefault(facet, List.of()),
                    prevLat, prevLng, picked, usedPlaceIds, usedKakaoIds, usedNames);

            if (chosen == null && kakaoClient.isEnabled()) {
                List<KakaoLocalClient.Place> pool = kakaoPool.computeIfAbsent(facet,
                        f -> searchKakao(f, anchor[0], anchor[1], radius));
                chosen = pickFromKakao(pool, prevLat, prevLng, picked, usedKakaoIds, usedNames);
            }
            if (chosen == null) continue; // 이 슬롯은 건너뜀 (예: 구 안에 전통시장이 없음)

            picked.add(chosen);
            if (chosen.placeId() != null) usedPlaceIds.add(chosen.placeId());
            if (chosen.kakaoPlaceId() != null) usedKakaoIds.add(chosen.kakaoPlaceId());
            usedNames.add(PlaceNames.normalize(chosen.name()));
            prevLat = chosen.lat();
            prevLng = chosen.lng();
        }
        return new Plan(picked, anchor[0], anchor[1], scopeDistrict);
    }

    /**
     * 구 중심 좌표.
     *
     * <p>Kakao 지오코딩에만 의존하면 키가 없을 때 구 단위 최근접 이웃의 출발점이 사라진다.
     * 레지스트리 장소들의 무게중심은 <b>이미 가진 데이터로 풀리고</b>, 실제 수집 분포를
     * 반영해 도리어 정확하다. 레지스트리가 빈 구에서는 지오코딩(있으면) → 도시 중심 순으로 떨어진다.
     */
    private double[] anchorOf(KoreanCity city, String district, List<Place> registry) {
        if (district == null) return new double[]{city.lat(), city.lng()};

        if (kakaoClient.isEnabled()) {
            double[] geo = kakaoClient.geocodeRegion(city.nameKo() + " " + district);
            if (geo != null) return geo;
        }
        if (!registry.isEmpty()) {
            double lat = registry.stream().mapToDouble(Place::getLat).average().orElse(city.lat());
            double lng = registry.stream().mapToDouble(Place::getLng).average().orElse(city.lng());
            return new double[]{lat, lng};
        }
        return new double[]{city.lat(), city.lng()};
    }

    private List<KakaoLocalClient.Place> searchKakao(String facet, double lat, double lng, int radius) {
        String code = CATEGORY_CODES.get(facet);
        List<KakaoLocalClient.Place> found = code != null
                ? kakaoClient.searchByCategory(code, lat, lng, radius)
                : kakaoClient.searchByKeyword(KEYWORDS.getOrDefault(facet, facet), lat, lng, radius);
        return found.stream().filter(p -> p.latitude() != null && p.longitude() != null).toList();
    }

    private PlannedStop pickFromRegistry(List<Place> pool, double fromLat, double fromLng,
                                         List<PlannedStop> already, Set<Long> usedPlaceIds,
                                         Set<String> usedKakaoIds, Set<String> usedNames) {
        List<Place> candidates = pool.stream()
                .filter(p -> !usedPlaceIds.contains(p.getId()))
                .filter(p -> p.getKakaoPlaceId() == null || !usedKakaoIds.contains(p.getKakaoPlaceId()))
                .filter(p -> !usedNames.contains(PlaceNames.normalize(p.getNameKo())))
                .filter(p -> notNear(already, p.getLat(), p.getLng()))
                .sorted((a, b) -> Double.compare(
                        GeoUtils.distanceKm(fromLat, fromLng, a.getLat(), a.getLng()),
                        GeoUtils.distanceKm(fromLat, fromLng, b.getLat(), b.getLng())))
                .limit(PICK_POOL)
                .toList();
        if (candidates.isEmpty()) return null;
        return PlannedStop.ofRegistry(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    private PlannedStop pickFromKakao(List<KakaoLocalClient.Place> pool, double fromLat, double fromLng,
                                      List<PlannedStop> already, Set<String> usedKakaoIds,
                                      Set<String> usedNames) {
        List<KakaoLocalClient.Place> candidates = pool.stream()
                .filter(p -> !usedKakaoIds.contains(p.id()))
                .filter(p -> !usedNames.contains(PlaceNames.normalize(p.name())))
                .filter(p -> notNear(already, p.latitude(), p.longitude()))
                .sorted((a, b) -> Double.compare(
                        GeoUtils.distanceKm(fromLat, fromLng, a.latitude(), a.longitude()),
                        GeoUtils.distanceKm(fromLat, fromLng, b.latitude(), b.longitude())))
                .limit(PICK_POOL)
                .toList();
        if (candidates.isEmpty()) return null;
        return PlannedStop.ofKakao(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    /**
     * 중복 제거 3단 중 마지막 — 이름도 id도 다르지만 사실상 같은 지점.
     * ("덕수궁"과 "덕수궁 대한문"이 각각 다른 출처에서 오는 상황이 실제로 관측됐다.)
     */
    private boolean notNear(List<PlannedStop> already, Double lat, Double lng) {
        if (lat == null || lng == null) return false;
        return already.stream().noneMatch(s ->
                GeoUtils.distanceKm(s.lat(), s.lng(), lat, lng) * 1000.0 <= DEDUPE_METERS);
    }
}
