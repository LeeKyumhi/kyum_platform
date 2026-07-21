package com.guidematch.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 리포지토리(Repository) = DB에 접근하는 창구.
 * JpaRepository를 상속하면 save(저장), findById(조회), delete(삭제) 등
 * 기본 CRUD 메서드를 코드 한 줄 없이 자동으로 제공받는다.
 *
 * 아래 두 메서드는 "메서드 이름"만 보고 스프링이 SQL을 자동 생성한다.
 *   findByEmail  -> SELECT * FROM users WHERE email = ?
 *   existsByEmail -> 해당 이메일이 존재하는지 true/false
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 닉네임 중복 검사 (대소문자 무시) */
    boolean existsByNicknameIgnoreCase(String nickname);

    /** 공개 프로필 핸들 해석 (대소문자 무시). 닉네임 미설정 사용자는 이 조회로 찾을 수 없음(알려진 한계). */
    Optional<User> findByNicknameIgnoreCase(String nickname);

    /** 어드민 대시보드: 최근 N일 신규 가입자 수 (count 쿼리, N+1 없음) */
    long countByCreatedAtAfter(Instant since);

    /**
     * 어드민 회원 목록 검색. status=NULL(기존 행)은 getStatus()에서 ACTIVE로 취급하므로,
     * status=ACTIVE 필터에도 반드시 포함시켜야 한다(그렇지 않으면 레거시 행이 목록에서 누락됨).
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:q IS NULL OR :q = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status IS NULL OR u.status = :status OR (:status = com.guidematch.user.UserStatus.ACTIVE AND u.status IS NULL)) " +
           "ORDER BY u.createdAt DESC")
    Page<User> search(@Param("q") String q,
                      @Param("status") UserStatus status,
                      Pageable pageable);
}
