package com.guidematch.geo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TranslationCacheRepository extends JpaRepository<TranslationCache, Long> {

    Optional<TranslationCache> findBySourceTextAndTargetLang(String sourceText, String targetLang);

    List<TranslationCache> findBySourceTextInAndTargetLang(List<String> sourceTexts, String targetLang);
}
