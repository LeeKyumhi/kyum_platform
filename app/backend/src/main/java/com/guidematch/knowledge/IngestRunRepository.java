package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestRunRepository extends JpaRepository<IngestRun, Long> {

    Optional<IngestRun> findByRunId(String runId);
}
