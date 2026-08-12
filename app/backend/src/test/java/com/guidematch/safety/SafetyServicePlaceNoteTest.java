package com.guidematch.safety;

import com.guidematch.user.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 장소 노트(사진·한줄팁) 신고. 사전 검수 큐 없이 시작하는 판단의 전제가 <b>신고가 실제로 접수된다</b>는 것이다 —
 * 이게 안 되면 사후 대응이라는 설계 자체가 빈말이 된다.
 */
class SafetyServicePlaceNoteTest {

    private final BlockRepository blockRepo = mock(BlockRepository.class);
    private final ReportRepository reportRepo = mock(ReportRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);

    private final SafetyService service = new SafetyService(blockRepo, reportRepo, userRepo);

    @Test
    void PLACE_NOTE를_신고할_수_있다() {
        when(reportRepo.existsByReporterUserIdAndTargetTypeAndTargetId(anyLong(), any(), anyLong()))
                .thenReturn(false);
        when(reportRepo.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        SafetyService.ReportResult result = service.report(3L, "PLACE_NOTE", 5L, "INAPPROPRIATE", "부적절한 사진");

        assertThat(result).isNotNull();
        verify(reportRepo).save(any(Report.class));
    }

    @Test
    void 없는_대상종류는_여전히_거부된다() {
        assertThatThrownBy(() -> service.report(3L, "SOMETHING_ELSE", 5L, "SPAM", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
