package com.guidematch.knowledge;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 코스 추천 같은 소비자가 인사이트를 붙일 때 쓰는 조회 진입점.
 *
 * <p><b>왜 별도 클래스인가</b>: 소비자가 리포지토리를 직접 쓰면 루프 안에서 단건 조회를 하기
 * 쉽고, Supabase가 시드니 리전이라 왕복이 250ms다. 정차지 5곳이면 그것만으로 1.25초다.
 * 배치 조회만 노출해서 그 실수를 구조적으로 막는다.
 */
@Service
public class PlaceInsightLookup {

    private final PlaceRepository placeRepo;
    private final PlaceInsightRepository insightRepo;

    public PlaceInsightLookup(PlaceRepository placeRepo, PlaceInsightRepository insightRepo) {
        this.placeRepo = placeRepo;
        this.insightRepo = insightRepo;
    }

    /** 소비자에게 나가는 형태 — 내부 엔티티를 그대로 흘리지 않는다. */
    public record InsightView(String kind, Map<String, Object> value, String note, Double confidence) {}

    /**
     * Kakao place id들로 인사이트를 한 번에 가져온다. 쿼리 2회 고정(장소 조회 + 인사이트 조회).
     *
     * @return kakaoPlaceId → 인사이트 목록 (신뢰도 내림차순). 없는 키는 빈 목록이 아니라 아예 없음.
     */
    public Map<String, List<InsightView>> byKakaoPlaceIds(Collection<String> kakaoPlaceIds, String lang) {
        List<String> ids = kakaoPlaceIds.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        // 1회차: kakao id → 우리 place
        Map<Long, String> placeIdToKakao = new LinkedHashMap<>();
        for (Place p : placeRepo.findAllByKakaoPlaceIdIn(ids)) {
            placeIdToKakao.put(p.getId(), p.getKakaoPlaceId());
        }
        if (placeIdToKakao.isEmpty()) return Map.of();

        // 2회차: place ids → 인사이트 (절대 루프 안에서 단건 조회하지 말 것)
        return insightRepo.findByPlaceIdIn(placeIdToKakao.keySet()).stream()
                .collect(Collectors.groupingBy(
                        i -> placeIdToKakao.get(i.getPlaceId()),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparingDouble(PlaceInsight::getConfidence).reversed())
                                .map(i -> toView(i, lang))
                                .toList())));
    }

    /**
     * 우리 place id들로 인사이트를 한 번에 가져온다. <b>쿼리 1회 고정</b>.
     *
     * <p>레지스트리 정차지는 이미 place id를 들고 오므로 {@link #byKakaoPlaceIds}의 1회차
     * (kakao id → place) 조회가 통째로 불필요하다. 그 메서드는 Kakao 폴백 정차지용으로 남는다.
     *
     * @return placeId → 인사이트 목록 (신뢰도 내림차순). 없는 키는 빈 목록이 아니라 아예 없음.
     */
    public Map<Long, List<InsightView>> byPlaceIds(Collection<Long> placeIds, String lang) {
        List<Long> ids = placeIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        return insightRepo.findByPlaceIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        PlaceInsight::getPlaceId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparingDouble(PlaceInsight::getConfidence).reversed())
                                .map(i -> toView(i, lang))
                                .toList())));
    }

    /**
     * 요청 언어 → 한국어 → 아무 언어 순으로 떨어진다.
     * 번역이 아직 안 붙은 사실이라도 한국어로라도 보여주는 편이 아무것도 안 보이는 것보다 낫다.
     */
    private static InsightView toView(PlaceInsight i, String lang) {
        Map<String, String> notes = i.getNoteI18n();
        String note = null;
        if (notes != null && !notes.isEmpty()) {
            note = Optional.ofNullable(notes.get(lang))
                    .orElseGet(() -> Optional.ofNullable(notes.get("ko"))
                            .orElseGet(() -> notes.values().iterator().next()));
        }
        return new InsightView(i.getFactKind().toWire(), i.getValue(), note, i.getConfidence());
    }
}
