package com.guidematch.knowledge;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 같은 장소를 가리키는 다른 표기 — "Onion Seongsu", "어니언(성수점)".
 *
 * <p>해결 사다리 2단으로 결합될 때마다 쌓이고, 다음 실행부터는 이 별칭이 곧바로 매칭에 쓰인다.
 * 즉 <b>돌수록 매칭이 좋아지는</b> 부분이다.
 */
@Entity
@Table(
    name = "place_aliases",
    indexes = @Index(name = "idx_place_aliases_norm", columnList = "alias_normalized"),
    uniqueConstraints = @UniqueConstraint(
        name = "uk_place_alias", columnNames = {"place_id", "alias_normalized"})
)
public class PlaceAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "alias_raw", nullable = false, columnDefinition = "TEXT")
    private String aliasRaw;

    @Column(name = "alias_normalized", nullable = false, length = 200)
    private String aliasNormalized;

    /** 이 별칭이 어느 소스에서 왔는지 — 나중에 특정 소스의 오염을 걷어낼 때 필요하다. */
    @Column(name = "source_kind", length = 40)
    private String sourceKind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PlaceAlias() {}

    public PlaceAlias(Long placeId, String aliasRaw, String sourceKind) {
        this.placeId = placeId;
        this.aliasRaw = aliasRaw;
        this.aliasNormalized = PlaceNames.normalize(aliasRaw);
        this.sourceKind = sourceKind;
    }

    public Long getId()               { return id; }
    public Long getPlaceId()          { return placeId; }
    public String getAliasRaw()       { return aliasRaw; }
    public String getAliasNormalized(){ return aliasNormalized; }
    public String getSourceKind()     { return sourceKind; }
}
