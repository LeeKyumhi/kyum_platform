package com.guidematch.admin;

import com.guidematch.booking.BookingRepository;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.knowledge.PlaceNote;
import com.guidematch.knowledge.PlaceNoteRepository;
import com.guidematch.knowledge.PlaceNoteService;
import com.guidematch.safety.Report;
import com.guidematch.safety.ReportRepository;
import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

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
    private final PlaceNoteRepository placeNoteRepository = mock(PlaceNoteRepository.class);

    private final AdminReportService service = new AdminReportService(
            reportRepository, userRepository, bookingRepository, guideProfileRepository,
            moderationService, adminUserService, placeNoteService, placeNoteRepository);

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
    void 노트가_이미_삭제됐으면_예외가_act밖으로_전파되고_신고는_OPEN으로_남는다() {
        // hide()가 조용히 성공하면 관리자는 숨겼다고 믿고 신고를 REVIEWED로 닫지만
        // 실제로는 아무것도 숨겨지지 않는다 — 이 기능의 유일한 안전장치가 무너진다.
        Report report = new Report(1L, "PLACE_NOTE", 42L, "INAPPROPRIATE", null);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));
        doThrow(new IllegalArgumentException("존재하지 않는 노트입니다.")).when(placeNoteService).hide(42L);

        assertThatThrownBy(() -> service.act(10L, 99L, "HIDE_PLACE_NOTE", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(report.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void 알_수_없는_action은_여전히_거부된다() {
        Report report = new Report(1L, "PLACE_NOTE", 42L, "INAPPROPRIATE", null);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.act(10L, 99L, "SOMETHING_ELSE", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(placeNoteService);
    }

    @Test
    void PLACE_NOTE_신고의_targetSummary가_장소명과_한줄팁으로_채워진다() {
        Report report = new Report(1L, "PLACE_NOTE", 42L, "INAPPROPRIATE", null);
        when(reportRepository.findByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of(report));
        PlaceNote note = new PlaceNote(17L, null, "덕수궁", 5L, null, null, "돌담길이 예뻐요");
        ReflectionTestUtils.setField(note, "id", 42L);
        when(placeNoteRepository.findAllById(List.of(42L))).thenReturn(List.of(note));

        List<AdminReportService.ReportItem> items = service.listOpen();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).targetSummary()).isEqualTo("덕수궁 · \"돌담길이 예뻐요\"");
    }

    @Test
    void 한줄팁이_없는_노트는_장소명만_targetSummary가_된다() {
        Report report = new Report(1L, "PLACE_NOTE", 42L, "INAPPROPRIATE", null);
        when(reportRepository.findByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of(report));
        PlaceNote note = new PlaceNote(17L, null, "덕수궁", 5L, "https://sb/x.jpg", "https://sb/x_thumb.jpg", null);
        ReflectionTestUtils.setField(note, "id", 42L);
        when(placeNoteRepository.findAllById(List.of(42L))).thenReturn(List.of(note));

        List<AdminReportService.ReportItem> items = service.listOpen();

        assertThat(items.get(0).targetSummary()).isEqualTo("덕수궁");
    }

    @Test
    void 신고된_노트가_이미_삭제됐으면_targetSummary는_null이고_목록은_안_죽는다() {
        Report report = new Report(1L, "PLACE_NOTE", 42L, "INAPPROPRIATE", null);
        when(reportRepository.findByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of(report));
        when(placeNoteRepository.findAllById(List.of(42L))).thenReturn(List.of());

        List<AdminReportService.ReportItem> items = service.listOpen();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).targetSummary()).isNull();
        assertThat(items.get(0).targetType()).isEqualTo("PLACE_NOTE");
    }
}
