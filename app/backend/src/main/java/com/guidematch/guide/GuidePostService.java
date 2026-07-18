package com.guidematch.guide;

import com.guidematch.geo.GoogleTranslateClient;
import com.guidematch.guide.dto.GuidePostResponse;
import com.guidematch.guide.dto.GuidePostWithGuideResponse;
import com.guidematch.storage.SupabaseStorageClient;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GuidePostService {

    private final GuidePostRepository postRepository;
    private final GuideProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageClient storageClient;
    private final PostInteractionService interactionService;
    private final GoogleTranslateClient googleClient;
    private final String bucket;

    public GuidePostService(GuidePostRepository postRepository,
                            GuideProfileRepository profileRepository,
                            UserRepository userRepository,
                            SupabaseStorageClient storageClient,
                            PostInteractionService interactionService,
                            GoogleTranslateClient googleClient,
                            @Value("${supabase.storage.credentials-bucket}") String bucket) {
        this.postRepository = postRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.storageClient = storageClient;
        this.interactionService = interactionService;
        this.googleClient = googleClient;
        this.bucket = bucket;
    }

    @Transactional
    public GuidePost create(Long userId, String content, String category, MultipartFile image) {
        // 가이드 프로필이 있으면 가이드 게시글로, 없으면 여행자 게시글로 저장 (둘 다 작성 가능).
        GuideProfile profile = profileRepository.findByUserId(userId).orElse(null);
        Long guideProfileId = profile != null ? profile.getId() : null;

        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            String owner = profile != null ? "guide-" + profile.getId() : "user-" + userId;
            String path = "posts/" + owner + "/" + UUID.randomUUID() + extension(image.getOriginalFilename());
            try {
                imageUrl = storageClient.uploadPublic(bucket, path, image.getBytes(), image.getContentType());
            } catch (IOException e) {
                throw new RuntimeException("이미지 업로드 중 오류가 발생했습니다.", e);
            }
        }

        String normalizedCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        return postRepository.save(new GuidePost(guideProfileId, userId, content, imageUrl, normalizedCategory));
    }

    @Transactional(readOnly = true)
    public List<GuidePostResponse> listByGuideProfile(Long guideProfileId, Long currentUserId) {
        List<GuidePost> posts = postRepository.findVisibleByGuideProfileIdOrderByCreatedAtDesc(guideProfileId);
        var stats = interactionService.statsFor(currentUserId, posts.stream().map(GuidePost::getId).toList());
        return posts.stream()
                .map(post -> {
                    var s = stats.getOrDefault(post.getId(), PostInteractionService.PostStats.EMPTY);
                    return GuidePostResponse.from(post, s.likeCount(), s.commentCount(), s.liked());
                })
                .toList();
    }

    /**
     * 전체 피드. DB가 원격이라 왕복 지연이 크므로 게시글별 개별 조회 대신
     * 프로필/사용자/상호작용 통계를 각각 한 번의 쿼리로 일괄 로딩한다.
     */
    @Transactional(readOnly = true)
    public List<GuidePostWithGuideResponse> listAll(Long currentUserId) {
        List<GuidePost> posts = postRepository.findVisibleOrderByCreatedAtDesc();
        if (posts.isEmpty()) return List.of();

        var stats = interactionService.statsFor(currentUserId, posts.stream().map(GuidePost::getId).toList());

        // 가이드 프로필 일괄 로딩 (languages fetch join 포함)
        List<Long> profileIds = posts.stream()
                .map(GuidePost::getGuideProfileId).filter(Objects::nonNull).distinct().toList();
        java.util.Map<Long, GuideProfile> profiles = new java.util.HashMap<>();
        if (!profileIds.isEmpty()) {
            profileRepository.findAllWithLanguagesByIdIn(profileIds)
                    .forEach(p -> profiles.put(p.getId(), p));
        }

        // 작성자 일괄 로딩 (가이드의 userId + 여행자 authorUserId 모두) — 이름과 @아이디용 이메일
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        profiles.values().forEach(p -> userIds.add(p.getUserId()));
        posts.stream().map(GuidePost::getAuthorUserId).filter(Objects::nonNull).forEach(userIds::add);
        java.util.Map<Long, User> users = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));
        }

        return posts.stream()
                .map(post -> {
                    var s = stats.getOrDefault(post.getId(), PostInteractionService.PostStats.EMPTY);
                    if (post.getGuideProfileId() != null) {
                        GuideProfile profile = profiles.get(post.getGuideProfileId());
                        if (profile == null) return null;
                        List<String> languages = profile.getLanguages().stream()
                                .map(GuideLanguage::getLanguage).toList();
                        User author = users.get(profile.getUserId());
                        return GuidePostWithGuideResponse.fromGuide(
                                post, profile, profile.getUserId(),
                                author != null ? author.getFullName() : "Unknown",
                                handleOf(author), languages,
                                s.likeCount(), s.commentCount(), s.liked());
                    }
                    if (post.getAuthorUserId() == null) return null;
                    User author = users.get(post.getAuthorUserId());
                    return GuidePostWithGuideResponse.fromTraveler(
                            post, post.getAuthorUserId(),
                            author != null ? author.getFullName() : "Unknown",
                            handleOf(author),
                            s.likeCount(), s.commentCount(), s.liked());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** 인스타그램식 @아이디 — 닉네임 우선, 없으면 이메일 로컬파트 (User.getHandle) */
    private static String handleOf(User user) {
        return user == null ? null : user.getHandle();
    }

    /** 내 게시글 전체 (가이드/여행자 공통 — 홈 프로필 그리드용) */
    @Transactional(readOnly = true)
    public List<GuidePostResponse> listMine(Long userId) {
        List<GuidePost> posts = postRepository.findAllByAuthor(userId);
        var stats = interactionService.statsFor(userId, posts.stream().map(GuidePost::getId).toList());
        return posts.stream()
                .map(post -> {
                    var s = stats.getOrDefault(post.getId(), PostInteractionService.PostStats.EMPTY);
                    return GuidePostResponse.from(post, s.likeCount(), s.commentCount(), s.liked());
                })
                .toList();
    }

    /** 게시글 조회수 1 증가 (피드 노출 임프레션). 비로그인도 호출 가능. */
    @Transactional
    public void recordView(Long postId) {
        postRepository.incrementViewCount(postId);
    }

    /**
     * 게시글 본문을 지정 언어로 번역 (공개 콘텐츠 — 참여자 검증 불필요).
     * 게시글은 대부분 고유 문장이라 채팅과 동일하게 DB 캐시 없이 Google API를 직접 호출한다.
     */
    @Transactional(readOnly = true)
    public String translate(Long postId, String uiLang) {
        GuidePost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        String content = post.getContent();
        if (content == null || content.isBlank()) {
            return content;
        }
        if (!googleClient.isEnabled()) {
            throw new IllegalArgumentException("번역 기능을 사용할 수 없습니다.");
        }

        String target = switch (uiLang == null ? "ko" : uiLang) {
            case "en" -> "en";
            case "zh" -> "zh-CN";
            default   -> "ko";
        };

        List<String> result = googleClient.translate(List.of(content), null, target);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("번역에 실패했습니다.");
        }
        return result.get(0);
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        GuidePost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        boolean owns;
        if (post.getAuthorUserId() != null) {
            owns = post.getAuthorUserId().equals(userId);
        } else {
            // 레거시 행(작성 당시 author_user_id 없음): guide_profile 경유로 소유권 확인
            owns = post.getGuideProfileId() != null
                    && profileRepository.findByUserId(userId)
                        .map(p -> p.getId().equals(post.getGuideProfileId()))
                        .orElse(false);
        }

        if (!owns) {
            throw new SecurityException("본인 게시글만 삭제할 수 있습니다.");
        }

        postRepository.delete(post);
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
