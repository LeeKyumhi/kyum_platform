package com.guidematch.knowledge;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 미해결 보관함 — 어느 장소인지 <b>확신할 수 없어</b> 병합하지 않은 레코드.
 *
 * <p>이건 결함이 아니라 기능이다. 잘못된 병합은 되돌릴 수 없지만(두 장소의 인사이트가
 * 섞이면 어느 쪽이 원래 것인지 알 방법이 없다), 미해결은 나중에 언제든 해결할 수 있다.
 * 그래서 애매하면 무조건 여기로 보낸다.
 *
 * <p>원본 줄은 TEXT로 그대로 보관한다. JSONB로 안 하는 이유: 이 표는 JSON 경로로
 * 질의하는 대상이 아니라 <b>나중에 다시 처리하기 위한 원본 보존소</b>라, 파싱 가능성보다
 * 원문 무손실이 중요하다.
 */
@Entity
@Table(
    name = "place_candidates_unresolved",
    indexes = @Index(name = "idx_unresolved_run", columnList = "run_id")
)
public class PlaceCandidateUnresolved {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", length = 80)
    private String recordId;

    @Column(name = "raw_json", nullable = false, columnDefinition = "TEXT")
    private String rawJson;

    /** 왜 해결 못 했는지 — 프롬프트/사다리 튜닝의 유일한 단서. */
    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Column(name = "run_id", length = 120)
    private String runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PlaceCandidateUnresolved() {}

    public PlaceCandidateUnresolved(String recordId, String rawJson, String reason, String runId) {
        this.recordId = recordId;
        this.rawJson = rawJson;
        this.reason = reason;
        this.runId = runId;
    }

    public Long getId()        { return id; }
    public String getRecordId(){ return recordId; }
    public String getRawJson() { return rawJson; }
    public String getReason()  { return reason; }
    public String getRunId()   { return runId; }
}
