package com.guidematch.knowledge;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** 수집 실행 1건의 기록 — 무엇을·언제·어떤 프롬프트로 뽑았는지. */
@Entity
@Table(
    name = "ingest_runs",
    uniqueConstraints = @UniqueConstraint(name = "uk_ingest_run", columnNames = "run_id")
)
public class IngestRun {

    public enum Status { STARTED, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 120)
    private String runId;

    @Column(name = "source_kind", length = 40)
    private String sourceKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_json", columnDefinition = "jsonb")
    private Map<String, Object> scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counts_json", columnDefinition = "jsonb")
    private Map<String, Object> counts;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.STARTED;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected IngestRun() {}

    public IngestRun(String runId, String sourceKind, Map<String, Object> scope, String promptVersion) {
        this.runId = runId;
        this.sourceKind = sourceKind;
        this.scope = scope;
        this.promptVersion = promptVersion;
    }

    public void complete(Map<String, Object> counts) {
        this.counts = counts;
        this.status = Status.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = Status.FAILED;
        this.message = message;
        this.finishedAt = Instant.now();
    }

    public Long getId()                     { return id; }
    public String getRunId()                { return runId; }
    public String getSourceKind()           { return sourceKind; }
    public Map<String, Object> getScope()   { return scope; }
    public Map<String, Object> getCounts()  { return counts; }
    public String getPromptVersion()        { return promptVersion; }
    public Status getStatus()               { return status; }
    public String getMessage()              { return message; }
}
