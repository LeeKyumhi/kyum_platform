package com.guidematch.itinerary;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 여행자/가이드가 만드는 개인 여행 일정.
 * 도시 + 기간 + 일자별 장소(ItineraryItem) 모음. 소유자(ownerId)만 접근.
 */
@Entity
@Table(name = "itineraries")
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "title", nullable = false)
    private String title;

    /** 표준 도시 키 (예: "Seoul"). 선택. */
    @Column(name = "city")
    private String city;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id")
    @OrderBy("dayIndex asc, sortOrder asc")
    private List<ItineraryItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Itinerary() {}

    public Itinerary(Long ownerId, String title, String city, LocalDate startDate, LocalDate endDate) {
        this.ownerId = ownerId;
        this.title = title;
        this.city = city;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 메타(제목/도시/기간) 갱신. */
    public void updateMeta(String title, String city, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.city = city;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 아이템 전체 교체.
     * orphanRemoval 컬렉션은 참조를 재할당하면 Hibernate가 예외를 던지므로,
     * 반드시 기존 리스트를 비우고(clear) 새 원소를 추가(addAll)하는 인-플레이스 방식으로 갱신한다.
     */
    public void replaceItems(List<ItineraryItem> newItems) {
        this.items.clear();
        if (newItems != null) this.items.addAll(newItems);
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getCity() { return city; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ItineraryItem> getItems() { return items; }
}
