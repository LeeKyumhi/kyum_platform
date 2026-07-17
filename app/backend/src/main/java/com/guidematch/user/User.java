package com.guidematch.user;

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
 * 사용자(회원) 한 명을 표현하는 엔티티.
 * 이 클래스가 DB의 "users" 테이블이 된다.
 *
 * - 한 계정이 여행자/가이드 둘 다 될 수 있으므로, 가입 단계에선 역할 구분이 없다.
 * - 가이드로 활동하려면 나중에 별도의 guide_profile을 추가로 만든다.
 */
@Entity
@Table(name = "users")
public class User {

    /**
     * 기본키(Primary Key) = 각 행을 구분하는 고유 번호.
     * IDENTITY = DB가 1, 2, 3... 자동으로 번호를 매긴다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 아이디로 쓰는 이메일. 중복 불가(unique). */
    @Column(nullable = false, unique = true)
    private String email;

    /** 비밀번호. 원문이 아니라 BCrypt로 암호화(해시)된 값을 저장한다. */
    @Column(nullable = false)
    private String password;

    /** 이름 */
    @Column(name = "full_name")
    private String fullName;

    /**
     * 공개 핸들(@아이디). 선택 입력, 대소문자 무시 유니크(애플리케이션에서 검증).
     * 비어 있으면 이메일 로컬파트로 폴백한다(getHandle).
     */
    @Column(unique = true, length = 20)
    private String nickname;

    /** 국적 (선택 입력) */
    private String nationality;

    /** 여행자 위치: 정형화된 도시 + 좌표 (선택). */
    @Column(name = "city")
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /** 성별 (선택, "male" / "female" / "other") */
    @Column(length = 10)
    private String gender;

    /** MBTI (선택, 예: ENFP) */
    @Column(length = 4)
    private String mbti;

    /** 관심사 (콤마로 구분된 키 목록) */
    @Column(columnDefinition = "text")
    private String interests;

    /**
     * 이메일 인증 완료 여부. nullable Boolean으로 둔다 — ddl-auto가 기존 행이 있는 테이블에
     * NOT NULL 컬럼을 추가하면 실패할 수 있으므로(HANDOFF.md 3-2), null은 "미인증"으로 취급한다.
     */
    @Column(name = "email_verified")
    private Boolean emailVerified;

    /**
     * 사용자 권한. nullable로 두어 기존 행은 null → getRole()에서 USER로 취급한다
     * (email_verified와 동일한 이유: ddl-auto가 NOT NULL 컬럼을 추가하면 기존 행에서 실패).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role;

    /** 가입 시각. 한 번 저장되면 수정되지 않는다(updatable=false). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 저장 직전에 자동으로 가입 시각을 채운다. */
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** JPA가 내부적으로 사용하는 기본 생성자 (직접 쓰지 않음). */
    protected User() {
    }

    /** 새 회원을 만들 때 사용하는 생성자. */
    public User(String email, String password, String fullName, String nationality) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.nationality = nationality;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    /** 비밀번호 재설정 — 반드시 BCrypt로 암호화된 값을 넘겨야 한다 (원문 저장 금지). */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 피드·프로필에 노출할 @핸들. 닉네임이 있으면 그것을, 없으면 이메일 로컬파트를 쓴다.
     * 이메일 로컬파트 폴백은 이메일 일부가 공개되므로 닉네임 설정을 권장한다.
     */
    public String getHandle() {
        if (nickname != null && !nickname.isBlank()) return nickname;
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    public String getNationality() {
        return nationality;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getCity() { return city; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public void updateLocation(String city, Double latitude, Double longitude) {
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getMbti() { return mbti; }
    public void setMbti(String mbti) { this.mbti = mbti; }

    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }

    public java.util.List<String> getInterestList() {
        if (interests == null || interests.isBlank()) return java.util.List.of();
        return java.util.List.of(interests.split(","));
    }

    public void setInterestList(java.util.List<String> list) {
        this.interests = (list == null || list.isEmpty()) ? null : String.join(",", list);
    }

    /** null(기존 회원 포함)은 미인증으로 취급한다. */
    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(emailVerified);
    }

    public void setEmailVerified(boolean verified) {
        this.emailVerified = verified;
    }

    /** null(기존 회원 포함)은 일반 USER로 취급한다. */
    public UserRole getRole() {
        return role == null ? UserRole.USER : role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isAdmin() {
        return getRole() == UserRole.ADMIN;
    }
}
