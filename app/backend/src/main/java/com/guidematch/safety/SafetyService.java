package com.guidematch.safety;

import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 신고·차단 도메인 서비스.
 * 차단은 (blocker→blocked) 한 방향으로 저장하되, 숨김 효과는 양방향으로 적용한다.
 * 원격 DB(왕복 ~250ms)라서 목록/집계 쪽 차단 필터는 반드시 배치 조회로 쓴다.
 */
@Service
public class SafetyService {

    private static final Set<String> VALID_TARGET_TYPES =
            Set.of("USER", "CONVERSATION", "POST", "REVIEW", "BOOKING");
    private static final Set<String> VALID_REASONS =
            // IMPERSONATION: 예약한 가이드와 다른 사람이 나타남(계정·자격 대여 탐지 — 오프라인 갭을 닫는 신고).
            Set.of("SPAM", "HARASSMENT", "SCAM", "INAPPROPRIATE", "IMPERSONATION", "OTHER");

    private final BlockRepository blockRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public SafetyService(BlockRepository blockRepository,
                         ReportRepository reportRepository,
                         UserRepository userRepository) {
        this.blockRepository = blockRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    public record BlockedUser(Long userId, String name, String handle) {}
    public record ReportResult(Long id) {}

    /** 사용자 차단 (멱등) — 이미 차단돼 있으면 아무것도 안 하고 성공. */
    @Transactional
    public void block(Long blockerUserId, Long targetUserId) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("차단할 사용자를 지정하세요.");
        }
        if (blockerUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신은 차단할 수 없습니다.");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
        }
        // 중복 요청은 no-op — unique 제약 위반(500→401 위장)을 피하려 먼저 확인한다.
        if (blockRepository.existsByBlockerUserIdAndBlockedUserId(blockerUserId, targetUserId)) {
            return;
        }
        blockRepository.save(new Block(blockerUserId, targetUserId));
    }

    /** 차단 해제 (멱등) — 차단이 없어도 성공. */
    @Transactional
    public void unblock(Long blockerUserId, Long targetUserId) {
        blockRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, targetUserId)
                .ifPresent(blockRepository::delete);
    }

    /** 내가 차단한 사용자 목록 (관리 화면). */
    @Transactional(readOnly = true)
    public List<BlockedUser> myBlocks(Long userId) {
        List<Block> blocks = blockRepository.findByBlockerUserIdOrderByCreatedAtDesc(userId);
        if (blocks.isEmpty()) return List.of();
        List<Long> ids = blocks.stream().map(Block::getBlockedUserId).toList();
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> users.put(u.getId(), u));
        return blocks.stream()
                .map(b -> {
                    User u = users.get(b.getBlockedUserId());
                    return new BlockedUser(
                            b.getBlockedUserId(),
                            u != null ? u.getFullName() : "알 수 없음",
                            u != null ? u.getHandle() : "");
                })
                .toList();
    }

    /** 신고 접수 (같은 대상 중복 신고는 멱등 — 기존 신고 id 그대로 반환하지 않고 no-op 성공). */
    @Transactional
    public ReportResult report(Long reporterUserId, String targetType, Long targetId,
                               String reason, String detail) {
        String type = targetType == null ? "" : targetType.trim().toUpperCase();
        String rsn = reason == null ? "" : reason.trim().toUpperCase();
        if (!VALID_TARGET_TYPES.contains(type)) {
            throw new IllegalArgumentException("신고 대상 종류가 올바르지 않습니다.");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("신고 대상을 지정하세요.");
        }
        if (!VALID_REASONS.contains(rsn)) {
            throw new IllegalArgumentException("신고 사유가 올바르지 않습니다.");
        }
        // 같은 대상을 이미 신고했으면 조용히 성공 처리(중복 접수 방지).
        if (reportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(reporterUserId, type, targetId)) {
            return new ReportResult(null);
        }
        String trimmedDetail = (detail == null || detail.isBlank()) ? null : detail.trim();
        Report saved = reportRepository.save(new Report(reporterUserId, type, targetId, rsn, trimmedDetail));
        return new ReportResult(saved.getId());
    }
}
