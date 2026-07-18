package com.guidematch.admin;

import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import com.guidematch.user.UserRole;
import com.guidematch.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 관리자 회원 관리. 정지/해제만 허용(ADMIN 승격·삭제는 UI 비범위). */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UserRow(Long id, String email, String fullName, String nickname,
                          String role, String status, Instant createdAt, String suspendedReason) {}

    public record PageResult<T>(List<T> items, int page, int totalPages, long totalItems) {}

    @Transactional(readOnly = true)
    public PageResult<UserRow> list(String query, String status, int page, int size) {
        UserStatus statusFilter = null;
        if (status != null && !status.isBlank()) statusFilter = UserStatus.valueOf(status);
        Page<User> result = userRepository.search(
                query, statusFilter, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<UserRow> rows = result.getContent().stream().map(u -> new UserRow(
                u.getId(), u.getEmail(), u.getFullName(), u.getNickname(),
                u.getRole().name(), u.getStatus().name(), u.getCreatedAt(), u.getSuspendedReason()
        )).toList();
        return new PageResult<>(rows, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    /** 공유 정지 로직 — 회원관리 화면과 신고 검토 화면이 모두 호출. */
    @Transactional
    public void suspend(Long targetId, Long adminId, String reason) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        if (target.getId().equals(adminId)) {
            throw new IllegalArgumentException("자기 자신은 정지할 수 없습니다.");
        }
        if (target.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("관리자 계정은 정지할 수 없습니다.");
        }
        target.suspend(reason);
        userRepository.save(target);
    }

    @Transactional
    public void reactivate(Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        target.reactivate();
        userRepository.save(target);
    }
}
