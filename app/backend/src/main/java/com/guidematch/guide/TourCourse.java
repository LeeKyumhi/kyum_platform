package com.guidematch.guide;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 가이드가 등록하는 고정 투어 코스 상품.
 * 시급 × 시간 커스텀 예약과 달리, 미리 정해둔 코스·소요시간·1인 가격으로 판매한다.
 * (예: "성수동 카페 투어 · 3시간 · 1인 50,000원 · 최대 4명")
 */
@Entity
@Table(name = "tour_courses")
public class TourCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guide_profile_id", nullable = false)
    private Long guideProfileId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** 코스가 진행되는 도시 (KoreanCity key 기준) */
    @Column
    private String city;

    /**
     * 서비스 카테고리 (ServiceCategory 키). 관광(requiresGuideLicense) 카테고리 코스는 VERIFIED 가이드만 생성 가능.
     * 기존 코스는 null(미분류) — 공개 탐색에서 숨기고 소유자에게 재분류를 안내한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "service_category")
    private ServiceCategory serviceCategory;

    /** 소요 시간 (시간 단위) */
    @Column(name = "duration_hours", nullable = false)
    private Integer durationHours;

    /** 1인 가격 */
    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private String currency;

    /** 최대 참여 인원 */
    @Column(name = "max_people", nullable = false)
    private Integer maxPeople;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 코스 동선 (순서가 있는 장소 스냅샷). 프론트에서 TripMap 핀+폴리라인 렌더링에 사용.
     * listAll/listMine/listByGuide는 여러 코스를 한 번에 응답으로 매핑하므로,
     * @BatchSize 없이는 코스마다 별도 SELECT가 나가는 N+1이 발생한다 — 배치 크기로 IN절 일괄 로딩.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_course_id")
    @OrderBy("sortOrder asc")
    @BatchSize(size = 100)
    private List<TourCourseWaypoint> waypoints = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected TourCourse() {}

    public TourCourse(Long guideProfileId, String title, String description, String city,
                      Integer durationHours, Integer price, String currency,
                      Integer maxPeople, String imageUrl, ServiceCategory serviceCategory) {
        this.guideProfileId = guideProfileId;
        this.title = title;
        this.description = description;
        this.city = city;
        this.durationHours = durationHours;
        this.price = price;
        this.currency = currency;
        this.maxPeople = maxPeople;
        this.imageUrl = imageUrl;
        this.serviceCategory = serviceCategory;
    }

    /** 제목/설명/도시/시간/가격/인원/이미지/카테고리 갱신 (currency는 최초 등록 시 스냅샷된 값 유지). */
    public void updateMeta(String title, String description, String city, Integer durationHours,
                           Integer price, Integer maxPeople, String imageUrl, ServiceCategory serviceCategory) {
        this.title = title;
        this.description = description;
        this.city = city;
        this.durationHours = durationHours;
        this.price = price;
        this.maxPeople = maxPeople;
        this.imageUrl = imageUrl;
        this.serviceCategory = serviceCategory;
    }

    /**
     * 동선 전체 교체.
     * orphanRemoval 컬렉션은 참조를 재할당하면 Hibernate가 예외를 던지므로,
     * 반드시 기존 리스트를 비우고(clear) 새 원소를 추가(addAll)하는 인-플레이스 방식으로 갱신한다.
     */
    public void replaceWaypoints(List<TourCourseWaypoint> newWaypoints) {
        this.waypoints.clear();
        if (newWaypoints != null) this.waypoints.addAll(newWaypoints);
    }

    public Long getId() { return id; }
    public Long getGuideProfileId() { return guideProfileId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCity() { return city; }
    public Integer getDurationHours() { return durationHours; }
    public Integer getPrice() { return price; }
    public String getCurrency() { return currency; }
    public Integer getMaxPeople() { return maxPeople; }
    public String getImageUrl() { return imageUrl; }
    public ServiceCategory getServiceCategory() { return serviceCategory; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public List<TourCourseWaypoint> getWaypoints() { return waypoints; }
}
