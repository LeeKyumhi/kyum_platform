package com.guidematch.guide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 투어 코스의 동선 상 장소 하나(순서가 있는 스냅샷).
 * ItineraryItem과 동일한 패턴 — Kakao 장소 데이터를 스냅샷으로 저장해
 * 코스 상세에서 TripMap(핀 + 폴리라인)으로 렌더링한다.
 */
@Entity
@Table(name = "tour_course_waypoints")
public class TourCourseWaypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 코스 안에서의 순서 (0부터). */
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

    /**
     * 시간표 빌더 편집 상태(선택). 가이드가 코스를 시간표로 구성한 배치를 저장해
     * 재편집 시 그대로 복원한다(ItineraryItem 패턴). 전부 nullable — 순서(sortOrder)만으로도 유효.
     * ⚠️ 편집 전용: 공개 코스 뷰(가이드 상세/CourseCard/명소 퍼널)는 이 값을 시간으로 노출하지 않는다.
     */
    @Column(name = "start_hour")
    private Integer startHour;

    @Column(name = "duration_hours")
    private Integer durationHours;

    @Column(name = "lane_index")
    private Integer laneIndex;

    @Column(name = "lane_span")
    private Integer laneSpan;

    protected TourCourseWaypoint() {}

    public TourCourseWaypoint(int sortOrder, String placeId, String placeName, String category,
                              String address, Double latitude, Double longitude,
                              Integer startHour, Integer durationHours, Integer laneIndex, Integer laneSpan) {
        this.sortOrder = sortOrder;
        this.placeId = placeId;
        this.placeName = placeName;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startHour = startHour;
        this.durationHours = durationHours;
        this.laneIndex = laneIndex;
        this.laneSpan = laneSpan;
    }

    public Long getId() { return id; }
    public int getSortOrder() { return sortOrder; }
    public String getPlaceId() { return placeId; }
    public String getPlaceName() { return placeName; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Integer getStartHour() { return startHour; }
    public Integer getDurationHours() { return durationHours; }
    public Integer getLaneIndex() { return laneIndex; }
    public Integer getLaneSpan() { return laneSpan; }
}
