package com.guidematch.geo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 정형화된 한국 도시 목록 (공개). 프론트 도시 선택 드롭다운의 소스. */
@RestController
public class CityController {

    @GetMapping("/api/cities")
    public List<KoreanCity> list() {
        return KoreanCity.LIST;
    }
}
