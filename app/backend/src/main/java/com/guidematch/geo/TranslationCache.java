package com.guidematch.geo;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "translation_cache",
    uniqueConstraints = @UniqueConstraint(columnNames = {"source_text", "target_lang"})
)
public class TranslationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "target_lang", nullable = false, length = 10)
    private String targetLang;

    @Column(name = "translated_text", nullable = false, columnDefinition = "TEXT")
    private String translatedText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TranslationCache() {}

    public TranslationCache(String sourceText, String targetLang, String translatedText) {
        this.sourceText = sourceText;
        this.targetLang = targetLang;
        this.translatedText = translatedText;
    }

    public String getSourceText()    { return sourceText; }
    public String getTargetLang()    { return targetLang; }
    public String getTranslatedText(){ return translatedText; }
}
