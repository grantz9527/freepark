package com.freepark.local.patternallowlist.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.api.PageView;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.patternallowlist.dto.CreatePatternAllowlistRequest;
import com.freepark.local.patternallowlist.service.PatternAllowlistService;
import com.freepark.local.patternallowlist.dto.PatternAllowlistView;
import com.freepark.local.patternallowlist.dto.UpdatePatternAllowlistRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lots/{lotId}/pattern-allowlist")
public class PatternAllowlistController {

    private final PatternAllowlistService patternAllowlistService;
    private final MessageService messages;

    public PatternAllowlistController(PatternAllowlistService patternAllowlistService, MessageService messages) {
        this.patternAllowlistService = patternAllowlistService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<PageView<PatternAllowlistView>> list(
            @PathVariable UUID lotId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(messages, patternAllowlistService.listEntries(lotId, keyword, page, size));
    }

    @PostMapping
    public ApiResponse<PatternAllowlistView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @Valid @RequestBody CreatePatternAllowlistRequest request) {
        return ApiResponse.ok(
                messages,
                patternAllowlistService.createEntry(UUID.fromString(jwt.getSubject()), lotId, request));
    }

    @PutMapping("/{entryId}")
    public ApiResponse<PatternAllowlistView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID entryId,
            @Valid @RequestBody UpdatePatternAllowlistRequest request) {
        return ApiResponse.ok(
                messages,
                patternAllowlistService.updateEntry(
                        UUID.fromString(jwt.getSubject()), lotId, entryId, request));
    }

    @DeleteMapping("/{entryId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID entryId) {
        patternAllowlistService.deleteEntry(UUID.fromString(jwt.getSubject()), lotId, entryId);
        return ApiResponse.ok(messages, null);
    }
}
