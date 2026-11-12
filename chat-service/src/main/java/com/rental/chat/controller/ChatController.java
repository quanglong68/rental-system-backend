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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Trò chuyện & Hội thoại", description = "Mở hội thoại, tải lịch sử tin nhắn, gửi tin nhắn, đánh dấu đã đọc và tra cứu liên hệ")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/conversations")
    @Operation(summary = "Danh sách hội thoại", description = "Liệt kê phân trang các cuộc hội thoại của người dùng hiện tại, hỗ trợ tìm theo từ khóa.")
    public PageResponse<ConversationSummaryResponse> conversations(
            @AuthenticationPrincipal HeaderUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return chatService.listConversations(user, page, size, keyword);
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Chi tiết hội thoại", description = "Trả về thông tin tóm tắt của một cuộc hội thoại theo ID.")
    public ConversationSummaryResponse conversation(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId) {
        return chatService.getConversation(user, conversationId);
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Mở hoặc lấy hội thoại", description = "Tạo hội thoại mới hoặc trả về hội thoại đã có theo ngữ cảnh (LISTING, RENTAL_REQUEST, CONTRACT) và người nhận.")
    public ConversationSummaryResponse openConversation(@AuthenticationPrincipal HeaderUser user,
            @Valid @RequestBody CreateConversationRequest request) {
        return chatService.open(user, request);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Tải tin nhắn trong hội thoại", description = "Tải danh sách tin nhắn theo con trỏ beforeId và kích thước size (mặc định 30).")
    public MessageCursorResponse messages(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int size) {
        return chatService.messages(user, conversationId, beforeId, size);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi tin nhắn", description = "Gửi tin nhắn văn bản hoặc đính kèm trong hội thoại và phát sự kiện realtime qua SSE.")
    public MessageResponse sendMessage(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(user, conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/read")
    @Operation(summary = "Đánh dấu đã đọc tin nhắn", description = "Cập nhật vị trí tin nhắn đã đọc và trả về số lượng tin chưa đọc còn lại.")
    public UnreadCountResponse markRead(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId,
            @Valid @RequestBody MarkReadRequest request) {
        return chatService.markRead(user, conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Gửi trạng thái đang gõ", description = "Phát tín hiệu đang soạn tin nhắn tới đối phương qua SSE.")
    public void typing(@AuthenticationPrincipal HeaderUser user,
            @PathVariable Long conversationId,
            @RequestBody TypingRequest request) {
        chatService.typing(user, conversationId, request);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm tổng tin nhắn chưa đọc", description = "Trả về tổng số tin nhắn chưa đọc trên toàn bộ hội thoại của người dùng.")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal HeaderUser user) {
        return chatService.unreadCount(user);
    }

    @GetMapping("/contacts")
    @Operation(summary = "Danh sách liên hệ theo ngữ cảnh", description = "Gợi ý người có thể nhắn tin dựa trên contextType (LISTING, RENTAL_REQUEST, CONTRACT...) và contextId.")
    public List<ContactResponse> contacts(@AuthenticationPrincipal HeaderUser user,
            @RequestParam String contextType,
            @RequestParam(required = false) Long contextId) {
        return chatService.contacts(user, contextType, contextId);
    }
}
