package com.rental.auth.controller;

import com.rental.auth.dto.VerifyPasswordRequest;
import com.rental.auth.dto.VerifyPasswordResponse;
import com.rental.auth.service.AuthSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only. The API Gateway never routes {@code /internal/**}.
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthSessionService sessionService;

    @PostMapping("/verify-password")
    public VerifyPasswordResponse verifyPassword(@Valid @RequestBody VerifyPasswordRequest request) {
        return sessionService.verifyPassword(request);
    }
}
