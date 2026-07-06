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
}
