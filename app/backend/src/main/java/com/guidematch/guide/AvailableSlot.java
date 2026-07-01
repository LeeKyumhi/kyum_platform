package com.guidematch.guide;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "available_slots")
public class AvailableSlot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    /** 슬롯 시작 일시 (가이드 현지 시간 기준, timezone-naive) */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** 슬롯 종료 일시 */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    protected AvailableSlot() {}

    public AvailableSlot(Long guideProfileId, LocalDateTime startAt, LocalDateTime endAt) {
        this.guideProfileId = guideProfileId;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public Long getId()              { return id; }
    public Long getGuideProfileId()  { return guideProfileId; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt()   { return endAt; }
}
