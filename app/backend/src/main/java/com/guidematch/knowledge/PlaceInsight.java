package com.guidematch.knowledge;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * 장소에 대한 사실 카드 한 장 — "주말 오후 대기 30분", "벚꽃 4월 초순", "영어 메뉴 있음".
 *
 * <p><b>왜 JSONB인가</b>: {@code fact_kind}가 늘어날 때마다 컬럼을 추가할 수는 없다.
 * 이 프로젝트는 {@code ddl-auto: update}(가산만 가능)를 쓰므로 스키마를 자주 못 바꾼다.
 * 질의에 쓰는 것만 타입 컬럼으로 두고 변하는 부분은 JSONB에 넣으면, 새 사실 종류가
 * 생겨도 DDL이 전혀 필요 없다. Hibernate 6가 의존성 없이 지원한다.
 *
 * <p><b>멱등성</b>: {@code recordId}가 unique다. 같은 소스를 다시 추출하면 같은 해시가
 * 나오므로 재실행해도 행이 안 늘고 값만 최신으로 갱신된다(이력은 남기지 않는다 — 블로그가
 * 수정되면 최신 사실이 이기는 게 맞고, 이력을 남기면 모든 조회에 "키별 최신 1건"이 붙는다).
 */
@Entity
@Table(
    name = "place_insights",
    indexes = {
        @Index(name = "idx_insights_place", columnList = "place_id"),
        @Index(name = "idx_insights_place_kind", columnList = "place_id,fact_kind")
    },
    uniqueConstraints = @UniqueConstraint(name = "uk_insight_record", columnNames = "record_id")
)
public class PlaceInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false, length = 80)
    private String recordId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_kind", nullable = false, length = 40)
    private FactKind factKind;

    /** 구조화된 사실. 예: {"minutes":30,"when":"weekend_afternoon"} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb")
    private Map<String, Object> value;

    /**
     * 언어별 요약(각 120자 이하). 처음부터 JSONB인 이유: 우리 사용자는 방한 외국인이라
     * 다국어화가 사실상 확정인데, {@code varchar} → 다국어 전환은 ddl-auto:update로 불가능하다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "note_i18n", columnDefinition = "jsonb")
    private Map<String, String> noteI18n;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "evidence_url", columnDefinition = "TEXT")
    private String evidenceUrl;

    @Column(name = "evidence_publisher", length = 200)
    private String evidencePublisher;

    @Column(name = "evidence_published_at")
    private LocalDate evidencePublishedAt;

    @Column(name = "source_kind", length = 40)
    private String sourceKind;

    /** 어느 프롬프트 버전이 뽑았는지 — "이 버전 것만 재추출"을 가능하게 한다. */
    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(name = "run_id", length = 120)
    private String runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PlaceInsight() {}

    public PlaceInsight(String recordId, Long placeId, FactKind factKind,
                        Map<String, Object> value, Map<String, String> noteI18n,
                        double confidence, String evidenceUrl, String evidencePublisher,
                        LocalDate evidencePublishedAt, String sourceKind,
                        String promptVersion, String runId) {
        this.recordId = recordId;
        this.placeId = placeId;
        this.factKind = factKind;
        this.value = value;
        this.noteI18n = noteI18n;
        this.confidence = confidence;
        this.evidenceUrl = evidenceUrl;
        this.evidencePublisher = evidencePublisher;
        this.evidencePublishedAt = evidencePublishedAt;
        this.sourceKind = sourceKind;
        this.promptVersion = promptVersion;
        this.runId = runId;
    }

    /** 같은 {@code recordId}가 다시 들어왔을 때 — 최신 값이 이긴다. */
    public void overwriteFrom(PlaceInsight fresh) {
        this.placeId = fresh.placeId;
        this.factKind = fresh.factKind;
        this.value = fresh.value;
        this.noteI18n = fresh.noteI18n;
        this.confidence = fresh.confidence;
        this.evidenceUrl = fresh.evidenceUrl;
        this.evidencePublisher = fresh.evidencePublisher;
        this.evidencePublishedAt = fresh.evidencePublishedAt;
        this.sourceKind = fresh.sourceKind;
        this.promptVersion = fresh.promptVersion;
        this.runId = fresh.runId;
        this.updatedAt = Instant.now();
    }

    public Long getId()                       { return id; }
    public String getRecordId()               { return recordId; }
    public Long getPlaceId()                  { return placeId; }
    public FactKind getFactKind()             { return factKind; }
    public Map<String, Object> getValue()     { return value; }
    public Map<String, String> getNoteI18n()  { return noteI18n; }
    public double getConfidence()             { return confidence; }
    public String getEvidenceUrl()            { return evidenceUrl; }
    public String getEvidencePublisher()      { return evidencePublisher; }
    public LocalDate getEvidencePublishedAt() { return evidencePublishedAt; }
    public String getSourceKind()             { return sourceKind; }
    public String getPromptVersion()          { return promptVersion; }
    public String getRunId()                  { return runId; }
}
