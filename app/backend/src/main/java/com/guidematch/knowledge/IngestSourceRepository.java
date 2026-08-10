package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IngestSourceRepository extends JpaRepository<IngestSource, Long> {

    Optional<IngestSource> findByUrlHash(String urlHash);

    /**
     * 이번 실행에서 다시 본 URL들의 커서를 <b>한 번에</b> 갱신한다.
     *
     * <p>URL마다 조회+수정을 하면 Sydney 왕복 250ms × 2가 행마다 붙는다(그게 58초의 절반이었다).
     * 그렇다고 갱신을 아예 건너뛰면 {@code last_seen_at}이 멈추고,
     * {@code scope-progress.jsonl}의 갱신 주기 판정이 그 값을 보므로 <b>같은 범위가 계속
     * 다시 뽑히거나 영원히 안 뽑히는</b> 조용한 고장이 난다. 그래서 왕복 한 번으로 모아서 친다.
     */
    @Modifying
    @Query("""
            UPDATE IngestSource s
               SET s.lastSeenRun = :runId, s.lastSeenAt = :now
             WHERE s.urlHash IN :hashes
            """)
    int markSeen(@Param("hashes") java.util.Collection<String> hashes,
                 @Param("runId") String runId,
                 @Param("now") java.time.Instant now);
}
