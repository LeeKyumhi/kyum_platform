package com.guidematch.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kakao 유래 노트를 뒤늦게 수집된 레지스트리 장소에 연결한다.
 *
 * <p><b>왜 필요한가</b>: 노트는 두 식별자 중 아는 것만 들고 저장된다. 탐색 화면에서 올린
 * 노트는 kakao id만 갖는데, 그 장소가 나중에 레지스트리에 수집되면 <b>같은 장소의 노트가
 * 두 축으로 갈라진 채 남는다</b>. 레지스트리 전용 정차지(kakao id가 없는 장소)에서 열면
 * 그 노트들이 보이지 않는다. 이 러너가 그 간극을 메운다.
 *
 * <p><b>왜 기동 시에 도나</b>: 연결이 필요해지는 시점은 <b>적재가 새 장소를 만든 뒤</b>인데,
 * 적재는 별도 프로세스(`ingest` 프로파일)라 앱이 알 방법이 없다. 기동 때 한 번 훑는 것이
 * {@link PlaceKindBackfill}과 같은 방식이고, 조회가 {@code place_id IS NULL} 기준이라 멱등이다.
 *
 * <p><b>왜 TransactionTemplate인가</b>: {@code @Transactional}을 같은 클래스의 메서드에 붙이면
 * 자기 호출이라 프록시를 우회해 아무 효과가 없다({@link PlaceKindBackfill}에서 실측).
 *
 * <p><b>왜 예외를 삼키나</b>: 이 러너가 기동을 막으면 안 된다. 실제로 Supabase 소켓이 끊겨
 * 앱이 못 뜬 적이 있다. 연결은 다음 기동에 다시 시도하면 되고, 못 해도 노트는 안 사라진다
 * (kakao id 경로로는 계속 보인다 — 잃는 것은 레지스트리 쪽 가시성뿐이다).
 *
 * <p>생성자는 하나만 둔다 — 둘이면 Spring이 {@code No default constructor found}로 죽는다.
 */
@Component
@Profile("!ingest")
public class PlaceNoteBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaceNoteBackfill.class);

    private final PlaceNoteRepository noteRepo;
    private final PlaceRepository placeRepo;
    private final TransactionTemplate tx;

    public PlaceNoteBackfill(PlaceNoteRepository noteRepo, PlaceRepository placeRepo,
                             TransactionTemplate tx) {
        this.noteRepo = noteRepo;
        this.placeRepo = placeRepo;
        this.tx = tx;
    }

    /** 기동 훅. 실제 일은 {@link #run()}이 하고, 그쪽이 예외를 이미 삼킨다. */
    @Override
    public void run(ApplicationArguments args) {
        run();
    }

    /** @return 연결된 노트 수 */
    public int run() {
        try {
            List<String> unlinked = noteRepo.findUnlinkedKakaoIds();
            if (unlinked.isEmpty()) return 0;

            // 배치 1회 — 미연결 kakao id 전부를 한 번에 해소한다. 시드니 왕복 250ms라
            // id마다 단건 조회를 돌리면 노트가 늘어날수록 기동이 길어진다.
            Map<String, Long> resolved = placeRepo.findAllByKakaoPlaceIdIn(unlinked).stream()
                    .collect(Collectors.toMap(Place::getKakaoPlaceId, Place::getId, (a, b) -> a));
            if (resolved.isEmpty()) return 0;

            List<PlaceNote> changed = new ArrayList<>();
            resolved.forEach((kakaoId, placeId) -> {
                for (PlaceNote n : noteRepo.findByKakaoPlaceIdAndPlaceIdIsNull(kakaoId)) {
                    // kakao id는 지우지 않는다 — 목록 화면은 여전히 그 키로 조회한다.
                    n.linkPlaceId(placeId);
                    changed.add(n);
                }
            });
            if (changed.isEmpty()) return 0;

            tx.executeWithoutResult(status -> noteRepo.saveAll(changed));
            log.info("장소 노트 {}건을 레지스트리에 연결했다", changed.size());
            return changed.size();
        } catch (Exception e) {
            log.warn("장소 노트 백필 실패 — 무시하고 진행: {}", e.toString());
            return 0;
        }
    }
}
