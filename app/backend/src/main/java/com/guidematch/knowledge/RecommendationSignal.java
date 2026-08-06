package com.guidematch.knowledge;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 추천이 실제로 어떤 결과를 냈는지 — 노출됨 → 일정에 담김 → 예약까지 감.
 *
 * <p><b>v1에서는 아무도 이 표를 읽지 않는다.</b> 그래도 지금 쌓는 이유는 되찾을 수 없기
 * 때문이다. 안 남긴 과거는 영원히 없다. 랭킹(하위 프로젝트 4)이 붙는 시점에 몇 달치
 * 실적 데이터가 이미 있는 것과, 그때부터 0에서 모으기 시작하는 것의 차이가 크다.
 *
 * <p>이 시장에서 코스 추천에 대한 <b>예약 결과</b> 신호를 가진 곳은 없다. 그게 이 표의 값어치다.
 */
@Entity
@Table(
    name = "recommendation_signals",
    indexes = {
        @Index(name = "idx_signals_place", columnList = "place_id"),
        @Index(name = "idx_signals_occurred", columnList = "occurred_at")
    }
)
public class RecommendationSignal {

    public enum EventType { SHOWN, ADDED, BOOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    @Column(name = "place_id")
    private Long placeId;

    /** 코스 추천 응답의 식별자(도시/구/테마 등) — 아직 코스 엔티티가 없으므로 문자열 참조. */
    @Column(name = "course_ref", length = 200)
    private String courseRef;

    /** 비로그인 노출도 기록한다 — 그래서 nullable. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected RecommendationSignal() {}

    public RecommendationSignal(EventType eventType, Long placeId, String courseRef, Long userId) {
        this.eventType = eventType;
        this.placeId = placeId;
        this.courseRef = courseRef;
        this.userId = userId;
    }

    public Long getId()             { return id; }
    public EventType getEventType() { return eventType; }
    public Long getPlaceId()        { return placeId; }
    public String getCourseRef()    { return courseRef; }
    public Long getUserId()         { return userId; }
    public Instant getOccurredAt()  { return occurredAt; }
}
