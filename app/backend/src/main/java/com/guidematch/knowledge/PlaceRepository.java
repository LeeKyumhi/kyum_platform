package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByKakaoPlaceId(String kakaoPlaceId);

    /** 코스 추천용 배치 조회 — 정차지마다 단건 조회하면 시드니 왕복 250ms에 즉사한다. */
    List<Place> findAllByKakaoPlaceIdIn(Collection<String> kakaoPlaceIds);

    Optional<Place> findByTourApiContentId(String tourApiContentId);

    /**
     * 이름이 같은 후보 전부 — 거리 판정은 호출부({@link PlaceResolver})가 한다.
     * 동명이인 장소(전국의 "스타벅스")가 많으므로 여기서 하나로 좁히면 안 된다.
     */
    List<Place> findByNameNormalized(String nameNormalized);
}
