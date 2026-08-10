package com.guidematch.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceAliasRepository extends JpaRepository<PlaceAlias, Long> {

    List<PlaceAlias> findByAliasNormalized(String aliasNormalized);

    boolean existsByPlaceIdAndAliasNormalized(Long placeId, String aliasNormalized);
}
