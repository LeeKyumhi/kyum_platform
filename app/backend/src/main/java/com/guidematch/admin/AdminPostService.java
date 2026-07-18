package com.guidematch.admin;

import com.guidematch.guide.GuidePost;
import com.guidematch.guide.GuidePostRepository;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 관리자 게시글 모더레이션 목록. 조치는 ModerationService로 위임. */
@Service
public class AdminPostService {

    private final GuidePostRepository guidePostRepository;
    private final UserRepository userRepository;

    public AdminPostService(GuidePostRepository guidePostRepository, UserRepository userRepository) {
        this.guidePostRepository = guidePostRepository;
        this.userRepository = userRepository;
    }

    public record PostRow(Long id, Long authorUserId, String authorName, String content,
                          String imageUrl, boolean hidden, Instant createdAt) {}

    @Transactional(readOnly = true)
    public AdminUserService.PageResult<PostRow> list(boolean onlyHidden, int page, int size) {
        Page<GuidePost> result = guidePostRepository.adminList(
                onlyHidden, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));

        List<Long> authorIds = result.getContent().stream()
                .map(GuidePost::getAuthorUserId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> names.put(u.getId(), u.getFullName()));

        List<PostRow> rows = result.getContent().stream().map(p -> new PostRow(
                p.getId(), p.getAuthorUserId(),
                p.getAuthorUserId() != null ? names.getOrDefault(p.getAuthorUserId(), "알 수 없음") : "알 수 없음",
                p.getContent(), p.getImageUrl(), p.isHidden(), p.getCreatedAt()
        )).toList();
        return new AdminUserService.PageResult<>(rows, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }
}
