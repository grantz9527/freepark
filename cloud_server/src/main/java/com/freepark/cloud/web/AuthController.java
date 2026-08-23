package com.freepark.cloud.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.cloud.auth.AuthService;
import com.freepark.cloud.auth.ChangePasswordRequest;
import com.freepark.cloud.auth.LoginRequest;
import com.freepark.cloud.auth.LoginResponse;
import com.freepark.cloud.auth.UserView;
import com.freepark.cloud.common.api.ApiResponse;
import com.freepark.cloud.common.i18n.MessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final MessageService messages;

    public AuthController(AuthService authService, MessageService messages) {
        this.authService = authService;
        this.messages = messages;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(messages, authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(messages, authService.currentUser(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(UUID.fromString(jwt.getSubject()), request);
        return ApiResponse.ok(messages, null);
    }
}
