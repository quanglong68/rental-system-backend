package com.rental.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.chat.dto.MarkReadRequest;
import com.rental.chat.dto.MessageResponse;
import com.rental.chat.dto.SendMessageRequest;
import com.rental.chat.entity.ChatMessage;
import com.rental.chat.entity.Conversation;
import com.rental.chat.entity.ConversationParticipant;
import com.rental.chat.realtime.ChatEventNotifier;
import com.rental.chat.realtime.SseRegistry;
import com.rental.chat.repository.ChatMessageRepository;
import com.rental.chat.repository.ConversationParticipantRepository;
import com.rental.chat.repository.ConversationRepository;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ConversationRepository conversations;
    @Mock private ConversationParticipantRepository participants;
    @Mock private ChatMessageRepository messages;
    @Mock private ChatContextResolver contextResolver;
    @Mock private SseRegistry registry;
    @Mock private ChatEventNotifier notifier;
    @InjectMocks private ChatService chatService;

    private final HeaderUser customer = new HeaderUser("10", "CUSTOMER", List.of());
    private Conversation conversation;
    private ConversationParticipant self;
    private ConversationParticipant peer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(chatService, "maxMessageLength", 2000);
        conversation = Conversation.builder()
                .id(5L)
                .contextType("VIEWING")
                .contextId(99L)
                .dedupKey("VIEWING:99:20")
                .contextLabel("Xem phòng · Phòng 201")
                .build();
        self = ConversationParticipant.builder()
                .conversationId(5L).userId(10L).userType("CUSTOMER").roleInConversation("CUSTOMER")
                .displayName("Khách An").unreadCount(0).build();
        peer = ConversationParticipant.builder()
                .conversationId(5L).userId(20L).userType("STAFF").roleInConversation("STAFF")
                .displayName("Sale Bình").unreadCount(0).build();
    }

    @Test
    void sendMessage_savesMessageAndPublishesEvents() {
        when(participants.findByConversationIdAndUserId(5L, 10L)).thenReturn(Optional.of(self));
        when(conversations.findById(5L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(100L);
            saved.setCreatedAt(Instant.parse("2026-11-03T10:00:00Z"));
            return saved;
        });
        when(participants.findByConversationId(5L)).thenReturn(List.of(self, peer));
        when(participants.totalUnread(20L)).thenReturn(1L);

        MessageResponse response = chatService.sendMessage(customer, 5L,
                new SendMessageRequest("  Chào bạn  ", "client-1"));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.content()).isEqualTo("Chào bạn");
        assertThat(conversation.getLastMessageId()).isEqualTo(100L);
        assertThat(conversation.getLastMessagePreview()).isEqualTo("Chào bạn");
        verify(participants).incrementUnread(5L, 10L);
        verify(notifier).publish(eq(List.of(10L, 20L)), eq("message.created"), any());
        verify(notifier).publish(eq(List.of(20L)), eq("unread"), any());
    }

    @Test
    void sendMessage_isIdempotentForClientMessageId() {
        ChatMessage existing = ChatMessage.builder()
                .id(77L).conversationId(5L).senderId(10L).senderType("CUSTOMER").senderName("Khách An")
                .clientMessageId("client-1").content("Đã gửi").messageType("TEXT")
                .createdAt(Instant.now()).build();
        when(participants.findByConversationIdAndUserId(5L, 10L)).thenReturn(Optional.of(self));
        when(conversations.findById(5L)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndClientMessageId(5L, "client-1")).thenReturn(Optional.of(existing));

        MessageResponse response = chatService.sendMessage(customer, 5L,
                new SendMessageRequest("Đã gửi", "client-1"));

        assertThat(response.id()).isEqualTo(77L);
        verify(messages, never()).save(any(ChatMessage.class));
        verify(notifier, never()).publish(anyList(), eq("message.created"), any());
    }

    @Test
    void sendMessage_rejectsNonParticipant() {
        when(participants.findByConversationIdAndUserId(5L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(customer, 5L, new SendMessageRequest("Hi", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.name());
    }

    @Test
    void messages_returnsAscendingPageWhenPeerHasNotRead() {
        ChatMessage newest = ChatMessage.builder()
                .id(102L).conversationId(5L).senderId(20L).senderType("STAFF").senderName("Sale Bình")
                .content("Tin mới").messageType("TEXT").createdAt(Instant.parse("2026-11-03T10:02:00Z")).build();
        ChatMessage older = ChatMessage.builder()
                .id(101L).conversationId(5L).senderId(10L).senderType("CUSTOMER").senderName("Khách An")
                .content("Tin cũ").messageType("TEXT").createdAt(Instant.parse("2026-11-03T10:01:00Z")).build();
        when(participants.findByConversationIdAndUserId(5L, 10L)).thenReturn(Optional.of(self));
        when(conversations.findById(5L)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdOrderByIdDesc(eq(5L), any(Pageable.class)))
                .thenReturn(List.of(newest, older));
        when(participants.findByConversationId(5L)).thenReturn(List.of(self, peer));

        var response = chatService.messages(customer, 5L, null, 30);

        assertThat(response.content()).extracting(MessageResponse::id).containsExactly(101L, 102L);
        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.peerLastReadMessageId()).isNull();
    }

    @Test
    void messages_limitsToRequestedSizeAndReturnsCursor() {
        ChatMessage newest = ChatMessage.builder()
                .id(102L).conversationId(5L).senderId(20L).senderType("STAFF").senderName("Sale Bình")
                .content("Tin mới").messageType("TEXT").createdAt(Instant.parse("2026-11-03T10:02:00Z")).build();
        ChatMessage older = ChatMessage.builder()
                .id(101L).conversationId(5L).senderId(10L).senderType("CUSTOMER").senderName("Khách An")
                .content("Tin cũ").messageType("TEXT").createdAt(Instant.parse("2026-11-03T10:01:00Z")).build();
        when(participants.findByConversationIdAndUserId(5L, 10L)).thenReturn(Optional.of(self));
        when(conversations.findById(5L)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdOrderByIdDesc(eq(5L), any(Pageable.class)))
                .thenReturn(List.of(newest, older));
        when(participants.findByConversationId(5L)).thenReturn(List.of(self, peer));

        var response = chatService.messages(customer, 5L, null, 1);

        assertThat(response.content()).extracting(MessageResponse::id).containsExactly(102L);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(102L);
    }

    @Test
    void markRead_resetsUnreadAndPublishesReadEvent() {
        ChatMessage message = ChatMessage.builder()
                .id(100L).conversationId(5L).senderId(20L).senderType("STAFF").senderName("Sale Bình")
                .content("Tin mới").messageType("TEXT").createdAt(Instant.now()).build();
        when(participants.findByConversationIdAndUserId(5L, 10L)).thenReturn(Optional.of(self));
        when(conversations.findById(5L)).thenReturn(Optional.of(conversation));
        when(messages.findByIdAndConversationId(100L, 5L)).thenReturn(Optional.of(message));
        when(participants.findByConversationId(5L)).thenReturn(List.of(self, peer));
        when(participants.totalUnread(10L)).thenReturn(4L);

        long remaining = chatService.markRead(customer, 5L, new MarkReadRequest(100L)).count();

        assertThat(remaining).isEqualTo(4L);
        verify(participants).markRead(5L, 10L, 100L);
        verify(notifier).publish(eq(List.of(10L, 20L)), eq("conversation.read"), any());
    }
}
