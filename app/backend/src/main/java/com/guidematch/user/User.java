package com.guidematch.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    /** 국적 (선택 입력) */
    private String nationality;

    /** MBTI (선택, 예: ENFP) */
    @Column(length = 4)
    private String mbti;

    /** 관심사 (콤마로 구분된 키 목록) */
    @Column(columnDefinition = "text")
    private String interests;

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

    public String getFullName() {
        return fullName;
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
}
