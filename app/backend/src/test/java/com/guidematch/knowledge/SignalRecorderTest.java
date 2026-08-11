package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * "담았다"(ADDED) 신호 기록.
 *
 * <p>이 신호가 2사이클의 🧳("여행자 N명이 담음")과 가이드용 수요 패널의 유일한 원천이다.
 * <b>지금 심지 않으면 2사이클은 빈손으로 시작한다</b> — 과거의 담기는 되돌려 받을 수 없다.
 *
 * <p>동시에, 신호 기록이 사용자 동작을 막아서는 절대 안 된다. 담기는 성공했는데 기록이 실패해서
 * 에러가 뜨면 사용자에게는 담기가 실패한 것으로 보인다.
 */
class SignalRecorderTest {

    private final RecommendationSignalRepository repo = mock(RecommendationSignalRepository.class);
    private final PlaceRepository placeRepo = mock(PlaceRepository.class);
    private final SignalRecorder recorder = new SignalRecorder(repo, placeRepo);

    private Place place(long id, String kakaoPlaceId) {
        Place p = new Place("경복궁", "Seoul", "종로구", 37.5796, 126.9770, kakaoPlaceId, null,
                "관광명소", "서울 종로구 사직로 161");
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @SuppressWarnings("unchecked")
    private List<RecommendationSignal> captureSaved() {
        ArgumentCaptor<List<RecommendationSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void 레지스트리_장소를_담으면_ADDED로_기록된다() {
        recorder.recordAdded(new SignalRecorder.StopRef(42L, "1234567"), "Seoul/종로구/mixed", 7L);

        RecommendationSignal saved = captureSaved().get(0);
        assertThat(saved.getEventType()).isEqualTo(RecommendationSignal.EventType.ADDED);
        assertThat(saved.getPlaceId()).isEqualTo(42L);
    }

    /** SHOWN↔ADDED를 같은 키로 짝지어야 "보여준 것 중 몇 개가 담겼나"를 나중에 셀 수 있다. */
    @Test
    void courseRef와_사용자를_함께_남긴다() {
        recorder.recordAdded(new SignalRecorder.StopRef(42L, "1234567"), "Seoul/종로구/mixed", 7L);

        RecommendationSignal saved = captureSaved().get(0);
        assertThat(saved.getCourseRef()).isEqualTo("Seoul/종로구/mixed");
        assertThat(saved.getUserId()).isEqualTo(7L);
    }

    /** 레지스트리 id를 모르고 Kakao id만 아는 폴백 정차지 — 조회해서 이어붙인다. */
    @Test
    void kakao_id만_알아도_레지스트리_장소로_이어붙인다() {
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of(place(42L, "1234567")));

        recorder.recordAdded(new SignalRecorder.StopRef(null, "1234567"), "Seoul//mixed", 7L);

        assertThat(captureSaved().get(0).getPlaceId()).isEqualTo(42L);
    }

    /**
     * 우리 레지스트리에 없는 장소도 기록한다 — place_id만 null.
     * "추천에 나왔고 사용자가 담기까지 한 장소인데 지식이 없다"가 곧 최우선 수집 대상이다.
     */
    @Test
    void 레지스트리에_없는_장소도_place_id만_비운_채_기록한다() {
        when(placeRepo.findAllByKakaoPlaceIdIn(anyCollection())).thenReturn(List.of());

        recorder.recordAdded(new SignalRecorder.StopRef(null, "999"), "Seoul//mixed", 7L);

        List<RecommendationSignal> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPlaceId()).isNull();
        assertThat(saved.get(0).getEventType()).isEqualTo(RecommendationSignal.EventType.ADDED);
    }

    /** 기록이 실패해도 사용자에게는 담기가 성공한 것으로 보여야 한다. */
    @Test
    void 저장이_터져도_예외를_밖으로_던지지_않는다() {
        when(repo.saveAll(any())).thenThrow(new RuntimeException("DB 죽음"));

        assertThatCode(() -> recorder.recordAdded(
                new SignalRecorder.StopRef(42L, "1234567"), "Seoul//mixed", 7L))
                .doesNotThrowAnyException();
    }

    /** 식별자가 아무것도 없으면 남길 것이 없다 — 빈 행을 만들지 않는다. */
    @Test
    void 식별자가_없으면_아무것도_기록하지_않는다() {
        recorder.recordAdded(new SignalRecorder.StopRef(null, null), "Seoul//mixed", 7L);

        verify(repo, never()).saveAll(any());
    }

    /** 비로그인 사용자의 담기도 신호로서는 유효하다 — user_id만 null. */
    @Test
    void 로그인하지_않은_사용자의_담기도_기록한다() {
        recorder.recordAdded(new SignalRecorder.StopRef(42L, "1234567"), "Seoul//mixed", null);

        assertThat(captureSaved().get(0).getUserId()).isNull();
    }
}
