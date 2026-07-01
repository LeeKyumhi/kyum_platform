package com.guidematch.guide;

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
    private final String bucket;

    public GuidePostService(GuidePostRepository postRepository,
                            GuideProfileRepository profileRepository,
                            UserRepository userRepository,
                            SupabaseStorageClient storageClient,
                            PostInteractionService interactionService,
                            @Value("${supabase.storage.credentials-bucket}") String bucket) {
        this.postRepository = postRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.storageClient = storageClient;
        this.interactionService = interactionService;
        this.bucket = bucket;
    }

    @Transactional
    public GuidePost create(Long userId, String content, MultipartFile image) {
        GuideProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다."));

        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            String path = "posts/guide-" + profile.getId() + "/" + UUID.randomUUID() + extension(image.getOriginalFilename());
            try {
                imageUrl = storageClient.uploadPublic(bucket, path, image.getBytes(), image.getContentType());
            } catch (IOException e) {
                throw new RuntimeException("이미지 업로드 중 오류가 발생했습니다.", e);
            }
        }

        return postRepository.save(new GuidePost(profile.getId(), content, imageUrl));
    }

    @Transactional(readOnly = true)
    public List<GuidePostResponse> listByGuideProfile(Long guideProfileId, Long currentUserId) {
        return postRepository.findByGuideProfileIdOrderByCreatedAtDesc(guideProfileId).stream()
                .map(post -> GuidePostResponse.from(
                        post,
                        interactionService.likeCount(post.getId()),
                        interactionService.commentCount(post.getId()),
                        interactionService.isLiked(currentUserId, post.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GuidePostWithGuideResponse> listAll(Long currentUserId) {
        return postRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(post -> profileRepository.findById(post.getGuideProfileId())
                        .map(profile -> {
                            String name = userRepository.findById(profile.getUserId())
                                    .map(User::getFullName).orElse("Unknown");
                            return GuidePostWithGuideResponse.from(
                                    post, profile, name,
                                    interactionService.likeCount(post.getId()),
                                    interactionService.commentCount(post.getId()),
                                    interactionService.isLiked(currentUserId, post.getId())
                            );
                        }).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        GuideProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("가이드 프로필이 없습니다."));

        GuidePost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (!post.getGuideProfileId().equals(profile.getId())) {
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
