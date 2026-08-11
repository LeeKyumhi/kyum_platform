package com.guidematch.knowledge;

import com.guidematch.itinerary.ItineraryRepository;
import com.guidematch.saved.SavedItemRepository;
import com.guidematch.saved.SavedItemType;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🧳 "여행자 N명이 담은 곳" — 장소별 담은 사람 수.
 *
 * <p>🎫가 전문가의 판단이라면 이건 또래의 판단이다. 수집 데이터가 거의 없는 지역에서도
 * 실제 사용 흔적은 쌓이므로, cold-start를 실질적으로 버텨주는 유일한 사람 신호이기도 하다.
 *
 * <p><b>두 출처를 더하지 않고 합집합으로 센다.</b> 찜(saved_items)과 일정(itinerary_items)은
 * 서로 다른 테이블이지만 같은 사람이 양쪽에 남길 수 있다. 수를 더하면 한 사람이 2명이 되어
 * 배지가 부풀려지고, "N명"이라는 문구가 그 순간 거짓이 된다. 그래서 저장소는 수가 아니라
 * [사람, 장소] 쌍을 돌려주고 합집합은 여기서 만든다.
 *
 * <p><b>표기 차이 흡수</b>: 찜은 {@code place_ref}에 {@code kakao:} 접두어를 붙여 저장하고
 * (explore 화면 규약) 일정은 접두어 없는 원시 id를 쓴다. 이 비대칭을 여기서 흡수하지 않으면
 * 조인이 통째로 빗나가는데, 그 결과는 "아직 아무도 안 담은 장소"와 똑같아 보여서 버그로 드러나지 않는다.
 */
@Service
public class TravelerSignalLookup {

    /** 찜 저장 시 붙는 접두어 (explore/page.tsx의 `kakao:${p.id}`). */
    private static final String SAVED_REF_PREFIX = "kakao:";

    private final SavedItemRepository savedRepo;
    private final ItineraryRepository itineraryRepo;

    public TravelerSignalLookup(SavedItemRepository savedRepo, ItineraryRepository itineraryRepo) {
        this.savedRepo = savedRepo;
        this.itineraryRepo = itineraryRepo;
    }

    /**
     * Kakao place id → 그 장소를 찜했거나 일정에 담은 <b>사람 수</b>.
     *
     * @return 담은 사람이 있는 장소만. 0인 장소는 키가 아예 없다(규칙2: 0은 표시하지 않는다).
     */
    public Map<String, Integer> travelerCounts(Collection<String> kakaoPlaceIds) {
        List<String> ids = kakaoPlaceIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct().toList();
        if (ids.isEmpty()) return Map.of();

        Map<String, Set<Long>> peopleByPlace = new HashMap<>();

        List<String> refs = ids.stream().map(id -> SAVED_REF_PREFIX + id).toList();
        for (Object[] row : savedRepo.userPlaceRefPairs(SavedItemType.PLACE, refs)) {
            add(peopleByPlace, stripPrefix(asString(row[1])), asLong(row[0]));
        }
        for (Object[] row : itineraryRepo.ownerPlaceIdPairs(ids)) {
            add(peopleByPlace, asString(row[1]), asLong(row[0]));
        }

        Set<String> wanted = new HashSet<>(ids);
        Map<String, Integer> counts = new HashMap<>();
        peopleByPlace.forEach((placeId, people) -> {
            if (placeId != null && wanted.contains(placeId) && !people.isEmpty()) {
                counts.put(placeId, people.size());
            }
        });
        return counts;
    }

    private static void add(Map<String, Set<Long>> acc, String placeId, Long userId) {
        // 사용자를 알 수 없는 행은 사람으로 셀 수 없다 — 세면 "N명"이 거짓이 된다.
        if (placeId == null || userId == null) return;
        acc.computeIfAbsent(placeId, k -> new HashSet<>()).add(userId);
    }

    private static String stripPrefix(String ref) {
        if (ref == null) return null;
        return ref.startsWith(SAVED_REF_PREFIX) ? ref.substring(SAVED_REF_PREFIX.length()) : ref;
    }

    private static String asString(Object o) { return o == null ? null : o.toString(); }

    private static Long asLong(Object o) { return o instanceof Number n ? n.longValue() : null; }
}
