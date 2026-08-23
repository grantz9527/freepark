package com.freepark.local.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.sitesettings.SystemSettingsService;
import com.freepark.local.sitesettings.SystemSettingsView;
import com.freepark.local.sitesettings.UpdateSystemSettingsRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/system-settings")
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;
    private final MessageService messages;

    public SystemSettingsController(SystemSettingsService systemSettingsService, MessageService messages) {
        this.systemSettingsService = systemSettingsService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<SystemSettingsView> get() {
        return ApiResponse.ok(messages, systemSettingsService.getSettings());
    }

    @PutMapping
    public ApiResponse<SystemSettingsView> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateSystemSettingsRequest request) {
        return ApiResponse.ok(
                messages,
                systemSettingsService.updateSettings(UUID.fromString(jwt.getSubject()), request));
    }
}
