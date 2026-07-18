package com.guidematch.admin;

import com.guidematch.booking.Booking;
import com.guidematch.booking.BookingRepository;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.safety.Report;
import com.guidematch.safety.ReportRepository;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자용 신고 검토 로직. /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN으로 보호된다.
 * detect(신고)+prohibit(약관)에 이어 act(검토·조치) 화면의 백엔드 — 계정 대여 대응 루프를 닫는다.
 */
@Service
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final ModerationService moderationService;
    private final AdminUserService adminUserService;

    public AdminReportService(ReportRepository reportRepository,
                              UserRepository userRepository,
                              BookingRepository bookingRepository,
                              GuideProfileRepository guideProfileRepository,
                              ModerationService moderationService,
                              AdminUserService adminUserService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.guideProfileRepository = guideProfileRepository;
        this.moderationService = moderationService;
        this.adminUserService = adminUserService;
    }

    /** 관리자 신고 대기열 항목. targetSummary는 BOOKING(사칭 신고)일 때 대상 가이드 이름 등 조치용 문맥. */
    public record ReportItem(
            Long id,
            String reporterName,
            String reason,
            String targetType,
            Long targetId,
            String targetSummary,
            String detail,
            Instant createdAt
    ) {}

    /**
     * 미처리(OPEN) 신고 목록. 신고자 이름과 BOOKING 대상의 가이드 이름을 배치 조회로 붙인다(N+1 방지).
     */
    @Transactional(readOnly = true)
    public List<ReportItem> listOpen() {
        List<Report> reports = reportRepository.findByStatusOrderByCreatedAtDesc("OPEN");
        if (reports.isEmpty()) return List.of();

        // BOOKING 대상 → 예약 → 가이드 프로필 → 가이드 user id (조치용 문맥)
        List<Long> bookingIds = reports.stream()
                .filter(r -> "BOOKING".equals(r.getTargetType()))
                .map(Report::getTargetId).distinct().toList();
        Map<Long, Booking> bookings = new HashMap<>();
        bookingRepository.findAllById(bookingIds).forEach(b -> bookings.put(b.getId(), b));
        Map<Long, GuideProfile> profiles = new HashMap<>();
        guideProfileRepository.findAllById(
                bookings.values().stream().map(Booking::getGuideProfileId).distinct().toList()
        ).forEach(p -> profiles.put(p.getId(), p));

        // 이름이 필요한 모든 user id(신고자 + BOOKING 대상 가이드)를 한 번에 조회
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        reports.forEach(r -> userIds.add(r.getReporterUserId()));
        profiles.values().forEach(p -> userIds.add(p.getUserId()));
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getFullName()));

        return reports.stream().map(r -> {
            String targetSummary = null;
            if ("BOOKING".equals(r.getTargetType())) {
                Booking b = bookings.get(r.getTargetId());
                GuideProfile p = b != null ? profiles.get(b.getGuideProfileId()) : null;
                if (p != null) targetSummary = names.getOrDefault(p.getUserId(), "알 수 없음");
            }
            return new ReportItem(
                    r.getId(),
                    names.getOrDefault(r.getReporterUserId(), "알 수 없음"),
                    r.getReason(),
                    r.getTargetType(),
                    r.getTargetId(),
                    targetSummary,
                    r.getDetail(),
                    r.getCreatedAt()
            );
        }).toList();
    }

    /** 검토 완료(조치함) 처리. */
    @Transactional
    public void review(Long reportId) {
        getOpen(reportId).markReviewed();
    }

    /** 기각(문제 없음) 처리. */
    @Transactional
    public void dismiss(Long reportId) {
        getOpen(reportId).dismiss();
    }

    private Report getOpen(Long reportId) {
        Report r = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다."));
        if (!"OPEN".equals(r.getStatus())) {
            throw new IllegalArgumentException("이미 처리된 신고입니다.");
        }
        return r;
    }

    /**
     * 신고 대상에 실제 조치를 취하고 신고를 REVIEWED로 닫는다.
     * action: "HIDE_POST"(대상이 POST), "SUSPEND_USER"(대상이 USER 또는 BOOKING의 가이드).
     */
    @Transactional
    public void act(Long reportId, Long adminId, String action, String reason) {
        Report r = getOpen(reportId);
        switch (action) {
            case "HIDE_POST" -> {
                if (!"POST".equals(r.getTargetType())) {
                    throw new IllegalArgumentException("이 신고 대상은 게시글이 아닙니다.");
                }
                moderationService.hidePost(r.getTargetId());
            }
            case "SUSPEND_USER" -> {
                Long userId = resolveTargetUserId(r);
                adminUserService.suspend(userId, adminId, reason);
            }
            default -> throw new IllegalArgumentException("알 수 없는 조치입니다.");
        }
        r.markReviewed();
    }

    /** 신고 대상에서 정지할 user id를 뽑는다. USER면 그대로, BOOKING이면 가이드 user. */
    private Long resolveTargetUserId(Report r) {
        if ("USER".equals(r.getTargetType())) return r.getTargetId();
        if ("BOOKING".equals(r.getTargetType())) {
            Booking b = bookingRepository.findById(r.getTargetId())
                    .orElseThrow(() -> new IllegalArgumentException("대상 예약을 찾을 수 없습니다."));
            GuideProfile p = guideProfileRepository.findById(b.getGuideProfileId())
                    .orElseThrow(() -> new IllegalArgumentException("대상 가이드를 찾을 수 없습니다."));
            return p.getUserId();
        }
        throw new IllegalArgumentException("이 신고 대상에는 회원 정지를 적용할 수 없습니다.");
    }
}
