package com.guidematch.guide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 가이드가 구사하는 언어 하나를 표현. (예: 영어 - FLUENT)
 * 한 가이드가 여러 언어를 가질 수 있으므로 별도 테이블(guide_languages)로 둔다.
 */
@Entity
@Table(name = "guide_languages")
public class GuideLanguage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 언어 이름 (예: "English", "Japanese") */
    @Column(nullable = false)
    private String language;

    /**
     * 구사 수준. @Enumerated(STRING) = enum 값을 숫자가 아닌 글자("FLUENT")로 DB에 저장.
     * (숫자로 저장하면 나중에 enum 순서가 바뀔 때 데이터가 깨지므로 STRING이 안전)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LanguageLevel level;

    protected GuideLanguage() {
    }

    public GuideLanguage(String language, LanguageLevel level) {
        this.language = language;
        this.level = level;
    }

    public Long getId() {
        return id;
    }

    public String getLanguage() {
        return language;
    }

    public LanguageLevel getLevel() {
        return level;
    }
}
