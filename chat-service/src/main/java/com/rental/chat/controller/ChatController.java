package com.rental.chat.controller;

import com.rental.chat.dto.ContactResponse;
import com.rental.chat.dto.ConversationSummaryResponse;
import com.rental.chat.dto.CreateConversationRequest;
import com.rental.chat.dto.MarkReadRequest;
import com.rental.chat.dto.MessageCursorResponse;
import com.rental.chat.dto.MessageResponse;
import com.rental.chat.dto.SendMessageRequest;
import com.rental.chat.dto.TypingRequest;
import com.rental.chat.dto.UnreadCountResponse;
import com.rental.chat.service.ChatService;
import com.rental.common.dto.PageResponse;
import com.rental.common.security.HeaderUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/conversations")
    public PageResponse<ConversationSummaryResponse> conversations(
            @AuthenticationPrincipal HeaderUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return chatService.listConversations(user, page, size, keyword);
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationSummaryResponse conversation(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId) {
        return chatService.getConversation(user, conversationId);
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationSummaryResponse openConversation(@AuthenticationPrincipal HeaderUser user,
            @Valid @RequestBody CreateConversationRequest request) {
        return chatService.open(user, request);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public MessageCursorResponse messages(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int size) {
        return chatService.messages(user, conversationId, beforeId, size);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(user, conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/read")
    public UnreadCountResponse markRead(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId,
            @Valid @RequestBody MarkReadRequest request) {
        return chatService.markRead(user, conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void typing(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId,
            @RequestBody TypingRequest request) {
        chatService.typing(user, conversationId, request);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal HeaderUser user) {
        return chatService.unreadCount(user);
    }

    @GetMapping("/contacts")
    public List<ContactResponse> contacts(@AuthenticationPrincipal HeaderUser user,
            @RequestParam String contextType,
            @RequestParam(required = false) Long contextId) {
        return chatService.contacts(user, contextType, contextId);
    }
}
