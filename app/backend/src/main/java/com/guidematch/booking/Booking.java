package com.guidematch.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 예약(매칭) 한 건.
 * 여행자가 특정 가이드에게 요청하면 생성되고, 가이드의 수락으로 계약이 성립한다.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 요청한 여행자(User)의 id */
    @Column(name = "traveler_id", nullable = false)
    private Long travelerId;

    /** 대상 가이드 프로필의 id */
    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    /** 희망 시작 시각 */
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    /** 이용 시간(시간 단위) */
    @Column(nullable = false)
    private Integer hours;

    /**
     * 계약 시점의 시간당 요금 스냅샷.
     * 가이드가 나중에 요금을 바꿔도 이 예약의 금액은 변하지 않도록 복사해 둔다.
     */
    @Column(name = "hourly_rate_snapshot", nullable = false)
    private Integer hourlyRateSnapshot;

    @Column(nullable = false)
    private String currency;

    /** 총 금액 = 시급 스냅샷 × 시간 */
    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    /** 여행자가 남기는 요청 메시지 (선택) */
    @Column(columnDefinition = "text")
    private String message;

    /**
     * 예약한 서비스 카테고리 (ServiceCategory 키). 관광(requiresGuideLicense) 카테고리는 VERIFIED 가이드만 예약 가능.
     * 기존(이 기능 이전) 예약은 null — 조회만 가능하고 신규 예약은 필수.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "service_category")
    private com.guidematch.guide.ServiceCategory serviceCategory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 예약 채팅 읽음 상태 (통합 인박스 안읽음 배지용). 각자 마지막으로 대화방을 연 시각.
     * null = 아직 한 번도 안 읽음. Conversation의 traveler/guide_last_read_at 패턴과 동일.
     */
    @Column(name = "traveler_last_read_at")
    private Instant travelerLastReadAt;

    @Column(name = "guide_last_read_at")
    private Instant guideLastReadAt;

    /**
     * 만남 장소 (T1). 채팅에서 장소 카드를 "만남 장소로 지정"하면 저장된다. 전부 nullable(미지정 상태).
     * 주소는 한국어 유지(택시/지도 편의). 좌표는 예약 상세 지도 핀에 사용.
     */
    @Column(name = "meeting_place_name", columnDefinition = "text")
    private String meetingPlaceName;

    @Column(name = "meeting_place_address", columnDefinition = "text")
    private String meetingPlaceAddress;

    @Column(name = "meeting_place_lat")
    private Double meetingPlaceLat;

    @Column(name = "meeting_place_lng")
    private Double meetingPlaceLng;

    @Column(name = "meeting_place_url", columnDefinition = "text")
    private String meetingPlaceUrl;

    /**
     * 거절 알림 확인 여부 (#4). reject() 시 false로 세팅 → 여행자가 예약 목록을 열면 true.
     * null = 이 기능 이전 예약(배지 대상 아님).
     */
    @Column(name = "rejection_seen")
    private Boolean rejectionSeen;

    /**
     * 동행 예약의 카테고리별 요청 내용 (프론트 전용 JSON 규약, 백엔드는 불투명 텍스트).
     * placeCard 규약과 같은 철학 — 서버는 저장/반환만 한다. 투어 예약은 null.
     */
    @Column(name = "request_details", columnDefinition = "text")
    private String requestDetails;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = BookingStatus.REQUESTED;
    }

    protected Booking() {
    }

    public Booking(Long travelerId, Long guideProfileId, Instant startAt, Integer hours,
                   Integer hourlyRateSnapshot, String currency, Integer totalPrice, String message,
                   com.guidematch.guide.ServiceCategory serviceCategory) {
        this.travelerId = travelerId;
        this.guideProfileId = guideProfileId;
        this.startAt = startAt;
        this.hours = hours;
        this.hourlyRateSnapshot = hourlyRateSnapshot;
        this.currency = currency;
        this.totalPrice = totalPrice;
        this.message = message;
        this.serviceCategory = serviceCategory;
        this.status = BookingStatus.REQUESTED;
    }

    // --- 상태 변경 (규칙 포함) ---

    /** 가이드 수락: 요청 상태일 때만 가능 */
    public void accept() {
        requireStatus(BookingStatus.REQUESTED);
        this.status = BookingStatus.ACCEPTED;
    }

    /** 가이드 거절: 요청 상태일 때만 가능. 여행자에게 미확인 거절로 표시(배지). */
    public void reject() {
        requireStatus(BookingStatus.REQUESTED);
        this.status = BookingStatus.REJECTED;
        this.rejectionSeen = false;
    }

    /** 여행자 취소: 요청 또는 수락 상태일 때 가능 */
    public void cancel() {
        if (status != BookingStatus.REQUESTED && status != BookingStatus.ACCEPTED) {
            throw new IllegalArgumentException("취소할 수 없는 상태입니다.");
        }
        this.status = BookingStatus.CANCELLED;
    }

    /** 일정 종료 처리: 수락된 예약만 완료로 바꿀 수 있다 (이후 리뷰 가능) */
    public void complete() {
        requireStatus(BookingStatus.ACCEPTED);
        this.status = BookingStatus.COMPLETED;
    }

    private void requireStatus(BookingStatus expected) {
        if (this.status != expected) {
            throw new IllegalArgumentException("처리할 수 없는 예약 상태입니다.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTravelerId() {
        return travelerId;
    }

    public Long getGuideProfileId() {
        return guideProfileId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Integer getHours() {
        return hours;
    }

    public Integer getHourlyRateSnapshot() {
        return hourlyRateSnapshot;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getTotalPrice() {
        return totalPrice;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public com.guidematch.guide.ServiceCategory getServiceCategory() {
        return serviceCategory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTravelerLastReadAt() {
        return travelerLastReadAt;
    }

    public Instant getGuideLastReadAt() {
        return guideLastReadAt;
    }

    /** 예약 채팅방을 연 사람이 여행자면 여행자 읽음, 가이드면 가이드 읽음 시각을 지금으로. */
    public void markChatRead(boolean isTraveler) {
        Instant now = Instant.now();
        if (isTraveler) {
            this.travelerLastReadAt = now;
        } else {
            this.guideLastReadAt = now;
        }
    }

    public String getMeetingPlaceName() {
        return meetingPlaceName;
    }

    public String getMeetingPlaceAddress() {
        return meetingPlaceAddress;
    }

    public Double getMeetingPlaceLat() {
        return meetingPlaceLat;
    }

    public Double getMeetingPlaceLng() {
        return meetingPlaceLng;
    }

    public String getMeetingPlaceUrl() {
        return meetingPlaceUrl;
    }

    /** 만남 장소 지정/변경. 좌표(lat/lng)는 지도 핀에 필요하므로 필수, 나머지는 선택. */
    public void setMeetingPlace(String name, String address, Double lat, Double lng, String url) {
        this.meetingPlaceName = name;
        this.meetingPlaceAddress = address;
        this.meetingPlaceLat = lat;
        this.meetingPlaceLng = lng;
        this.meetingPlaceUrl = url;
    }

    public Boolean getRejectionSeen() { return rejectionSeen; }

    /** 여행자가 거절 알림을 확인함 (배지 클리어). */
    public void markRejectionSeen() { this.rejectionSeen = true; }

    public String getRequestDetails() { return requestDetails; }
    public void setRequestDetails(String requestDetails) { this.requestDetails = requestDetails; }
}
