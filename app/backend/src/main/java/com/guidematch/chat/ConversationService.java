package com.guidematch.chat;

import com.guidematch.chat.dto.ConversationMessageResponse;
import com.guidematch.chat.dto.ConversationResponse;
import com.guidematch.geo.GoogleTranslateClient;
import com.guidematch.guide.GuideProfile;
import com.guidematch.guide.GuideProfileRepository;
import com.guidematch.guide.GuideProfileService;
import com.guidematch.safety.BlockRepository;
import com.guidematch.user.User;
import com.guidematch.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 예약 전 1:1 문의 대화. 예약 채팅(MessageService)과 같은 UX를 예약 없이 제공한다.
 * 원격 DB(왕복 ~250ms)이므로 목록 조회는 반드시 일괄(batch) 쿼리로 구성한다.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final GuideProfileService guideProfileService;
    private final GuideProfileRepository guideProfileRepository;
    private final UserRepository userRepository;
    private final GoogleTranslateClient googleClient;
    private final BlockRepository blockRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               GuideProfileService guideProfileService,
                               GuideProfileRepository guideProfileRepository,
                               UserRepository userRepository,
                               GoogleTranslateClient googleClient,
                               BlockRepository blockRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.guideProfileService = guideProfileService;
        this.guideProfileRepository = guideProfileRepository;
        this.userRepository = userRepository;
        this.googleClient = googleClient;
        this.blockRepository = blockRepository;
    }

    /** 가이드에게 보내는 대화방을 얻는다 (없으면 생성). 예약 여부와 무관. */
    @Transactional
    public ConversationResponse getOrCreate(Long userId, Long guideProfileId) {
        GuideProfile guide = guideProfileService.getById(guideProfileId);
        if (guide.getUserId().equals(userId)) {
            throw new IllegalArgumentException("자기 자신에게는 메시지를 보낼 수 없습니다.");
        }
        if (blockRepository.existsBetween(userId, guide.getUserId())) {
            throw new IllegalArgumentException("차단된 사용자와는 대화를 시작할 수 없습니다.");
        }
        Conversation conversation = conversationRepository
                .findByTravelerUserIdAndGuideProfileId(userId, guideProfileId)
                .orElseGet(() -> conversationRepository.save(
                        new Conversation(userId, guideProfileId, guide.getUserId())));
        Map<Long, User> users = new HashMap<>();
        userRepository.findById(guide.getUserId()).ifPresent(u -> users.put(u.getId(), u));
        return toResponse(conversation, userId, users, Map.of(guideProfileId, guide));
    }

    /** 내 대화 목록 (여행자로 시작한 방 + 가이드로 받은 방 모두) */
    @Transactional(readOnly = true)
    public List<ConversationResponse> myConversations(Long userId) {
        List<Conversation> conversations = conversationRepository.findMine(userId);
        if (conversations.isEmpty()) return List.of();

        // 차단 관계(양방향)에 있는 상대와의 대화는 목록에서 숨긴다 — 배치 1회 조회.
        Set<Long> blockedPeers = new HashSet<>(blockRepository.relatedUserIds(userId));
        if (!blockedPeers.isEmpty()) {
            conversations = conversations.stream()
                    .filter(c -> {
                        Long peer = c.getTravelerUserId().equals(userId)
                                ? c.getGuideUserId() : c.getTravelerUserId();
                        return !blockedPeers.contains(peer);
                    })
                    .toList();
            if (conversations.isEmpty()) return List.of();
        }

        // 상대방 표시 정보를 두 번의 일괄 조회로 준비 (가이드 프로필 + 상대 사용자 이름)
        Set<Long> profileIds = new HashSet<>();
        Set<Long> otherUserIds = new HashSet<>();
        for (Conversation c : conversations) {
            if (c.getTravelerUserId().equals(userId)) {
                profileIds.add(c.getGuideProfileId());
                otherUserIds.add(c.getGuideUserId());
            } else {
                otherUserIds.add(c.getTravelerUserId());
            }
        }
        Map<Long, GuideProfile> profiles = new HashMap<>();
        if (!profileIds.isEmpty()) {
            guideProfileRepository.findAllById(profileIds).forEach(p -> profiles.put(p.getId(), p));
        }
        Map<Long, User> users = new HashMap<>();
        if (!otherUserIds.isEmpty()) {
            userRepository.findAllById(otherUserIds).forEach(u -> users.put(u.getId(), u));
        }

        return conversations.stream()
                .map(c -> toResponse(c, userId, users, profiles))
                .toList();
    }

    /** 방 하나 조회 (참여자만) — 대화 화면 헤더용 */
    @Transactional(readOnly = true)
    public ConversationResponse get(Long userId, Long conversationId) {
        Conversation c = assertParticipant(userId, conversationId);
        Long otherUserId = c.getTravelerUserId().equals(userId) ? c.getGuideUserId() : c.getTravelerUserId();
        Map<Long, User> users = new HashMap<>();
        userRepository.findById(otherUserId).ifPresent(u -> users.put(u.getId(), u));
        Map<Long, GuideProfile> profiles = new HashMap<>();
        if (c.getTravelerUserId().equals(userId)) {
            profiles.put(c.getGuideProfileId(), guideProfileService.getById(c.getGuideProfileId()));
        }
        return toResponse(c, userId, users, profiles);
    }

    @Transactional
    public ConversationMessageResponse send(Long userId, Long conversationId, String content) {
        Conversation conversation = assertParticipant(userId, conversationId);
        Long otherUserId = conversation.getTravelerUserId().equals(userId)
                ? conversation.getGuideUserId() : conversation.getTravelerUserId();
        if (blockRepository.existsBetween(userId, otherUserId)) {
            throw new IllegalArgumentException("차단된 사용자에게는 메시지를 보낼 수 없습니다.");
        }
        ConversationMessage saved = messageRepository.save(
                new ConversationMessage(conversationId, userId, content));
        conversation.touchLastMessage(Previews.clip(content));
        String senderName = userRepository.findById(userId).map(User::getFullName).orElse("알 수 없음");
        return ConversationMessageResponse.of(saved, senderName);
    }

    @Transactional
    public List<ConversationMessageResponse> history(Long userId, Long conversationId) {
        Conversation conversation = assertParticipant(userId, conversationId);
        // 대화방을 열었으니 여기까지 읽음 처리 (안읽음 배지 반영)
        conversation.markRead(userId);
        // 참여자는 두 명뿐 — 이름은 일괄 조회 한 번이면 된다
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(List.of(conversation.getTravelerUserId(), conversation.getGuideUserId()))
                .forEach(u -> names.put(u.getId(), u.getFullName()));
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(m -> ConversationMessageResponse.of(m, names.getOrDefault(m.getSenderId(), "알 수 없음")))
                .toList();
    }

    /** 내 안읽은 DM 총 개수 — 사이드바 배지 */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return conversationRepository.unreadCount(userId);
    }

    /** 메시지 번역 — 예약 채팅과 동일하게 자동 감지 + 캐시 미사용 */
    @Transactional(readOnly = true)
    public String translate(Long userId, Long conversationId, Long messageId, String uiLang) {
        assertParticipant(userId, conversationId);
        ConversationMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
        if (!message.getConversationId().equals(conversationId)) {
            throw new IllegalArgumentException("이 대화의 메시지가 아닙니다.");
        }
        if (!googleClient.isEnabled()) {
            throw new IllegalArgumentException("번역 기능을 사용할 수 없습니다.");
        }
        String target = switch (uiLang == null ? "ko" : uiLang) {
            case "en" -> "en";
            case "zh" -> "zh-CN";
            default   -> "ko";
        };
        List<String> result = googleClient.translate(List.of(message.getContent()), null, target);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("번역에 실패했습니다.");
        }
        return result.get(0);
    }

    private Conversation assertParticipant(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));
        if (!conversation.isParticipant(userId)) {
            throw new IllegalArgumentException("이 대화에 참여할 권한이 없습니다.");
        }
        return conversation;
    }

    private ConversationResponse toResponse(Conversation c, Long viewerId,
                                            Map<Long, User> usersById,
                                            Map<Long, GuideProfile> profiles) {
        boolean viewerIsTraveler = c.getTravelerUserId().equals(viewerId);
        Long otherUserId = viewerIsTraveler ? c.getGuideUserId() : c.getTravelerUserId();
        User other = usersById.get(otherUserId);
        GuideProfile guide = viewerIsTraveler ? profiles.get(c.getGuideProfileId()) : null;
        return new ConversationResponse(c.getId(), c.getGuideProfileId(),
                otherUserId,
                other != null ? other.getFullName() : "알 수 없음",
                guide != null ? guide.getAvatarUrl() : null,
                viewerIsTraveler,
                c.getLastMessagePreview(), c.getLastMessageAt());
    }
}
