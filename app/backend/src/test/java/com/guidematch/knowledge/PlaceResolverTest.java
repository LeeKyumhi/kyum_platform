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

    /** 기본 반경 200m · 의심 구간 2km */
    private final PlaceResolver resolver = new PlaceResolver(placeRepo, aliasRepo, 200, 2000);

    // 서울숲 입구 근처
    private static final double SEOUL_FOREST_LAT = 37.5444;
    private static final double SEOUL_FOREST_LNG = 127.0374;

    /** 해결 대상이 되는 장소는 실제로는 항상 저장된 상태(id 있음)다. */
    private static Place withId(Place p, long id) {
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static final String SEONGSU_ADDRESS = "서울 성동구 아차산로9길 8";

    private Place existing(String name, Double lat, Double lng, String kakaoId) {
        Place p = new Place(name, "seoul", "성동구", lat, lng, kakaoId, null, "카페", SEONGSU_ADDRESS);
        when(placeRepo.findByNameNormalized(PlaceNames.normalize(name))).thenReturn(List.of(p));
        return p;
    }

    private Place existing(String name, Double lat, Double lng) {
        return existing(name, lat, lng, null);
    }

    /** tour_api 단서 — 외부 ID가 있어 새 노드를 만들 자격이 있다(그래서 의심 구간이 필요하다). */
    private PlaceClue tourClue(String name, Double lat, Double lng, String tourId) {
        return new PlaceClue(name, List.of(), "Seoul", "중구", lat, lng,
                null, tourId, "A02>A0201>A02010700", "서울 중구 어딘가", "tour_api", null, null);
    }

    private PlaceClue clue(String name, Double lat, Double lng, String kakaoId) {
        return clue(name, List.of(), lat, lng, kakaoId);
    }

    private PlaceClue clue(String name, List<String> aliases, Double lat, Double lng, String kakaoId) {
        return new PlaceClue(name, aliases, "seoul", "성동구", lat, lng,
                kakaoId, null, "음식점 > 카페 > 커피전문점", SEONGSU_ADDRESS, "kakao_local", null, null);
    }

    private void noNameMatches() {
        when(placeRepo.findByNameNormalized(anyString())).thenReturn(List.of());
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());
    }

    // ── 1단: 외부 ID ────────────────────────────────────────────────

    @Test
    void kakaoIdMatch_bindsToExistingPlace() {
        Place p = new Place("어니언 성수", "seoul", "성동구", 37.5445, 127.0557, "1234567", null, "카페", SEONGSU_ADDRESS);
        when(placeRepo.findByKakaoPlaceId("1234567")).thenReturn(Optional.of(p));

        PlaceResolver.Resolution r = resolver.resolve(clue("어니언(성수점)", 37.5445, 127.0557, "1234567"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
        // 외부 ID로 붙었으므로 새 장소를 만들지 않는다
        verify(placeRepo, never()).save(argThat(x -> x != p));
    }

    @Test
    void tourApiIdMatch_bindsToExistingPlace() {
        Place p = new Place("경복궁", "seoul", "종로구", 37.5796, 126.9770, null, "126508", "관광지", "서울 종로구 사직로 161");
        when(placeRepo.findByTourApiContentId("126508")).thenReturn(Optional.of(p));

        PlaceClue c = new PlaceClue("경복궁", List.of(), "seoul", "종로구", 37.5796, 126.9770,
                null, "126508", "A02>A0201>A02010100", "서울 종로구 사직로 161", "tour_api", null, null);

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
                37.5445, 127.0557, "1234567", null, "카페", SEONGSU_ADDRESS), 7L);
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
                37.5445, 127.0557, "1234567", null, "카페", SEONGSU_ADDRESS), 7L);
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
                SEOUL_FOREST_LAT, SEOUL_FOREST_LNG, null, null, "공원", null);
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("서울숲"))).thenReturn(List.of(forest));
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(false);

        // 공원 반대편 — 약 450m
        PlaceClue farSide = new PlaceClue("서울숲", List.of(), "seoul", "성동구",
                SEOUL_FOREST_LAT + 0.0040, SEOUL_FOREST_LNG, null, null, "공원", null, "kakao_local", null, null);

        assertThat(resolver.resolve(farSide).isResolved())
                .as("기본 200m에서는 같은 공원인데도 갈라진다")
                .isFalse();

        PlaceResolver wide = new PlaceResolver(placeRepo, aliasRepo, 800, 2000);
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
        Place a = new Place("스타벅스", "seoul", "성동구", 37.5445, 127.0557, "111", null, "카페", null);
        Place b = new Place("스타벅스", "seoul", "성동구", 37.5446, 127.0558, "222", null, "카페", null);
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
        Place p = new Place("어니언 성수", "seoul", "성동구", 37.5445, 127.0557, "1234567", null, "카페", SEONGSU_ADDRESS);
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

    // ── 종류·주소 (Task 2) ──────────────────────────────────────────

    /** 새 노드를 만드는 순간 종류가 정해진다. 나중에 채우면 그 사이의 조회가 전부 후보를 놓친다. */
    @Test
    void 새로_만든_장소는_종류와_주소를_갖는다() {
        noNameMatches();
        when(placeRepo.findByKakaoPlaceId("k1")).thenReturn(Optional.empty());
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        PlaceResolver.Resolution r = resolver.resolve(new PlaceClue(
                "어니언 성수", List.of(), "seoul", "성동구", 37.5444, 127.0374,
                "k1", null, "음식점 > 카페 > 커피전문점", SEONGSU_ADDRESS, "kakao_local", null, null));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place().getPlaceKind()).isEqualTo(PlaceKind.CAFE);
        assertThat(r.place().getAddressKo()).isEqualTo(SEONGSU_ADDRESS);
    }

    /** 주소는 빈 칸일 때만 채운다 — 나중 소스가 먼저 들어온 권위 있는 값을 흔들면 안 된다. */
    @Test
    void 이미_주소가_있으면_덮어쓰지_않는다() {
        Place p = withId(existing("어니언 성수", 37.5444, 127.0374, "k1"), 7L);
        when(placeRepo.findByKakaoPlaceId("k1")).thenReturn(Optional.of(p));

        resolver.resolve(new PlaceClue("어니언 성수", List.of(), "seoul", "성동구",
                37.5444, 127.0374, "k1", null, "카페", "다른 주소", "tour_api", null, null));

        assertThat(p.getAddressKo()).isEqualTo(SEONGSU_ADDRESS);
    }

    /** 주소가 비어 있던 기존 행은 새 단서로 채워진다 — 재적재만으로 주소가 붙는 근거. */
    @Test
    void 주소가_비어있으면_새_단서로_채운다() {
        Place p = withId(new Place("어니언 성수", "seoul", "성동구", 37.5444, 127.0374,
                "k1", null, "음식점 > 카페", null), 7L);
        when(placeRepo.findByKakaoPlaceId("k1")).thenReturn(Optional.of(p));

        resolver.resolve(new PlaceClue("어니언 성수", List.of(), "seoul", "성동구",
                37.5444, 127.0374, "k1", null, "음식점 > 카페", SEONGSU_ADDRESS, "kakao_local", null, null));

        assertThat(p.getAddressKo()).isEqualTo(SEONGSU_ADDRESS);
    }

    // ── 의심 구간 (Task 9) ──────────────────────────────────────────

    /**
     * ★ 이름은 같은데 500m 떨어져 있다 = 경복궁형.
     *
     * <p>여기서 새 노드를 만들면 {@code name_normalized}가 같은 행이 둘이 되고, 그 뒤 이 장소에
     * 오는 모든 단서는 후보 2건을 물어와 <b>영영 ambiguous 거절</b>이거나 <b>오병합</b>이 된다.
     * 역방향 시딩은 정확 이름 일치를 최대화하는 기법이라 이 경로를 상시로 밟는다.
     */
    @Test
    void 이름이_같고_반경_밖_의심구간이면_새_노드를_만들지_않는다() {
        Place p = withId(existing("경복궁", 37.5796, 126.9770), 5L);
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());

        // 약 500m 북쪽
        PlaceResolver.Resolution r = resolver.resolve(tourClue("경복궁", 37.5841, 126.9770, "t9"));

        assertThat(r.isResolved()).isFalse();
        assertThat(r.unresolvedReason()).contains("suspect");
        verify(placeRepo, never()).save(any(Place.class));
        assertThat(p.getTourApiContentId()).as("병합도 하지 않았다").isNull();
    }

    /** 200m 이내는 지금까지처럼 병합한다 — 사다리의 기존 의미가 그대로여야 한다. */
    @Test
    void 이름이_같고_반경_이내면_병합한다() {
        Place p = withId(existing("남대문시장", 37.5595, 126.9773), 6L);
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());

        PlaceResolver.Resolution r = resolver.resolve(tourClue("남대문시장", 37.5596, 126.9774, "t10"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
        assertThat(p.getTourApiContentId()).isEqualTo("t10");
    }

    /** 3km 떨어진 동명 장소는 진짜로 별개다 — 이걸 보관함에 보내면 정상 시딩이 전부 샌다. */
    @Test
    void 이름이_같아도_의심구간_밖이면_새_노드다() {
        withId(existing("스타벅스", 37.5000, 127.0000), 9L);
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(false);
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 200L));

        // 약 4.4km — 의심 구간(2km) 밖
        PlaceResolver.Resolution r = resolver.resolve(tourClue("스타벅스", 37.5400, 127.0000, "t11"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place().getId()).isEqualTo(200L);
    }

    // ── 2차 조회 (Task 9) ───────────────────────────────────────────

    /** 괄호절 때문에 안 붙던 것을 붙인다. 저장 컬럼에는 언제나 정확 키가 들어간다. */
    @Test
    void 괄호절을_제거한_키로_2차_조회를_한다() {
        Place p = withId(existing("간송미술관", 37.5921, 126.9990), 8L);
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("간송미술관(서울 보화각)")))
                .thenReturn(List.of());
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());

        PlaceResolver.Resolution r = resolver.resolve(
                tourClue("간송미술관(서울 보화각)", 37.5921, 126.9990, "t12"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
    }

    /**
     * ★ 완화 키가 서로 다른 지점을 합치지 않는다.
     *
     * <p>{@code 스타벅스(명동점)}과 {@code 스타벅스(을지로점)}은 괄호를 떼면 둘 다
     * {@code 스타벅스}가 된다. 그런데도 안전한 이유는 <b>완화 키를 저장하지 않기 때문</b>이다 —
     * 저장된 이름은 언제나 정확 키({@code 스타벅스을지로점})라서 완화 조회에 걸리지 않는다.
     * 만약 {@code normalize()} 자체를 고쳐 괄호절을 접었다면 저장 키까지 같아져
     * 안전한 ambiguous 거절 대신 <b>오병합</b>이 났을 것이다.
     */
    @Test
    void 지점명이_다른_체인은_2차_조회로도_서로를_물어오지_않는다() {
        // 을지로점만 저장돼 있다. 명동점 단서가 150m 거리로 들어온다.
        Place euljiro = withId(new Place("스타벅스(을지로점)", "seoul", "중구",
                37.5660, 126.9820, "kEul", null, "음식점 > 카페", null), 11L);
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("스타벅스(을지로점)")))
                .thenReturn(List.of(euljiro));
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("스타벅스(명동점)"))).thenReturn(List.of());
        when(placeRepo.findByNameNormalized(PlaceNames.normalize("스타벅스"))).thenReturn(List.of());
        when(aliasRepo.findByAliasNormalized(anyString())).thenReturn(List.of());
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(false);
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 300L));

        PlaceResolver.Resolution r = resolver.resolve(tourClue("스타벅스(명동점)", 37.5673, 126.9820, "tMd"));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place().getId())
                .as("을지로점(11L)으로 잘못 병합되지 않고 새 노드가 된다")
                .isEqualTo(300L);
        assertThat(euljiro.getTourApiContentId()).isNull();
    }

    // ── 레지스트리 스냅샷 (Task 10) ─────────────────────────────────

    /** 스냅샷이 있으면 읽기 왕복이 0이다 — 이게 적재 시간을 줄이는 지점이다. */
    @Test
    void 스냅샷이_있으면_조회_왕복이_없다() {
        Place known = withId(new Place("덕수궁", "Seoul", "중구", 37.5656, 126.9749,
                "k1", null, "여행 > 관광,명소", "서울 중구"), 1L);
        RegistrySnapshot snap = RegistrySnapshot.of(List.of(known), List.of(), java.util.Set.of());

        PlaceResolver.Resolution r = resolver.resolve(
                new PlaceClue("덕수궁", List.of(), "Seoul", "중구", 37.5656, 126.9749,
                        "k1", null, "여행 > 관광,명소", "서울 중구", "kakao_local", null, null), snap);

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(known);
        verifyNoInteractions(placeRepo);
    }

    /**
     * ★ 스냅샷의 미스는 곧 DB의 미스여야 한다 — 그래야 폴백 없이 사다리 의미가 보존된다.
     *
     * <p>매니페스트 범위(중구)와 저장된 district(성북구)가 달라도 외부 ID로 찾아진다.
     * <b>범위 단위 스냅샷이었다면</b> 여기서 미스 → 새 노드 생성 →
     * {@code uk_places_tour_api} 위반이 나고, 어제까지 멱등이던 재적재가 갑자기 실패했을 것이다.
     * 실측 사례: 간송미술관은 주소가 성북구인데 {@code district='중구'}로 저장돼 있다.
     */
    @Test
    void 저장된_구가_매니페스트_범위와_달라도_외부ID로_찾는다() {
        Place known = withId(new Place("간송미술관", "Seoul", "성북구", 37.5921, 126.9990,
                null, "130511", "A02>A0206>A02060500", null), 9L);
        RegistrySnapshot snap = RegistrySnapshot.of(List.of(known), List.of(), java.util.Set.of());

        PlaceResolver.Resolution r = resolver.resolve(
                new PlaceClue("간송미술관", List.of(), "Seoul", "중구", 37.5921, 126.9990,
                        null, "130511", "A02>A0206>A02060500", null, "tour_api", null, null), snap);

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(known);
        verifyNoInteractions(placeRepo);   // 새 노드를 만들지 않았다
    }

    /** ★ 같은 파일 안의 두 번째 등장이 새 노드가 되면 안 된다. */
    @Test
    void 같은_실행에서_만든_장소가_뒷줄에_보인다() {
        RegistrySnapshot snap = RegistrySnapshot.of(List.of(), List.of(), java.util.Set.of());
        when(aliasRepo.existsByPlaceIdAndAliasNormalized(any(), anyString())).thenReturn(false);
        when(placeRepo.save(any(Place.class))).thenAnswer(inv -> withId(inv.getArgument(0), 42L));

        PlaceClue first = new PlaceClue("남산골한옥마을", List.of(), "Seoul", "중구",
                37.5594, 126.9940, "k7", null, "여행 > 관광,명소", "서울 중구", "kakao_local", null, null);
        PlaceResolver.Resolution a = resolver.resolve(first, snap);
        PlaceResolver.Resolution b = resolver.resolve(first, snap);

        assertThat(a.place()).isSameAs(b.place());
        verify(placeRepo, times(1)).save(any(Place.class));  // 두 번째는 새로 만들지 않는다
    }

    /** 사다리 의미가 스냅샷 유무로 달라지면 안 된다 — ambiguous는 여전히 거절이다. */
    @Test
    void 스냅샷에서도_ambiguous는_거절한다() {
        Place a = withId(new Place("스타벅스", "Seoul", "중구", 37.5636, 126.9827,
                "k1", null, "음식점 > 카페", null), 1L);
        Place b = withId(new Place("스타벅스", "Seoul", "중구", 37.5637, 126.9828,
                "k2", null, "음식점 > 카페", null), 2L);
        RegistrySnapshot snap = RegistrySnapshot.of(List.of(a, b), List.of(), java.util.Set.of());

        PlaceResolver.Resolution r = resolver.resolve(
                new PlaceClue("스타벅스", List.of(), "Seoul", "중구", 37.5636, 126.9827,
                        null, "t1", "A05>A0502>A05020900", null, "tour_api", null, null), snap);

        assertThat(r.isResolved()).isFalse();
        assertThat(r.unresolvedReason()).contains("ambiguous");
    }

    /** 별칭도 스냅샷의 이름 인덱스에 합쳐져 있어야 한다 — DB 경로와 같은 결과여야 한다. */
    @Test
    void 스냅샷도_별칭으로_도달한다() {
        Place p = withId(new Place("어니언 성수", "seoul", "성동구", 37.5445, 127.0557,
                "1234567", null, "음식점 > 카페", null), 7L);
        RegistrySnapshot snap = RegistrySnapshot.of(
                List.of(p), List.of(new PlaceAlias(7L, "Onion Seongsu", "blog")), java.util.Set.of());

        PlaceResolver.Resolution r = resolver.resolve(
                new PlaceClue("Onion Seongsu", List.of(), "seoul", "성동구", 37.5445, 127.0557,
                        null, "t2", "음식점 > 카페", null, "tour_api", null, null), snap);

        assertThat(r.isResolved()).isTrue();
        assertThat(r.place()).isSameAs(p);
    }
}
