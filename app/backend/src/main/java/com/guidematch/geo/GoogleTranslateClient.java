package com.guidematch.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Google Cloud Translation API v2 (REST) 클라이언트.
 * 키 없으면 isEnabled()==false → 빈 목록 반환, 앱 정상 동작.
 * format=text 강제 — HTML 엔티티 인코딩 방지.
 */
@Component
public class GoogleTranslateClient {

    private static final String ENDPOINT =
            "https://translation.googleapis.com/language/translate/v2";

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public GoogleTranslateClient(@Value("${google.translate-api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 여러 텍스트를 한 번에 번역 (배치). 실패 시 빈 목록.
     * 반환 순서는 입력 순서와 동일.
     *
     * @param texts      원문 목록 (한국어)
     * @param targetLang 목표 언어 코드 (예: "en", "zh-CN")
     * @return 번역 결과 목록 (같은 크기); 실패 시 빈 목록
     */
    public List<String> translate(List<String> texts, String targetLang) {
        return translate(texts, "ko", targetLang);
    }

    /**
     * 원문 언어를 지정하거나(장소명은 항상 ko), null이면 Google이 자동 감지.
     * 채팅처럼 원문 언어를 알 수 없는 텍스트는 sourceLang=null로 호출한다.
     */
    public List<String> translate(List<String> texts, String sourceLang, String targetLang) {
        if (!isEnabled() || texts == null || texts.isEmpty()) return List.of();
        try {
            TranslateRequest body = new TranslateRequest(texts, sourceLang, targetLang, "text");
            TranslateResponse res = restClient.post()
                    .uri(ENDPOINT + "?key={k}", apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TranslateResponse.class);
            if (res == null || res.data() == null || res.data().translations() == null) return List.of();
            return res.data().translations().stream()
                    .map(Translation::translatedText)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 프론트 lang 코드 → Google 언어 코드. ko는 번역 스킵 대상이므로 null. */
    public static String toGoogleLang(String lang) {
        return switch (lang == null ? "" : lang) {
            case "en" -> "en";
            case "zh" -> "zh-CN";
            default  -> null;  // ko 또는 미지원 → 번역 안 함
        };
    }

    // ── request / response records ──────────────────────────────────────

    // source가 null이면 필드 자체를 생략 → Google이 원문 언어를 자동 감지
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private record TranslateRequest(
            @JsonProperty("q") List<String> q,
            @JsonProperty("source") String source,
            @JsonProperty("target") String target,
            @JsonProperty("format") String format
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TranslateResponse(@JsonProperty("data") TranslateData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TranslateData(@JsonProperty("translations") List<Translation> translations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Translation(@JsonProperty("translatedText") String translatedText) {}
}
