package com.guidematch.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 레지스트리 전체(장소·별칭·이미 본 소스 URL)를 메모리에 올린 것.
 *
 * <p><b>왜 필요한가</b>: 적재는 장소 1건당 조회 4회 + 저장으로 ≈8왕복을 쓰는데 Supabase가
 * Sydney라 왕복이 250ms다. 40건에 58초가 걸리고, {@code codex exec}는 그 전에 세션을 끝내
 * <b>JVM을 쓰기 도중에 죽인다.</b> 유실은 항상 파일 뒷부분이고 {@code exit 0}으로 보고된다.
 *
 * <p><b>★ 왜 범위가 아니라 전체인가</b>: {@code kakao_place_id}·{@code tour_api_content_id}는
 * {@code uk_places_kakao}·{@code uk_places_tour_api}로 <b>전역 유니크</b>인데, 저장된
 * {@code district}가 매니페스트 범위와 다른 행이 있다(실측: 간송미술관은 주소가 성북구인데
 * {@code district='중구'}로 들어가 있다 — 역방향 시딩이 이런 행을 본격적으로 만든다).
 * 범위만 올리면 기존 장소가 미스로 나와 새 노드를 만들려다 <b>unique 제약 위반</b>이 나고,
 * 어제까지 멱등이던 재적재가 갑자기 실패한다. 전체를 올리면
 * <b>스냅샷의 미스 = DB의 미스</b>이므로 폴백 없이도 사다리 의미가 정확히 보존된다.
 *
 * <p>현재 53행이고 수만 행까지 문제없다(배치 프로세스다). <b>10만 행을 넘으면 이 결정을
 * 다시 볼 것</b> — 그때는 범위 스냅샷 + DB 폴백으로 바꾼다(절약은 줄지만 안전하다).
 *
 * <p><b>불변 조건</b>: 이 클래스는 해결 사다리의 <b>의미를 바꾸지 않는다.</b> 후보를 어디서
 * 가져오는지만 바꿀 뿐, 순서·거리 판정·ambiguous 거절은 {@link PlaceResolver}가 그대로 한다.
 * 그리고 같은 실행에서 만든 장소는 {@link #add}로 즉시 반영돼야 한다 — 안 그러면 같은 파일
 * 안의 두 번째 등장이 새 노드가 되어 자산이 쪼개진다.
 */
public final class RegistrySnapshot {

    private final Map<String, Place> byKakaoId = new HashMap<>();
    private final Map<String, Place> byTourApiId = new HashMap<>();
    private final Map<String, List<Place>> byName = new LinkedHashMap<>();
    /** 이미 본 소스 URL 해시 — {@code touchSource}의 findByUrlHash 왕복을 없앤다. */
    private final Set<String> sourceUrlHashes = new HashSet<>();
    /** 이번 실행에서 실제로 본 URL 해시 — 실행 끝에 커서를 한 번에 옮기는 데 쓴다. */
    private final Set<String> touchedThisRun = new HashSet<>();
    private final boolean loaded;
    private int places;

    private RegistrySnapshot(boolean loaded) {
        this.loaded = loaded;
    }

    /** 스냅샷 없이 동작하는 모드 — 기존처럼 매번 DB를 친다(단발 호출·테스트용). */
    public static RegistrySnapshot empty() {
        return new RegistrySnapshot(false);
    }

    public static RegistrySnapshot of(List<Place> places, List<PlaceAlias> aliases,
                                      Set<String> sourceUrlHashes) {
        RegistrySnapshot s = new RegistrySnapshot(true);
        places.forEach(s::add);

        Map<Long, Place> byId = new HashMap<>();
        places.forEach(p -> byId.put(p.getId(), p));
        for (PlaceAlias a : aliases) {
            Place p = byId.get(a.getPlaceId());
            // 별칭은 이름 인덱스에 함께 넣는다 — candidatesByName이 둘을 합쳐 보던 것과 같은 결과다
            if (p != null) s.byName.computeIfAbsent(a.getAliasNormalized(), k -> new ArrayList<>()).add(p);
        }
        s.sourceUrlHashes.addAll(sourceUrlHashes);
        return s;
    }

    public static RegistrySnapshot loadAll(PlaceRepository placeRepo,
                                           PlaceAliasRepository aliasRepo,
                                           IngestSourceRepository sourceRepo) {
        List<Place> places = placeRepo.findAll();
        List<PlaceAlias> aliases = aliasRepo.findAll();
        Set<String> hashes = sourceRepo.findAll().stream()
                .map(IngestSource::getUrlHash).collect(Collectors.toSet());
        return of(places, aliases, hashes);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int placeCount() {
        return places;
    }

    /** 같은 실행에서 새로 만든 장소를 즉시 보이게 한다. */
    public void add(Place p) {
        if (p.getKakaoPlaceId() != null) byKakaoId.put(p.getKakaoPlaceId(), p);
        if (p.getTourApiContentId() != null) byTourApiId.put(p.getTourApiContentId(), p);
        byName.computeIfAbsent(p.getNameNormalized(), k -> new ArrayList<>()).add(p);
        places++;
    }

    public Optional<Place> byKakaoId(String id) {
        return Optional.ofNullable(id == null ? null : byKakaoId.get(id));
    }

    public Optional<Place> byTourApiId(String id) {
        return Optional.ofNullable(id == null ? null : byTourApiId.get(id));
    }

    public List<Place> byName(String normalized) {
        return byName.getOrDefault(normalized, List.of());
    }

    public boolean hasSourceHash(String hash) {
        return sourceUrlHashes.contains(hash);
    }

    public void rememberSourceHash(String hash) {
        sourceUrlHashes.add(hash);
    }

    public void rememberTouched(String hash) {
        touchedThisRun.add(hash);
    }

    public Set<String> touchedSourceHashes() {
        return touchedThisRun;
    }
}
