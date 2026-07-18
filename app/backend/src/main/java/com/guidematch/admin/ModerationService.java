package com.guidematch.admin;

import com.guidematch.guide.GuidePost;
import com.guidematch.guide.GuidePostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글 모더레이션 공유 로직 — 브라우징 화면과 신고 검토 화면이 모두 호출한다. */
@Service
public class ModerationService {

    private final GuidePostRepository guidePostRepository;

    public ModerationService(GuidePostRepository guidePostRepository) {
        this.guidePostRepository = guidePostRepository;
    }

    @Transactional
    public void hidePost(Long postId) {
        GuidePost p = guidePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        p.hide();
        guidePostRepository.save(p);
    }

    @Transactional
    public void unhidePost(Long postId) {
        GuidePost p = guidePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        p.unhide();
        guidePostRepository.save(p);
    }

    @Transactional
    public void deletePost(Long postId) {
        if (!guidePostRepository.existsById(postId)) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }
        guidePostRepository.deleteById(postId);
    }
}
