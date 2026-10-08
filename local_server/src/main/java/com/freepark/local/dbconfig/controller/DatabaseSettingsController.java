package com.freepark.local.dbconfig.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.dbconfig.dto.DatabaseSettingsView;
import com.freepark.local.dbconfig.dto.UpdateDatabaseSettingsRequest;
import com.freepark.local.dbconfig.service.DatabaseSettingsService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/database-settings")
public class DatabaseSettingsController {

    private final DatabaseSettingsService service;
    private final MessageService messages;

    public DatabaseSettingsController(DatabaseSettingsService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<DatabaseSettingsView> get(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(messages, service.getSettings(UUID.fromString(jwt.getSubject())));
    }

    @PutMapping
    public ApiResponse<DatabaseSettingsView> update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateDatabaseSettingsRequest request) {
        return ApiResponse.ok(messages, service.update(UUID.fromString(jwt.getSubject()), request));
    }

    @PostMapping("/test")
    public ApiResponse<DatabaseSettingsView> test(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateDatabaseSettingsRequest request) {
        return ApiResponse.ok(messages, service.test(UUID.fromString(jwt.getSubject()), request));
    }

    @DeleteMapping
    public ApiResponse<DatabaseSettingsView> restore(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(messages, service.restoreDefaults(UUID.fromString(jwt.getSubject())));
    }
}
