package com.guidematch.itinerary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 여행 일정 안의 장소 하나 (일자별로 담기는 "모듈").
 * Kakao 장소 데이터를 스냅샷으로 저장한다(placeId + 이름 + 좌표 + 카테고리).
 * 한 일정이 여러 아이템을 가지므로 별도 테이블(itinerary_items).
 */
@Entity
@Table(name = "itinerary_items")
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 며칠차 (1부터). */
    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    /** 같은 날 안에서의 순서 (0부터). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Kakao 장소 ID (스냅샷). */
    @Column(name = "place_id")
    private String placeId;

    @Column(name = "place_name", nullable = false)
    private String placeName;

    @Column(name = "category")
    private String category;

    @Column(name = "address")
    private String address;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "memo", columnDefinition = "text")
    private String memo;

    /** 이 아이템이 예약 확정으로 자동 생성됐다면 그 예약 id. 중복 생성 방지(멱등성)용. */
    @Column(name = "source_booking_id")
    private Long sourceBookingId;

    // ── 타임테이블(시간표) 배치 정보 ── 전부 nullable (ddl-auto additive 안전, 레거시 아이템은 null).
    /** 시작 시각(시 단위, 0~23). null이면 시간 미지정(레거시/자유 아이템). */
    @Column(name = "start_hour")
    private Integer startHour;

    /** 깊이 = 소요 시간(시 단위, 1 이상). null이면 1로 간주. */
    @Column(name = "duration_hours")
    private Integer durationHours;

    /** 몇 번째 레인(가로 칸, 0부터) — 같은 시간대 여러 개를 나란히 두기 위함. */
    @Column(name = "lane_index")
    private Integer laneIndex;

    /** 넓이 = 차지하는 레인 수(1 이상). null이면 1로 간주. */
    @Column(name = "lane_span")
    private Integer laneSpan;

    /** 이 아이템이 가이드 투어코스 블록이면 그 코스 id (누르면 상세/예약 CTA, A4 연결). */
    @Column(name = "source_course_id")
    private Long sourceCourseId;

    protected ItineraryItem() {}

    public ItineraryItem(int dayIndex, int sortOrder, String placeId, String placeName,
                         String category, String address, Double latitude, Double longitude, String memo) {
        this.dayIndex = dayIndex;
        this.sortOrder = sortOrder;
        this.placeId = placeId;
        this.placeName = placeName;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.memo = memo;
    }

    public Long getId() { return id; }
    public int getDayIndex() { return dayIndex; }
    public int getSortOrder() { return sortOrder; }
    public String getPlaceId() { return placeId; }
    public String getPlaceName() { return placeName; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getMemo() { return memo; }
    public Long getSourceBookingId() { return sourceBookingId; }
    public void setSourceBookingId(Long sourceBookingId) { this.sourceBookingId = sourceBookingId; }

    public Integer getStartHour() { return startHour; }
    public void setStartHour(Integer startHour) { this.startHour = startHour; }
    public Integer getDurationHours() { return durationHours; }
    public void setDurationHours(Integer durationHours) { this.durationHours = durationHours; }
    public Integer getLaneIndex() { return laneIndex; }
    public void setLaneIndex(Integer laneIndex) { this.laneIndex = laneIndex; }
    public Integer getLaneSpan() { return laneSpan; }
    public void setLaneSpan(Integer laneSpan) { this.laneSpan = laneSpan; }
    public Long getSourceCourseId() { return sourceCourseId; }
    public void setSourceCourseId(Long sourceCourseId) { this.sourceCourseId = sourceCourseId; }
}
