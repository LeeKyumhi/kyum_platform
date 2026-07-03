package com.guidematch.geo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 정형화된 한국 도시 목록 (공개). 프론트 도시 선택 드롭다운의 소스. 각 도시에 세부 지역(구) 목록 포함. */
@RestController
public class CityController {

    @GetMapping("/api/cities")
    public List<CityDto> list() {
        return KoreanCity.LIST.stream()
                .map(c -> new CityDto(
                        c.key(), c.nameKo(), c.nameEn(), c.nameZh(), c.lat(), c.lng(),
                        KoreanCity.districtsOf(c.key())))
                .toList();
    }

    /** 도시 정보 + 구 목록(있으면, ko/en/zh). 프론트 City 타입과 매핑. */
    public record CityDto(
            String key,
            String nameKo,
            String nameEn,
            String nameZh,
            double lat,
            double lng,
            List<KoreanCity.District> districts
    ) {}
}
