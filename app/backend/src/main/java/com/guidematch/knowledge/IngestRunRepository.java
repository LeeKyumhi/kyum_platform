package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestRunRepository extends JpaRepository<IngestRun, Long> {

    Optional<IngestRun> findByRunId(String runId);

    /**
     * 중단 감지 — 죽은 런은 {@code STARTED}로 영원히 남는다.
     *
     * <p>마커 파일도 exit code도 쓸 수 없다: codex 샌드박스는 셸째로 프로세스를 없애므로
     * 둘 다 안 남는다. DB에 남은 이 상태가 teardown을 견디는 <b>유일한 신호</b>다.
     */
    java.util.List<IngestRun> findByStatus(IngestRun.Status status);
}
