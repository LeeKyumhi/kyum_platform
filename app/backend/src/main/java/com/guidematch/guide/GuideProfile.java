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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "guide_profiles")
public class GuideProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String headline;

    @Column(columnDefinition = "text")
    private String introduction;

    @Column(name = "hourly_rate", nullable = false)
    private Integer hourlyRate;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String region;

    /** 정형화된 도시 (신규 표준 필드). 기존 free-text region은 레거시로 유지. */
    @Column(name = "city")
    private String city;

    /** 위치 좌표 (GPS/거리순 정렬용). 선택. */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** 즉시 예약(예약 요청 없이 바로 확정). null은 false로 취급. */
    @Column(name = "instant_booking")
    private Boolean instantBooking = false;

    /** MBTI 유형 (예: "ENFP"). 선택사항. */
    @Column(length = 4)
    private String mbti;

    /** 관심사 목록. 쉼표로 구분된 키 목록 (예: "FOOD,CAFE,KPOP"). */
    @Column(columnDefinition = "text")
    private String interests;

    /**
     * 제공 서비스 카테고리. 쉼표로 구분된 ServiceCategory 키 목록 (예: "TOUR_GUIDE,DINING_COMPANION").
     * 관광(TOUR_GUIDE 등 requiresGuideLicense) 카테고리는 VERIFIED 가이드만 담을 수 있다(서비스단 검증).
     */
    @Column(name = "service_categories", columnDefinition = "text")
    private String serviceCategories;

    /**
     * 관광통역안내사 자격 인증 상태 (배지·관광 카테고리 게이팅용 비정규화 필드).
     * 진실의 원천은 GuideVerification이지만, 목록/배지 조회 때 조인을 피하려 여기 복사한다.
     * null(기존 가이드 포함)은 NONE(미인증)으로 취급한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    private VerificationStatus verificationStatus;

    /**
     * 호스트 약관(계정·자격 대여 금지, 비관광 호스트의 관광안내 금지, 명의자 전책임) 동의 시각.
     * 온보딩 시 동의 기록 — 계정 대여 등 분쟁 시 "플랫폼이 금지·고지했다"는 방어 근거.
     */
    @Column(name = "host_terms_agreed_at")
    private Instant hostTermsAgreedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_profile_id")
    private List<GuideLanguage> languages = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected GuideProfile() {}

    public GuideProfile(Long userId, String headline, String introduction,
                        Integer hourlyRate, String currency, String region) {
        this.userId = userId;
        this.headline = headline;
        this.introduction = introduction;
        this.hourlyRate = hourlyRate;
        this.currency = currency;
        this.region = region;
    }

    public void addLanguage(GuideLanguage language) {
        this.languages.add(language);
    }

    public List<String> getInterestList() {
        if (interests == null || interests.isBlank()) return List.of();
        return List.of(interests.split(","));
    }

    public void setInterestList(List<String> list) {
        this.interests = (list == null || list.isEmpty()) ? null : String.join(",", list);
    }

    public List<String> getServiceCategoryList() {
        if (serviceCategories == null || serviceCategories.isBlank()) return List.of();
        return List.of(serviceCategories.split(","));
    }

    public void setServiceCategoryList(List<String> list) {
        this.serviceCategories = (list == null || list.isEmpty()) ? null : String.join(",", list);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getHeadline() { return headline; }
    public String getIntroduction() { return introduction; }
    public Integer getHourlyRate() { return hourlyRate; }
    public String getCurrency() { return currency; }
    public String getRegion() { return region; }
    public String getCity() { return city; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    /** 도시/좌표 갱신. region 컬럼은 레거시 표시용으로 함께 채워둔다. */
    public void updateLocation(String city, Double latitude, Double longitude) {
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        if (city != null && !city.isBlank()) this.region = city;
    }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    /** null은 false로 취급 (신규 컬럼이라 기존 행은 null일 수 있음). */
    public boolean isInstantBooking() { return Boolean.TRUE.equals(instantBooking); }
    public void setInstantBooking(boolean instantBooking) { this.instantBooking = instantBooking; }
    public String getMbti() { return mbti; }
    public void setMbti(String mbti) { this.mbti = mbti; }
    /** null(기존 가이드 포함)은 NONE(미인증)으로 취급한다. */
    public VerificationStatus getVerificationStatus() {
        return verificationStatus == null ? VerificationStatus.NONE : verificationStatus;
    }
    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
    public boolean isVerified() {
        return getVerificationStatus() == VerificationStatus.VERIFIED;
    }
    public Instant getHostTermsAgreedAt() { return hostTermsAgreedAt; }
    public void agreeHostTerms() { this.hostTermsAgreedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public List<GuideLanguage> getLanguages() { return languages; }
}
