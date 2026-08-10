package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 백필 대상 — 컬럼이 갓 추가돼 종류가 비어 있는 행.
     * 평시에는 0건이라 비용이 없고, 0건이 아니면 적재 경로에 구멍이 났다는 신호다.
     */
    List<Place> findByPlaceKindIsNull();

    /**
     * 코스 정차지 후보 — 도시(+구)와 종류로 좁힌다. {@code idx_places_city_district}가 이미 있다.
     *
     * <p><b>왜 쿼리를 둘로 나누는가</b>: {@code (:district IS NULL OR p.district = :district)}
     * 한 방으로 쓰면 Postgres가 null 파라미터의 타입을 정하지 못해 <b>실행 시점</b>에
     * "could not determine data type of parameter"로 죽을 수 있다. 기동은 멀쩡히 되므로
     * 구를 지정한 요청만 테스트하면 영영 안 걸린다. 분기를 자바에서 하면 그 위험이 없다.
     */
    default List<Place> findCandidates(String city, String district, Collection<PlaceKind> kinds) {
        if (kinds.isEmpty()) return List.of();
        return (district == null || district.isBlank())
                ? findCandidatesInCity(city, kinds)
                : findCandidatesInDistrict(city, district, kinds);
    }

    /**
     * 좌표가 없는 행은 최근접 이웃 계산에 못 쓰므로 쿼리에서 뺀다 — 호출부에서 필터하면
     * 언젠가 빠뜨린다.
     *
     * <p>city를 대소문자 무시로 비교하는 이유: 계약이 표기를 강제하지 않고 과거 실행이
     * {@code seoul}과 {@code Seoul}을 모두 보낸 적이 있다. 한 글자 때문에 후보가 0건이 되면
     * 응답은 정상이고 결과만 조용히 Kakao로 되돌아간다.
     */
    @Query("""
            SELECT p FROM Place p
             WHERE upper(p.city) = upper(:city)
               AND p.district = :district
               AND p.placeKind IN :kinds
               AND p.lat IS NOT NULL AND p.lng IS NOT NULL
            """)
    List<Place> findCandidatesInDistrict(@Param("city") String city,
                                         @Param("district") String district,
                                         @Param("kinds") Collection<PlaceKind> kinds);

    @Query("""
            SELECT p FROM Place p
             WHERE upper(p.city) = upper(:city)
               AND p.placeKind IN :kinds
               AND p.lat IS NOT NULL AND p.lng IS NOT NULL
            """)
    List<Place> findCandidatesInCity(@Param("city") String city,
                                     @Param("kinds") Collection<PlaceKind> kinds);
}
