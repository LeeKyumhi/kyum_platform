package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlaceNoteRepository extends JpaRepository<PlaceNote, Long> {

    /**
     * <b>두 키를 합쳐서</b> 가져온다 — 이 프로젝트의 핵심 조회.
     *
     * <p>목록 화면은 kakao id만 쥐고 있다. 그런데 같은 장소에 레지스트리 경로로 올린 노트는
     * {@code place_id}에만 붙어 있다. 그래서 호출부가 <b>미리 kakao id → place id를 풀어</b>
     * 두 집합을 함께 넘긴다({@link PlaceMediaLookup}). 두 키 중 하나만 보면 노트가 갈라져
     * 보이고, 화면에는 "사진이 좀 적네"로만 나타나 결함인지 알 수 없다.
     *
     * <p>place id를 호출부가 푸는 이유: 조인 한 방으로 긁으면 <b>어느 노트가 어느 요청 id에
     * 속하는지 되돌릴 수 없다</b>(노트는 kakao id를 안 들고 있을 수 있다). 귀속을 추측으로
     * 메우면 목록에서 노트가 조용히 사라진다. {@code PlaceInsightLookup}이 쓰는 것과 같은 2회 패턴이다.
     *
     * <p><b>쿼리를 둘로 쪼갠 이유</b>: {@code (:param is null or ...)} 한 방으로 합치면
     * Postgres가 null 파라미터의 타입을 정하지 못해 <b>실행 시점</b>에 죽을 수 있다.
     * 기동은 멀쩡하므로 한쪽 경로만 테스트하면 안 걸린다 — {@code PlaceRepository.findCandidates}를
     * 쪼갠 것과 같은 이유다. 호출부가 빈 컬렉션이면 아예 부르지 않는다.
     */
    @Query("select n from PlaceNote n where n.status = 'VISIBLE' "
         + "and n.kakaoPlaceId in :kakaoIds order by n.createdAt desc")
    List<PlaceNote> findVisibleByKakaoIdIn(@Param("kakaoIds") Collection<String> kakaoIds);

    @Query("select n from PlaceNote n where n.status = 'VISIBLE' "
         + "and n.placeId in :placeIds order by n.createdAt desc")
    List<PlaceNote> findVisibleByPlaceIdIn(@Param("placeIds") Collection<Long> placeIds);

    /**
     * 도배 상한을 세는 쿼리 — 두 갈래로 나눠 두고 <b>서비스가 둘 다 불러 합친다.</b>
     *
     * <p>한 방으로 합치지 않는 이유는 위와 같다: {@code (:param is not null and ...)}는
     * Postgres가 null 파라미터의 타입을 못 정해 실행 시점에 죽을 수 있다.
     *
     * <p>같은 노트가 양쪽에 다 걸릴 수 있으므로 <b>count가 아니라 id를 돌려준다</b> —
     * 서비스가 합집합 크기를 센다. count를 더하면 두 키가 다 채워진 노트가 두 번 세어져
     * 상한이 실제보다 빨리 걸린다.
     */
    @Query("select n.id from PlaceNote n where n.status = 'VISIBLE' "
         + "and n.userId = :userId and n.placeId = :placeId")
    List<Long> idsByUserAndPlaceId(@Param("userId") Long userId, @Param("placeId") Long placeId);

    @Query("select n.id from PlaceNote n where n.status = 'VISIBLE' "
         + "and n.userId = :userId and n.kakaoPlaceId = :kakaoPlaceId")
    List<Long> idsByUserAndKakaoPlaceId(@Param("userId") Long userId,
                                        @Param("kakaoPlaceId") String kakaoPlaceId);

    /** 백필 대상 — kakao id만 있고 아직 레지스트리에 연결되지 않은 노트들의 kakao id. */
    @Query("select distinct n.kakaoPlaceId from PlaceNote n where n.placeId is null and n.kakaoPlaceId is not null")
    List<String> findUnlinkedKakaoIds();

    List<PlaceNote> findByKakaoPlaceIdAndPlaceIdIsNull(String kakaoPlaceId);
}
