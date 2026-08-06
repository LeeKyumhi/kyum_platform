package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestSourceRepository extends JpaRepository<IngestSource, Long> {

    Optional<IngestSource> findByUrlHash(String urlHash);
}
