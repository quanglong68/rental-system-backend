package com.rental.chat.controller;

import com.rental.chat.realtime.SseRegistry;
import com.rental.common.security.HeaderUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Sự kiện Chat Realtime (SSE)", description = "Luồng Server-Sent Events đẩy tin nhắn mới và trạng thái đang gõ theo thời gian thực")
@RequiredArgsConstructor
public class ChatEventController {

    private final SseRegistry registry;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Kết nối luồng sự kiện SSE", description = "Mở kết nối text/event-stream cho người dùng đang đăng nhập để nhận sự kiện chat realtime.")
    public SseEmitter events(@AuthenticationPrincipal HeaderUser user) {
        return registry.connect(Long.parseLong(user.getUserId()));
    }
}
