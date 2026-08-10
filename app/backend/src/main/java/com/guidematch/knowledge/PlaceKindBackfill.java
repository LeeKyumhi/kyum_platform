package com.guidematch.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code place_kind}가 비어 있는 기존 행을 적재와 <b>같은 규칙</b>으로 채운다.
 *
 * <p><b>왜 SQL이 아닌가</b>: 매핑 규칙이 두 곳에 생기면 반드시 어긋나고, 적재 경로와 백필이
 * 다르게 분류하는 순간 어느 쪽이 틀렸는지 알아낼 방법이 없다. {@link PlaceKinds}만 부른다.
 *
 * <p><b>왜 {@code @Profile("!ingest")}인가</b>: 적재 배치가 앱 마이그레이션을 돌리는 건 그
 * 자체로 틀렸고, 적재 롤에는 권한도 없다({@code docs/ingest/db-role.sql}).
 * {@code UserFollowBackfill}이 이 규칙 없이 매 적재마다 돌던 사고가 있었다.
 *
 * <p><b>왜 안 지우는가</b>: 매 기동 도는 러너지만 평시 비용은 조회 1회(0건)다. 다음 수집이
 * 새 장소를 넣을 때 종류는 적재가 채우므로 이 러너가 할 일은 원래 0건이어야 정상이고,
 * 0건이 아니면 그게 곧 <b>적재 경로에 구멍이 났다는 신호</b>다.
 *
 * <p><b>이게 안 돌면 어떻게 되나</b>: 후보 조회가 {@code place_kind IN (...)}으로 거르므로
 * 후보 0건 → Kakao 폴백이 100% 채움 → 엔드포인트는 정상 응답하고 결과가 예전과 완전히
 * 동일하다. <b>조용한 실패다.</b> 그래서 완료 판정이 "정차지가 있다"가 아니라
 * {@code source="registry"} 정차지 ≥ 1이다.
 */
@Component
@Profile("!ingest")
public class PlaceKindBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaceKindBackfill.class);

    private final PlaceRepository placeRepo;

    /**
     * <b>{@code @Transactional} 애너테이션 대신 이것을 쓰는 이유</b>: 트랜잭션 경계를 이 클래스
     * 안의 다른 메서드에 걸면 {@code run()}에서 부를 때 프록시를 우회해(자기 호출) 아무 효과가
     * 없다. 조용히 트랜잭션 없이 도는 것이 정확히 아래 주석이 막으려는 상황이라, 애너테이션의
     * 함정을 피해 명시적으로 연다.
     */
    private final TransactionTemplate txTemplate;

    /**
     * 생성자는 <b>하나만</b> 둔다. 편의용 두 번째 생성자를 만들었더니 Spring이 어느 쪽을 쓸지
     * 못 정해 {@code No default constructor found}로 기동이 실패했다.
     * ({@code TransactionTemplate}은 Spring Boot가 자동 구성해주는 빈이다.)
     */
    public PlaceKindBackfill(PlaceRepository placeRepo, TransactionTemplate txTemplate) {
        this.placeRepo = placeRepo;
        this.txTemplate = txTemplate;
    }

    /**
     * <b>기동을 막지 않는다.</b> 백필 실패로 프로덕션 기동이 통째로 죽으면 안 된다
     * (실제로 적재 중 Supabase 소켓이 끊겨 앱이 못 뜬 적이 있다). 대신 ERROR로 크게 남기고
     * 다음 기동에서 다시 시도한다 — 조회가 {@code IS NULL} 기준이라 멱등이다.
     *
     * <p>실패를 놓치지 않는 진짜 안전망은 로그가 아니라 코스 추천 응답의
     * {@code source="registry"} 정차지 수다.
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            txTemplate.executeWithoutResult(status -> backfill());
        } catch (Exception e) {
            log.error("place_kind 백필 실패 — 레지스트리 정차지가 동작하지 않는다"
                    + " (코스 추천이 Kakao 폴백으로만 채워져 겉보기엔 정상이다)."
                    + " 다음 기동에서 재시도한다: {}", e.toString(), e);
        }
    }

    /**
     * <b>트랜잭션 안에서 도는 것이 핵심이다.</b> 밖에서 돌면 조회 결과가 detached라
     * {@code saveAll}이 엔티티마다 {@code merge} → <b>행마다 SELECT 한 번</b>을 낸다.
     * Supabase가 Sydney(왕복 250ms)라 53행이면 그것만으로 13초고, 실제로 그 도중
     * 소켓이 끊겨({@code Can't assign requested address}) 롤백된 적이 있다.
     * 트랜잭션 안에서는 엔티티가 managed라 더티 체킹만으로 UPDATE가 나가고 추가 SELECT가 0이다.
     */
    private void backfill() {
        List<Place> pending = placeRepo.findByPlaceKindIsNull();
        if (pending.isEmpty()) return;

        List<Place> changed = pending.stream().filter(Place::assignKindIfMissing).toList();
        if (changed.isEmpty()) return;

        placeRepo.saveAll(changed);
        log.info("place_kind 백필 {}건 — {}", changed.size(),
                changed.stream().collect(Collectors.groupingBy(
                        Place::getPlaceKind, Collectors.counting())));
    }
}
