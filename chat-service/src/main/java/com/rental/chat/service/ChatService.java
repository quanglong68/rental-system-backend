package com.rental.chat.service;

import com.rental.chat.dto.ContactResponse;
import com.rental.chat.dto.ConversationCreatedEvent;
import com.rental.chat.dto.ConversationReadEvent;
import com.rental.chat.dto.ConversationSummaryResponse;
import com.rental.chat.dto.CreateConversationRequest;
import com.rental.chat.dto.MarkReadRequest;
import com.rental.chat.dto.MessageCursorResponse;
import com.rental.chat.dto.MessageResponse;
import com.rental.chat.dto.SendMessageRequest;
import com.rental.chat.dto.TypingEvent;
import com.rental.chat.dto.TypingRequest;
import com.rental.chat.dto.UnreadCountResponse;
import com.rental.chat.dto.UnreadEvent;
import com.rental.chat.entity.ChatMessage;
import com.rental.chat.entity.Conversation;
import com.rental.chat.entity.ConversationParticipant;
import com.rental.chat.realtime.ChatEventNotifier;
import com.rental.chat.realtime.SseRegistry;
import com.rental.chat.repository.ChatMessageRepository;
import com.rental.chat.repository.ConversationParticipantRepository;
import com.rental.chat.repository.ConversationRepository;
import com.rental.common.dto.PageResponse;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int CONVERSATION_PAGE_MAX = 50;
    private static final int MESSAGE_PAGE_MAX = 50;

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final ChatMessageRepository messages;
    private final ChatContextResolver contextResolver;
    private final SseRegistry registry;
    private final ChatEventNotifier notifier;

    @Value("${chat.message.max-length:2000}")
    private int maxMessageLength;

    @Transactional
    public ConversationSummaryResponse open(HeaderUser user, CreateConversationRequest request) {
        ConversationContext context = contextResolver.resolve(user, request);
        Long userId = Long.parseLong(user.getUserId());
        Conversation conversation = conversations.findByDedupKey(context.dedupKey()).orElse(null);
        if (conversation == null) {
            conversation = conversations.save(Conversation.builder()
                    .contextType(context.contextType())
                    .contextId(context.contextId())
                    .dedupKey(context.dedupKey())
                    .contextLabel(context.contextLabel())
                    .buildingId(context.buildingId())
                    .roomId(context.roomId())
                    .build());
            participants.saveAll(List.of(participantRow(conversation.getId(), context.self()),
                    participantRow(conversation.getId(), context.peer())));
            notifier.publish(List.of(context.self().userId(), context.peer().userId()),
                    "conversation.created", new ConversationCreatedEvent(conversation.getId()));
        }
        requireParticipant(conversation.getId(), userId);
        List<ConversationParticipant> rows = participants.findByConversationId(conversation.getId());
        return summary(conversation, rows, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationSummaryResponse> listConversations(HeaderUser user, int page, int size,
            String keyword) {
        assertPage(page, size, CONVERSATION_PAGE_MAX);
        Long userId = Long.parseLong(user.getUserId());
        String pattern = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Conversation> result = conversations.findForUser(userId, pattern, PageRequest.of(page, size));
        List<Long> ids = result.getContent().stream().map(Conversation::getId).toList();
        Map<Long, List<ConversationParticipant>> byConversation = ids.isEmpty()
                ? Map.of()
                : participants.findByConversationIdIn(ids).stream()
                        .collect(Collectors.groupingBy(ConversationParticipant::getConversationId));
        List<ConversationSummaryResponse> content = result.getContent().stream()
                .map(conversation -> summary(conversation,
                        byConversation.getOrDefault(conversation.getId(), List.of()), userId))
                .toList();
        return PageResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ConversationSummaryResponse getConversation(HeaderUser user, Long conversationId) {
        Long userId = Long.parseLong(user.getUserId());
        Conversation conversation = requireConversation(conversationId, userId);
        return summary(conversation, participants.findByConversationId(conversationId), userId);
    }

    @Transactional(readOnly = true)
    public MessageCursorResponse messages(HeaderUser user, Long conversationId, Long beforeId, int size) {
        if (size < 1 || size > MESSAGE_PAGE_MAX) {
            throw validation("size phải trong khoảng 1.." + MESSAGE_PAGE_MAX);
        }
        if (beforeId != null && beforeId <= 0) {
            throw validation("beforeId không hợp lệ");
        }
        Long userId = Long.parseLong(user.getUserId());
        requireConversation(conversationId, userId);
        Pageable limit = PageRequest.of(0, size + 1);
        List<ChatMessage> fetched = beforeId == null
                ? messages.findByConversationIdOrderByIdDesc(conversationId, limit)
                : messages.findByConversationIdAndIdLessThanOrderByIdDesc(conversationId, beforeId, limit);
        boolean hasMore = fetched.size() > size;
        List<ChatMessage> page = hasMore ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;
        List<MessageResponse> content = new ArrayList<>(page.stream().map(ChatService::toResponse).toList());
        Collections.reverse(content);
        Long peerLastRead = participants.findByConversationId(conversationId).stream()
                .filter(row -> !row.getUserId().equals(userId))
                .map(ConversationParticipant::getLastReadMessageId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new MessageCursorResponse(content, nextCursor, hasMore, peerLastRead);
    }

    @Transactional
    public MessageResponse sendMessage(HeaderUser user, Long conversationId, SendMessageRequest request) {
        Long userId = Long.parseLong(user.getUserId());
        requireConversation(conversationId, userId);
        ConversationParticipant self = requireParticipant(conversationId, userId);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw validation("Nội dung tin nhắn không được để trống");
        }
        if (content.length() > maxMessageLength) {
            throw validation("Tin nhắn tối đa " + maxMessageLength + " ký tự");
        }
        String clientMessageId = trimToNull(request.clientMessageId());
        if (clientMessageId != null) {
            ChatMessage existing = messages
                    .findByConversationIdAndClientMessageId(conversationId, clientMessageId)
                    .orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        ChatMessage message = messages.save(ChatMessage.builder()
                .conversationId(conversationId)
                .senderId(userId)
                .senderType(self.getUserType())
                .senderName(self.getDisplayName())
                .clientMessageId(clientMessageId)
                .content(content)
                .messageType("TEXT")
                .build());

        Conversation conversation = conversations.findById(conversationId).orElseThrow(
                () -> notFound("Không tìm thấy hội thoại"));
        conversation.setLastMessageId(message.getId());
        conversation.setLastMessagePreview(preview(content));
        conversation.setLastMessageAt(message.getCreatedAt());
        conversations.save(conversation);

        participants.incrementUnread(conversationId, userId);

        List<ConversationParticipant> rows = participants.findByConversationId(conversationId);
        List<Long> memberIds = rows.stream().map(ConversationParticipant::getUserId).toList();
        MessageResponse payload = toResponse(message);
        notifier.publish(memberIds, "message.created", payload);
        rows.stream()
                .filter(row -> !row.getUserId().equals(userId))
                .forEach(row -> notifier.publish(List.of(row.getUserId()), "unread",
                        new UnreadEvent(participants.totalUnread(row.getUserId()))));
        return payload;
    }

    @Transactional
    public UnreadCountResponse markRead(HeaderUser user, Long conversationId, MarkReadRequest request) {
        Long userId = Long.parseLong(user.getUserId());
        requireConversation(conversationId, userId);
        ConversationParticipant self = requireParticipant(conversationId, userId);
        ChatMessage message = messages.findByIdAndConversationId(request.messageId(), conversationId)
                .orElseThrow(() -> validation("Tin nhắn không thuộc hội thoại này"));

        if (self.getLastReadMessageId() == null || message.getId() > self.getLastReadMessageId()) {
            participants.markRead(conversationId, userId, message.getId());
        }
        Instant now = Instant.now();
        participants.touchLastSeen(userId, now);

        List<ConversationParticipant> rows = participants.findByConversationId(conversationId);
        List<Long> memberIds = rows.stream().map(ConversationParticipant::getUserId).toList();
        notifier.publish(memberIds, "conversation.read", new ConversationReadEvent(conversationId, userId,
                self.getDisplayName(), message.getId(), now));
        long total = participants.totalUnread(userId);
        notifier.publish(List.of(userId), "unread", new UnreadEvent(total));
        return new UnreadCountResponse(total);
    }

    @Transactional(readOnly = true)
    public void typing(HeaderUser user, Long conversationId, TypingRequest request) {
        Long userId = Long.parseLong(user.getUserId());
        requireConversation(conversationId, userId);
        ConversationParticipant self = requireParticipant(conversationId, userId);
        participants.findByConversationId(conversationId).stream()
                .filter(row -> !row.getUserId().equals(userId))
                .forEach(row -> registry.sendToUser(row.getUserId(), "typing",
                        new TypingEvent(conversationId, userId, self.getDisplayName(), request.typing())));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(HeaderUser user) {
        return new UnreadCountResponse(participants.totalUnread(Long.parseLong(user.getUserId())));
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> contacts(HeaderUser user, String contextType, Long contextId) {
        return contextResolver.contacts(user, contextType, contextId);
    }

    private Conversation requireConversation(Long conversationId, Long userId) {
        requireParticipant(conversationId, userId);
        return conversations.findById(conversationId)
                .orElseThrow(() -> notFound("Không tìm thấy hội thoại"));
    }

    private ConversationParticipant requireParticipant(Long conversationId, Long userId) {
        return participants.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> forbidden("Bạn không tham gia hội thoại này"));
    }

    private ConversationSummaryResponse summary(Conversation conversation,
            List<ConversationParticipant> rows, Long viewerId) {
        ConversationParticipant self = rows.stream()
                .filter(row -> row.getUserId().equals(viewerId))
                .findFirst()
                .orElse(null);
        ConversationParticipant peer = rows.stream()
                .filter(row -> !row.getUserId().equals(viewerId))
                .findFirst()
                .orElse(null);
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getContextType(),
                conversation.getContextLabel(),
                peer == null ? null : peer.getUserId(),
                peer == null ? "Hội thoại" : peer.getDisplayName(),
                peer == null ? null : peer.getUserType(),
                peer == null ? null : peer.getRoleInConversation(),
                conversation.getLastMessagePreview(),
                conversation.getLastMessageAt(),
                self == null ? 0 : self.getUnreadCount(),
                peer != null && registry.isOnline(peer.getUserId()),
                peer == null ? null : peer.getLastSeenAt(),
                peer == null ? null : peer.getLastReadMessageId());
    }

    private static ConversationParticipant participantRow(Long conversationId, ContextParticipant participant) {
        return ConversationParticipant.builder()
                .conversationId(conversationId)
                .userId(participant.userId())
                .userType(participant.userType())
                .roleInConversation(participant.role())
                .displayName(participant.displayName())
                .unreadCount(0)
                .build();
    }

    private static MessageResponse toResponse(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getConversationId(), message.getSenderId(),
                message.getSenderType(), message.getSenderName(), message.getContent(),
                message.getClientMessageId(), message.getCreatedAt());
    }

    private static String preview(String content) {
        String singleLine = content.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void assertPage(int page, int size, int maxSize) {
        if (page < 0 || size < 1 || size > maxSize) {
            throw validation("page phải >= 0 và size trong khoảng 1.." + maxSize);
        }
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    private static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }
}
