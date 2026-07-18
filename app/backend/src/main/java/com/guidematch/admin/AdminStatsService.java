package com.guidematch.admin;

import com.guidematch.booking.BookingRepository;
import com.guidematch.booking.BookingStatus;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.GuideVerificationRepository;
import com.guidematch.guide.VerificationStatus;
import com.guidematch.safety.ReportRepository;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 관리자 대시보드 통계. 모든 값은 count 쿼리로만 계산(N+1 없음). */
@Service
public class AdminStatsService {

    private final UserRepository userRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final BookingRepository bookingRepository;
    private final GuideVerificationRepository verificationRepository;
    private final ReportRepository reportRepository;

    public AdminStatsService(UserRepository userRepository,
                             GuideProfileRepository guideProfileRepository,
                             BookingRepository bookingRepository,
                             GuideVerificationRepository verificationRepository,
                             ReportRepository reportRepository) {
        this.userRepository = userRepository;
        this.guideProfileRepository = guideProfileRepository;
        this.bookingRepository = bookingRepository;
        this.verificationRepository = verificationRepository;
        this.reportRepository = reportRepository;
    }

    public record StatsDto(
            long totalUsers,
            long totalGuides,
            long newUsers7d,
            long bookingsRequested,
            long bookingsAccepted,
            long bookingsCompleted,
            long pendingVerifications,
            long openReports
    ) {}

    @Transactional(readOnly = true)
    public StatsDto load() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        return new StatsDto(
                userRepository.count(),
                guideProfileRepository.count(),
                userRepository.countByCreatedAtAfter(weekAgo),
                bookingRepository.countByStatus(BookingStatus.REQUESTED),
                bookingRepository.countByStatus(BookingStatus.ACCEPTED),
                bookingRepository.countByStatus(BookingStatus.COMPLETED),
                verificationRepository.countByStatus(VerificationStatus.PENDING),
                reportRepository.countByStatus("OPEN")
        );
    }
}
