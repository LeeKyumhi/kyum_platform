package com.guidematch.geo;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 번역 캐시-우선 서비스.
 * 1) DB 캐시 일괄 조회 → 미스만 수집
 * 2) Google API 배치 호출 (미스 분만)
 * 3) 결과 DB 저장 → 원래 순서로 병합 반환
 *
 * Google 키 없음 / API 오류 → 한국어 원문 그대로 반환, 예외 없음.
 */
@Service
public class TranslationService {

    private final GoogleTranslateClient googleClient;
    private final TranslationCacheRepository cacheRepo;

    public TranslationService(GoogleTranslateClient googleClient, TranslationCacheRepository cacheRepo) {
        this.googleClient = googleClient;
        this.cacheRepo = cacheRepo;
    }

    /**
     * 텍스트 목록을 targetLang으로 번역. 빈 문자열/null은 그대로 유지.
     * targetLang이 null이면(= ko) 원문 반환.
     */
    public List<String> translate(List<String> texts, String targetLang) {
        if (targetLang == null || texts == null || texts.isEmpty()) return texts;

        // 빈 텍스트 걸러내고 번역 대상만 추림
        List<String> unique = texts.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());

        // 1) 캐시 히트
        Map<String, String> cached = cacheRepo.findBySourceTextInAndTargetLang(unique, targetLang)
                .stream()
                .collect(Collectors.toMap(TranslationCache::getSourceText, TranslationCache::getTranslatedText));

        // 2) 미스 목록
        List<String> misses = unique.stream().filter(s -> !cached.containsKey(s)).collect(Collectors.toList());

        // 3) Google API 배치 호출
        if (!misses.isEmpty() && googleClient.isEnabled()) {
            List<String> translated = googleClient.translate(misses, targetLang);
            if (translated.size() == misses.size()) {
                List<TranslationCache> toSave = new ArrayList<>();
                for (int i = 0; i < misses.size(); i++) {
                    String src = misses.get(i);
                    String tgt = translated.get(i);
                    cached.put(src, tgt);
                    toSave.add(new TranslationCache(src, targetLang, tgt));
                }
                try {
                    cacheRepo.saveAll(toSave);
                } catch (Exception ignored) {
                    // 중복 저장 경쟁 등 무시 — 이미 cached map에 있음
                }
            }
        }

        // 4) 원래 순서로 병합 (캐시 미스 + Google 실패 시 원문 폴백)
        return texts.stream()
                .map(s -> (s == null || s.isBlank()) ? s : cached.getOrDefault(s, s))
                .collect(Collectors.toList());
    }
}
