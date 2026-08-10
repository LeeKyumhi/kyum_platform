package com.guidematch.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 추천 결과 신호를 남긴다 — 노출됨 → 담김 → 예약됨.
 *
 * <p><b>v1에서는 아무도 이 데이터를 읽지 않는다.</b> 그래도 지금 남기는 이유는 되찾을 수
 * 없기 때문이다. 랭킹(하위 프로젝트 4)이 붙는 시점에 몇 달치 실적이 이미 있는 것과, 그때부터
 * 0에서 모으기 시작하는 것의 차이가 크다.
 *
 * <p><b>절대 요청을 실패시키지 않는다.</b> 신호 기록은 부가 기능이라, 여기서 예외가 새어나가
 * 코스 추천 응답이 깨지면 본말전도다. 실패는 로그로만 남긴다.
 */
@Service
public class SignalRecorder {

    private static final Logger log = LoggerFactory.getLogger(SignalRecorder.class);

    private final RecommendationSignalRepository repo;
    private final PlaceRepository placeRepo;

    public SignalRecorder(RecommendationSignalRepository repo, PlaceRepository placeRepo) {
        this.repo = repo;
        this.placeRepo = placeRepo;
    }

    /**
     * 노출된 정차지 하나에 대한 참조.
     * 레지스트리 유래는 {@code placeId}를 이미 알고, Kakao 폴백은 {@code kakaoPlaceId}만 안다.
     */
    public record StopRef(Long placeId, String kakaoPlaceId) {}

    /**
     * 코스 추천이 정차지들을 사용자에게 보여줬다.
     *
     * @param stops     노출된 정차지들
     * @param courseRef 어떤 추천이었는지 (예: "Seoul/중구/cafe")
     */
    public void recordShown(List<StopRef> stops, String courseRef, Long userId) {
        try {
            if (stops.isEmpty()) return;

            // 아직 place_id를 모르는 것만 한 번에 조회한다 (쿼리 최대 1회).
            // 정차지마다 조회하면 시드니 왕복 250ms가 그대로 응답 시간에 얹힌다.
            List<String> unknown = stops.stream()
                    .filter(s -> s.placeId() == null)
                    .map(StopRef::kakaoPlaceId)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct().toList();
            Map<String, Long> resolved = unknown.isEmpty() ? Map.of()
                    : placeRepo.findAllByKakaoPlaceIdIn(unknown).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Place::getKakaoPlaceId, Place::getId, (a, b) -> a));

            List<RecommendationSignal> rows = stops.stream()
                    // 아직 우리 레지스트리에 없는 장소도 기록한다 — place_id만 null.
                    // "추천에는 나왔는데 지식이 없는 장소"가 곧 다음 수집 우선순위다.
                    .map(s -> new RecommendationSignal(
                            RecommendationSignal.EventType.SHOWN,
                            s.placeId() != null ? s.placeId() : resolved.get(s.kakaoPlaceId()),
                            courseRef, userId))
                    .toList();
            repo.saveAll(rows);
        } catch (Exception e) {
            log.warn("추천 노출 신호 기록 실패 — 무시하고 진행: {}", e.toString());
        }
    }
}
