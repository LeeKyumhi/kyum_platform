package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlaceInsightRepository extends JpaRepository<PlaceInsight, Long> {

    Optional<PlaceInsight> findByRecordId(String recordId);

    /**
     * 코스 추천이 쓰는 유일한 조회 — 반드시 <b>배치 1회</b>.
     * Supabase가 시드니 리전이라 왕복이 250ms다. 정차지 5곳을 개별 조회하면 그것만으로
     * 1.25초가 날아간다. 절대 루프 안에서 단건 조회하지 말 것.
     */
    List<PlaceInsight> findByPlaceIdIn(Collection<Long> placeIds);
}
