package com.guidematch.geo;

import java.util.List;

/**
 * 한국 주요/관광 도시의 정형화된 목록 (단일 진실 공급원).
 *  - 프론트 도시 드롭다운(GET /api/cities)의 소스
 *  - GPS 좌표 → 최근접 도시 매핑(reverse geocoding)에도 재사용
 * 다국어 이름을 함께 담아 프론트 i18n 부담을 줄인다.
 */
public record KoreanCity(
        String key,      // 검색/저장에 쓰는 표준 키 (예: "Seoul")
        String nameKo,
        String nameEn,
        String nameZh,
        double lat,
        double lng
) {
    public static final List<KoreanCity> LIST = List.of(
            new KoreanCity("Seoul",       "서울",   "Seoul",       "首尔", 37.5665, 126.9780),
            new KoreanCity("Busan",       "부산",   "Busan",       "釜山", 35.1796, 129.0756),
            new KoreanCity("Incheon",     "인천",   "Incheon",     "仁川", 37.4563, 126.7052),
            new KoreanCity("Daegu",       "대구",   "Daegu",       "大邱", 35.8714, 128.6014),
            new KoreanCity("Daejeon",     "대전",   "Daejeon",     "大田", 36.3504, 127.3845),
            new KoreanCity("Gwangju",     "광주",   "Gwangju",     "光州", 35.1595, 126.8526),
            new KoreanCity("Ulsan",       "울산",   "Ulsan",       "蔚山", 35.5384, 129.3114),
            new KoreanCity("Suwon",       "수원",   "Suwon",       "水原", 37.2636, 127.0286),
            new KoreanCity("Jeju",        "제주",   "Jeju",        "济州", 33.4996, 126.5312),
            new KoreanCity("Gyeongju",    "경주",   "Gyeongju",    "庆州", 35.8562, 129.2247),
            new KoreanCity("Jeonju",      "전주",   "Jeonju",      "全州", 35.8242, 127.1480),
            new KoreanCity("Gangneung",   "강릉",   "Gangneung",   "江陵", 37.7519, 128.8761),
            new KoreanCity("Chuncheon",   "춘천",   "Chuncheon",   "春川", 37.8813, 127.7300),
            new KoreanCity("Sokcho",      "속초",   "Sokcho",      "束草", 38.2070, 128.5918),
            new KoreanCity("Andong",      "안동",   "Andong",      "安东", 36.5684, 128.7294),
            new KoreanCity("Tongyeong",   "통영",   "Tongyeong",   "统营", 34.8544, 128.4331),
            new KoreanCity("Yeosu",       "여수",   "Yeosu",       "丽水", 34.7604, 127.6622),
            new KoreanCity("Pohang",      "포항",   "Pohang",      "浦项", 36.0190, 129.3435),
            new KoreanCity("Mokpo",       "목포",   "Mokpo",       "木浦", 34.8118, 126.3922),
            new KoreanCity("Pyeongchang", "평창",   "Pyeongchang", "平昌", 37.3705, 128.3901)
    );

    /** 주어진 좌표에서 가장 가까운 도시를 반환 (haversine). 목록이 비면 null. */
    public static KoreanCity nearestTo(double lat, double lng) {
        KoreanCity nearest = null;
        double best = Double.MAX_VALUE;
        for (KoreanCity c : LIST) {
            double d = GeoUtils.distanceKm(lat, lng, c.lat(), c.lng());
            if (d < best) { best = d; nearest = c; }
        }
        return nearest;
    }
}
