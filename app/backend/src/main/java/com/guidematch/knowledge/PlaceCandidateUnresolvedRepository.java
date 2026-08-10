package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceCandidateUnresolvedRepository
        extends JpaRepository<PlaceCandidateUnresolved, Long> {

    List<PlaceCandidateUnresolved> findByRunId(String runId);

    boolean existsByRecordId(String recordId);
}
