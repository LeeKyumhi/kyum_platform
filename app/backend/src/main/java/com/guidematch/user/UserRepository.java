package com.guidematch.user;

import org.springframework.data.jpa.repository.JpaRepository;

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

    /** 어드민 대시보드: 최근 N일 신규 가입자 수 (count 쿼리, N+1 없음) */
    long countByCreatedAtAfter(Instant since);
}
