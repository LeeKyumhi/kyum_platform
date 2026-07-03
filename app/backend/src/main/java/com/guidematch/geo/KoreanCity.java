package com.guidematch.geo;

import java.util.List;
import java.util.Map;

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

    /**
     * 도시 key → 세부 지역(구) 목록. 구가 있는 도시만 등록(추가형 — record는 건드리지 않음).
     * ko는 지도/주소·Kakao 지오코딩("{도시} {ko}")의 표준 키, en(로마자)/zh(중문)는 표시용.
     */
    public record District(String ko, String en, String zh) {}

    private static District d(String ko, String en, String zh) { return new District(ko, en, zh); }

    public static final Map<String, List<District>> DISTRICTS = Map.ofEntries(
            Map.entry("Seoul", List.of(
                    d("종로구", "Jongno-gu", "钟路区"), d("중구", "Jung-gu", "中区"), d("용산구", "Yongsan-gu", "龙山区"),
                    d("성동구", "Seongdong-gu", "城东区"), d("광진구", "Gwangjin-gu", "广津区"), d("동대문구", "Dongdaemun-gu", "东大门区"),
                    d("중랑구", "Jungnang-gu", "中浪区"), d("성북구", "Seongbuk-gu", "城北区"), d("강북구", "Gangbuk-gu", "江北区"),
                    d("도봉구", "Dobong-gu", "道峰区"), d("노원구", "Nowon-gu", "芦原区"), d("은평구", "Eunpyeong-gu", "恩平区"),
                    d("서대문구", "Seodaemun-gu", "西大门区"), d("마포구", "Mapo-gu", "麻浦区"), d("양천구", "Yangcheon-gu", "阳川区"),
                    d("강서구", "Gangseo-gu", "江西区"), d("구로구", "Guro-gu", "九老区"), d("금천구", "Geumcheon-gu", "衿川区"),
                    d("영등포구", "Yeongdeungpo-gu", "永登浦区"), d("동작구", "Dongjak-gu", "铜雀区"), d("관악구", "Gwanak-gu", "冠岳区"),
                    d("서초구", "Seocho-gu", "瑞草区"), d("강남구", "Gangnam-gu", "江南区"), d("송파구", "Songpa-gu", "松坡区"),
                    d("강동구", "Gangdong-gu", "江东区"))),
            Map.entry("Busan", List.of(
                    d("중구", "Jung-gu", "中区"), d("서구", "Seo-gu", "西区"), d("동구", "Dong-gu", "东区"),
                    d("영도구", "Yeongdo-gu", "影岛区"), d("부산진구", "Busanjin-gu", "釜山镇区"), d("동래구", "Dongnae-gu", "东莱区"),
                    d("남구", "Nam-gu", "南区"), d("북구", "Buk-gu", "北区"), d("해운대구", "Haeundae-gu", "海云台区"),
                    d("사하구", "Saha-gu", "沙下区"), d("금정구", "Geumjeong-gu", "金井区"), d("강서구", "Gangseo-gu", "江西区"),
                    d("연제구", "Yeonje-gu", "莲堤区"), d("수영구", "Suyeong-gu", "水营区"), d("사상구", "Sasang-gu", "沙上区"))),
            Map.entry("Incheon", List.of(
                    d("중구", "Jung-gu", "中区"), d("동구", "Dong-gu", "东区"), d("미추홀구", "Michuhol-gu", "弥邹忽区"),
                    d("연수구", "Yeonsu-gu", "延寿区"), d("남동구", "Namdong-gu", "南洞区"), d("부평구", "Bupyeong-gu", "富平区"),
                    d("계양구", "Gyeyang-gu", "桂阳区"), d("서구", "Seo-gu", "西区"))),
            Map.entry("Daegu", List.of(
                    d("중구", "Jung-gu", "中区"), d("동구", "Dong-gu", "东区"), d("서구", "Seo-gu", "西区"),
                    d("남구", "Nam-gu", "南区"), d("북구", "Buk-gu", "北区"), d("수성구", "Suseong-gu", "寿城区"),
                    d("달서구", "Dalseo-gu", "达西区"))),
            Map.entry("Daejeon", List.of(
                    d("동구", "Dong-gu", "东区"), d("중구", "Jung-gu", "中区"), d("서구", "Seo-gu", "西区"),
                    d("유성구", "Yuseong-gu", "儒城区"), d("대덕구", "Daedeok-gu", "大德区"))),
            Map.entry("Gwangju", List.of(
                    d("동구", "Dong-gu", "东区"), d("서구", "Seo-gu", "西区"), d("남구", "Nam-gu", "南区"),
                    d("북구", "Buk-gu", "北区"), d("광산구", "Gwangsan-gu", "光山区"))),
            Map.entry("Ulsan", List.of(
                    d("중구", "Jung-gu", "中区"), d("남구", "Nam-gu", "南区"), d("동구", "Dong-gu", "东区"),
                    d("북구", "Buk-gu", "北区"))),
            Map.entry("Suwon", List.of(
                    d("장안구", "Jangan-gu", "长安区"), d("권선구", "Gwonseon-gu", "劝善区"),
                    d("팔달구", "Paldal-gu", "八达区"), d("영통구", "Yeongtong-gu", "灵通区"))),
            Map.entry("Jeonju", List.of(
                    d("완산구", "Wansan-gu", "完山区"), d("덕진구", "Deokjin-gu", "德津区"))),
            Map.entry("Pohang", List.of(
                    d("남구", "Nam-gu", "南区"), d("북구", "Buk-gu", "北区")))
    );

    /** 도시 key의 구 목록(없으면 빈 목록). */
    public static List<District> districtsOf(String key) {
        return DISTRICTS.getOrDefault(key, List.of());
    }

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
