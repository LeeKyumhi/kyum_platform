package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 해결 사다리 테스트.
 *
 * <p>여기가 이 기능 전체에서 가장 중요한 로직이다. 잘못된 병합은 되돌릴 수 없고
 * (두 장소의 인사이트가 섞이면 어느 쪽이 원래 것인지 알 방법이 없다) 실패가 조용하다.
 * 그래서 "애매하면 미해결"이 지켜지는지를 경계에서 직접 찌른다.
 */
class PlaceResolverTest {

    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final PlaceAliasRepository aliasRepo = mock(PlaceAliasRepository.class);

    /** 기본 반경 200m */
    private final PlaceResolver resolver = new PlaceResolver(placeRepo, aliasRepo, 200);

    // 서울숲 입구 근처
    private static final double SEOUL_FOREST_LAT = 37.5444;
    private static final double SEOUL_FOREST_LNG = 127.0374;

    /** 해결 대상이 되는 장소는 실제로는 항상 저장된 상태(id 있음)다. */
    private static Place withId(Place p, long id) {
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private Place existing(String name, Double lat, Double lng, String kakaoId) {
        Place p = new Place(name, "seoul", "성동구", lat, lng, kakaoId, null, "카페");
        when(placeRepo.findByNameNormalized(PlaceNames.normalize(name))).thenReturn(List.of(p));
        return p;
    }

    private PlaceClue clue(String name, Double lat, Double lng, String kakaoId) {
        return clue(name, List.of(), lat, lng, kakaoId);
    }

    private PlaceClue clue(String name, List<String> aliases, Double lat, Double lng, String kakaoId) {
        return new PlaceClue(name, aliases, "seoul", "성동구", lat, lng,
                kakaoId, null, "카페", "kakao_local");
    }

    private void noNameMatches() {
        when(placeRepo.findByNameNormalized(anyString())).thenReturn(List.of());
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());
    }

    // ── 1단: 외부 ID ────────────────────────────────────────────────

    @Test
    void kakaoIdMatch_bindsToExistingPlace() {
        Place p = new Place("어니언 성수", "seoul", "성동구", 37.5445, 127.0557, "1234567", null, "카페");
        when(placeRepo.findByKakaoPlaceId("1234567")).thenReturn(Optional.of(p));

        PlaceResolver.Resolution r = resolver.resolve(clue("어니언(성수점)", 37.5445, 127.0557, "1234567"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
        // 외부 ID로 붙었으므로 새 장소를 만들지 않는다
        verify(placeRepo, never()).save(argThat(x -> x != p));
    }

    @Test
    void tourApiIdMatch_bindsToExistingPlace() {
        Place p = new Place("경복궁", "seoul", "종로구", 37.5796, 126.9770, null, "126508", "관광지");
        when(placeRepo.findByTourApiContentId("126508")).thenReturn(Optional.of(p));

        PlaceClue c = new PlaceClue("경복궁", List.of(), "seoul", "종로구", 37.5796, 126.9770,
                null, "126508", "관광지", "tour_api");

        PlaceResolver.Resolution r = resolver.resolve(c);

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
    }

    // ── 2단: 이름 + 반경 ────────────────────────────────────────────

    @Test
    void nameMatchWithinRadius_binds() {
        Place p = existing("어니언 성수", 37.5445, 127.0557, "1234567");
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());

        // 약 90m 떨어진 지점 — 반경 200m 안
        PlaceResolver.Resolution r = resolver.resolve(clue("어니언 성수", 37.5453, 127.0557, null));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
    }

    /**
     * 별칭은 추출기가 실은 {@code aliases} 배열에서 온다. {@code nameRaw}는 정규화하면
     * 이미 이 장소의 이름과 같으므로(2단이 그걸로 매칭했다) 새로 배울 게 없다.
     */
    @Test
    void resolving_recordsUnseenAliases_soNextRunMatchesFaster() {
        Place p = withId(new Place("어니언 성수", "seoul", "성동구",
                37.5445, 127.0557, "1234567", null, "카페"), 7L);
        when(placeRepo.findByKakaoPlaceId("1234567")).thenReturn(Optional.of(p));
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(false);

        resolver.resolve(clue("어니언 성수", List.of("Onion Seongsu", "어니언(성수점)"),
                37.5445, 127.0557, "1234567"));

        ArgumentCaptor<PlaceAlias> saved = ArgumentCaptor.forClass(PlaceAlias.class);
        verify(aliasRepo, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(PlaceAlias::getAliasRaw)
                .containsExactlyInAnyOrder("Onion Seongsu", "어니언(성수점)");
    }

    @Test
    void aliasIdenticalToPlaceName_isNotRecorded() {
        Place p = withId(new Place("어니언 성수", "seoul", "성동구",
                37.5445, 127.0557, "1234567", null, "카페"), 7L);
        when(placeRepo.findByKakaoPlaceId("1234567")).thenReturn(Optional.of(p));

        // 표기만 다르고 정규화하면 같은 값 → 배울 게 없으므로 저장하지 않는다
        resolver.resolve(clue("어니언 성수", List.of("어니언(성수)"), 37.5445, 127.0557, "1234567"));

        verify(aliasRepo, never()).save(any(PlaceAlias.class));
    }

    @Test
    void nameMatchJustOutsideRadius_isUnresolved() {
        existing("어니언 성수", 37.5445, 127.0557, "1234567");
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());

        // 약 250m — 기본 반경 200m 바로 바깥. 경계를 실제로 찌른다
        PlaceResolver.Resolution r = resolver.resolve(clue("어니언 성수", 37.5467, 127.0557, null));

        assertThat(r.isResolved()).isFalse();
        assertThat(r.unresolvedReason()).contains("radius");
    }

    /**
     * 대형 장소 — 이 실패 모드가 실제로 존재함을 고정한다.
     * 서울숲·경복궁은 한 장소가 400m 넘게 퍼져 있어, 입구와 반대편이 같은 장소인데도
     * 기본 반경에서는 갈라진다. 그래서 반경이 상수가 아니라 설정값이어야 한다.
     */
    @Test
    void largeVenue_splitsAtDefaultRadius_butBindsWhenRadiusRaised() {
        Place forest = new Place("서울숲", "seoul", "성동구",
                SEOUL_FOREST_LAT, SEOUL_FOREST_LNG, null, null, "공원");
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("서울숲"))).thenReturn(List.of(forest));
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(false);

        // 공원 반대편 — 약 450m
        PlaceClue farSide = new PlaceClue("서울숲", List.of(), "seoul", "성동구",
                SEOUL_FOREST_LAT + 0.0040, SEOUL_FOREST_LNG, null, null, "공원", "kakao_local");

        assertThat(resolver.resolve(farSide).isResolved())
                .as("기본 200m에서는 같은 공원인데도 갈라진다")
                .isFalse();

        PlaceResolver wide = new PlaceResolver(placeRepo, aliasRepo, 800);
        assertThat(wide.resolve(farSide).isResolved())
                .as("반경을 넓히면 결합된다 — 그래서 설정값이어야 한다")
                .isTrue();
    }

    @Test
    void differentName_nearbyCoords_isUnresolved() {
        // 좌표는 붙어 있지만 이름이 다르다 — 같은 건물의 다른 가게일 수 있다
        noNameMatches();

        PlaceResolver.Resolution r = resolver.resolve(clue("대림창고", 37.5445, 127.0557, null));

        assertThat(r.isResolved()).isFalse();
    }

    @Test
    void multipleCandidatesWithinRadius_isUnresolved_neverGuesses() {
        Place a = new Place("스타벅스", "seoul", "성동구", 37.5445, 127.0557, "111", null, "카페");
        Place b = new Place("스타벅스", "seoul", "성동구", 37.5446, 127.0558, "222", null, "카페");
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("스타벅스"))).thenReturn(List.of(a, b));
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());

        PlaceResolver.Resolution r = resolver.resolve(clue("스타벅스", 37.5445, 127.0557, null));

        assertThat(r.isResolved()).isFalse();
        assertThat(r.unresolvedReason()).contains("ambiguous");
    }

    // ── 3단: 미해결 · 신규 생성 정책 ─────────────────────────────────

    @Test
    void externalId_noMatch_createsNewPlace() {
        when(placeRepo.findByKakaoPlaceId("999")).thenReturn(Optional.empty());
        noNameMatches();
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> inv.getArgument(0));

        PlaceResolver.Resolution r = resolver.resolve(clue("새로운 카페", 37.5445, 127.0557, "999"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place().getKakaoPlaceId()).isEqualTo("999");
        verify(placeRepo).save(any(Place.class));
    }

    /**
     * 실DB 스모크에서 잡힌 버그의 회귀 테스트 — 신규 생성 경로가 별칭을 버리고 있었다.
     * 장소를 처음 보는 순간이 바로 권위 있는 소스가 준 표기 변형을 배우는 순간이라,
     * 여기서 놓치면 그 별칭은 다시 안 온다.
     */
    @Test
    void creatingNewPlace_alsoRecordsItsAliases() {
        when(placeRepo.findByKakaoPlaceId("999")).thenReturn(Optional.empty());
        noNameMatches();
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(false);
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 55L));

        resolver.resolve(clue("어니언 성수", List.of("Onion Seongsu"), 37.5445, 127.0557, "999"));

        ArgumentCaptor<PlaceAlias> saved = ArgumentCaptor.forClass(PlaceAlias.class);
        verify(aliasRepo).save(saved.capture());
        assertThat(saved.getValue().getAliasRaw()).isEqualTo("Onion Seongsu");
        assertThat(saved.getValue().getPlaceId()).isEqualTo(55L);
    }

    /**
     * 권위 없는 소스(블로그: 이름만 있고 외부 ID 없음)는 <b>노드를 만들 수 없다</b>.
     * 만들게 두면 같은 장소가 표기마다 새 노드로 쪼개져 자산이 복리가 안 된다.
     */
    @Test
    void noExternalId_noMatch_doesNotCreatePlace() {
        noNameMatches();

        PlaceResolver.Resolution r = resolver.resolve(clue("어디선가 본 카페", 37.5445, 127.0557, null));

        assertThat(r.isResolved()).isFalse();
        verify(placeRepo, never()).save(any(Place.class));
    }

    @Test
    void noCoordinates_cannotVerify_isUnresolved() {
        existing("어니언 성수", 37.5445, 127.0557, "1234567");
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());

        // 좌표가 없으면 이름이 같아도 검증할 수단이 없다
        PlaceResolver.Resolution r = resolver.resolve(clue("어니언 성수", null, null, null));

        assertThat(r.isResolved()).isFalse();
        assertThat(r.unresolvedReason()).contains("coordinates");
    }

    @Test
    void aliasMatch_bindsToPlaceBehindTheAlias() {
        Place p = new Place("어니언 성수", "seoul", "성동구", 37.5445, 127.0557, "1234567", null, "카페");
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("Onion Seongsu"))).thenReturn(List.of());
        when(aliasRepo.findByAliasNormalized(PlaceNames.normalize("Onion Seongsu")))
                .thenReturn(List.of(new PlaceAlias(7L, "Onion Seongsu", "blog")));
        when(placeRepo.findAllById(List.of(7L))).thenReturn(List.of(p));
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(true);

        PlaceResolver.Resolution r = resolver.resolve(clue("Onion Seongsu", 37.5445, 127.0557, null));

        assertThat(r.isResolved())
                .as("한 번 쌓인 별칭은 다음 실행부터 곧바로 매칭에 쓰인다")
                .isTrue();
        assertThat(r.place()).isSameAs(p);
    }
}
