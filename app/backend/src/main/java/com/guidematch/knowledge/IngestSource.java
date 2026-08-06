package com.guidematch.knowledge;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 이미 수집한 소스 URL — <b>다음 실행의 커서</b>.
 *
 * <p>매 실행 끝에 이 표를 {@code state/ingested-sources.jsonl}로 내보내고, Codex는 그걸 읽어
 * "아직 안 한 범위"를 스스로 고른다. 커서를 파일이 아니라 DB에 두는 이유는, 파일이 지워지거나
 * 맥을 바꿔도 진행 상황이 살아남아야 하기 때문이다.
 */
@Entity
@Table(
    name = "ingest_sources",
    indexes = @Index(name = "idx_ingest_sources_scope", columnList = "scope_key"),
    uniqueConstraints = @UniqueConstraint(name = "uk_ingest_source_url", columnNames = "url_hash")
)
public class IngestSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL은 길이 제한이 없어 unique 인덱스를 못 걸므로 해시에 건다. */
    @Column(name = "url_hash", nullable = false, length = 80)
    private String urlHash;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "source_kind", length = 40)
    private String sourceKind;

    /** "seoul/성동구" 형태 — Codex가 범위 단위로 "했다/안 했다"를 판단하는 키. */
    @Column(name = "scope_key", length = 120)
    private String scopeKey;

    // content_hash 컬럼은 두지 않는다 — 아무도 계산하지 않아 항상 NULL이 될 뿐이고,
    // 늘 비어 있는 컬럼은 나중에 누군가 그걸 믿게 만든다. 본문 변경 감지가 실제로
    // 필요해지면(하위 프로젝트 2) 그때 채우는 코드와 함께 추가한다.

    @Column(name = "first_seen_run", length = 120)
    private String firstSeenRun;

    @Column(name = "last_seen_run", length = 120)
    private String lastSeenRun;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    protected IngestSource() {}

    public IngestSource(String urlHash, String url, String sourceKind,
                        String scopeKey, String runId) {
        this.urlHash = urlHash;
        this.url = url;
        this.sourceKind = sourceKind;
        this.scopeKey = scopeKey;
        this.firstSeenRun = runId;
        this.lastSeenRun = runId;
    }

    public void touch(String runId) {
        this.lastSeenRun = runId;
        this.lastSeenAt = Instant.now();
    }

    public Long getId()           { return id; }
    public String getUrlHash()    { return urlHash; }
    public String getUrl()        { return url; }
    public String getSourceKind() { return sourceKind; }
    public String getScopeKey()   { return scopeKey; }
    public String getFirstSeenRun(){ return firstSeenRun; }
    public String getLastSeenRun(){ return lastSeenRun; }
    public Instant getLastSeenAt(){ return lastSeenAt; }
}
