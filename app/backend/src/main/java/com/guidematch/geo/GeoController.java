package com.guidematch.geo;

import com.guidematch.geo.KakaoLocalClient.KakaoRegion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 위치(GPS) 역지오코딩 (공개).
 * 브라우저 Geolocation 좌표 → 가장 가까운 표준 도시 + (가능하면) Kakao 정밀 행정구역.
 * Kakao 키가 없어도 nearestCity는 항상 반환한다(순수 좌표 거리 계산).
 */
@RestController
public class GeoController {

    private final KakaoLocalClient kakaoClient;

    public GeoController(KakaoLocalClient kakaoClient) {
        this.kakaoClient = kakaoClient;
    }

    @GetMapping("/api/geo/reverse")
    public ReverseGeocodeResponse reverse(@RequestParam double lat, @RequestParam double lng) {
        KoreanCity nearest = KoreanCity.nearestTo(lat, lng);
        KakaoRegion region = kakaoClient.reverseGeocode(lat, lng);
        return new ReverseGeocodeResponse(
                nearest,
                region != null ? region.label() : null,
                kakaoClient.isEnabled()
        );
    }

    public record ReverseGeocodeResponse(KoreanCity nearestCity, String kakaoRegion, boolean kakaoEnabled) {}
}
