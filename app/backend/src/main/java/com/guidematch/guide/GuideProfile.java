package com.guidematch.guide;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** MBTI 유형 (예: "ENFP"). 선택사항. */
    @Column(length = 4)
    private String mbti;

    /** 관심사 목록. 쉼표로 구분된 키 목록 (예: "FOOD,CAFE,KPOP"). */
    @Column(columnDefinition = "text")
    private String interests;

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

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getHeadline() { return headline; }
    public String getIntroduction() { return introduction; }
    public Integer getHourlyRate() { return hourlyRate; }
    public String getCurrency() { return currency; }
    public String getRegion() { return region; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getMbti() { return mbti; }
    public void setMbti(String mbti) { this.mbti = mbti; }
    public Instant getCreatedAt() { return createdAt; }
    public List<GuideLanguage> getLanguages() { return languages; }
}
