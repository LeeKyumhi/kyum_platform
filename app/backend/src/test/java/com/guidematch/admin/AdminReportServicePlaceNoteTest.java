package com.guidematch.admin;

import com.guidematch.booking.BookingRepository;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.knowledge.PlaceNoteService;
import com.guidematch.safety.Report;
import com.guidematch.safety.ReportRepository;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 신고된 장소 노트(사진·한줄팁)를 관리자가 숨기는 조치. 사전 검수 큐가 없는 설계에서
 * 이 경로가 유일한 사후 안전장치라 실제로 hide()가 불리는지, 그리고 대상 종류가
 * 어긋난 신고로 엉뚱한 노트를 숨길 수 없는지를 고정한다.
 */
class AdminReportServicePlaceNoteTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final GuideProfileRepository guideProfileRepository = mock(GuideProfileRepository.class);
    private final ModerationService moderationService = mock(ModerationService.class);
    private final AdminUserService adminUserService = mock(AdminUserService.class);
    private final PlaceNoteService placeNoteService = mock(PlaceNoteService.class);

    private final AdminReportService service = new AdminReportService(
            reportRepository, userRepository, bookingRepository, guideProfileRepository,
            moderationService, adminUserService, placeNoteService);

    @Test
    void HIDE_PLACE_NOTE는_대상_노트를_숨기고_신고를_REVIEWED로_닫는다() {
        Report report = new Report(1L, "PLACE_NOTE", 42L, "INAPPROPRIATE", "부적절한 사진");
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        service.act(10L, 99L, "HIDE_PLACE_NOTE", null);

        verify(placeNoteService).hide(42L);
        assertThat(report.getStatus()).isEqualTo("REVIEWED");
    }

    @Test
    void 대상종류가_POST인_신고에_HIDE_PLACE_NOTE를_쓰면_거부된다() {
        Report report = new Report(1L, "POST", 42L, "INAPPROPRIATE", null);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.act(10L, 99L, "HIDE_PLACE_NOTE", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(placeNoteService, never()).hide(anyLong());
    }

    @Test
    void 이미_처리된_신고에는_조치할_수_없다() {
        Report report = new Report(1L, "PLACE_NOTE", 42L, "INAPPROPRIATE", null);
        report.markReviewed();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.act(10L, 99L, "HIDE_PLACE_NOTE", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(placeNoteService);
    }

    @Test
    void 알_수_없는_action은_여전히_거부된다() {
        Report report = new Report(1L, "PLACE_NOTE", 42L, "INAPPROPRIATE", null);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.act(10L, 99L, "SOMETHING_ELSE", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(placeNoteService);
    }
}
