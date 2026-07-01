package com.guidematch.guide;

import com.guidematch.guide.dto.AvailableSlotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AvailableSlotService {

    private final AvailableSlotRepository slotRepo;
    private final GuideProfileRepository profileRepo;

    public AvailableSlotService(AvailableSlotRepository slotRepo, GuideProfileRepository profileRepo) {
        this.slotRepo = slotRepo;
        this.profileRepo = profileRepo;
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> listForGuide(Long guideProfileId) {
        return slotRepo.findByGuideProfileIdAndStartAtAfterOrderByStartAtAsc(
                guideProfileId, LocalDateTime.now().minusHours(1)
        ).stream().map(AvailableSlotResponse::from).toList();
    }

    @Transactional
    public AvailableSlotResponse addSlot(Long userId, LocalDateTime startAt, LocalDateTime endAt) {
        if (endAt.isBefore(startAt) || endAt.equals(startAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각 이후여야 합니다.");
        }
        GuideProfile profile = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다."));
        AvailableSlot slot = slotRepo.save(new AvailableSlot(profile.getId(), startAt, endAt));
        return AvailableSlotResponse.from(slot);
    }

    @Transactional
    public void deleteSlot(Long userId, Long slotId) {
        GuideProfile profile = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다."));
        AvailableSlot slot = slotRepo.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("슬롯을 찾을 수 없습니다."));
        if (!slot.getGuideProfileId().equals(profile.getId())) {
            throw new SecurityException("본인 슬롯만 삭제할 수 있습니다.");
        }
        slotRepo.delete(slot);
    }
}
