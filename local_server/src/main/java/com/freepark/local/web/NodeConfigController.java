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
import com.freepark.local.nodeconfig.NodeConfigService;
import com.freepark.local.nodeconfig.NodeSettingsView;
import com.freepark.local.nodeconfig.UpdateNodeSettingsRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/node-settings")
public class NodeConfigController {

    private final NodeConfigService nodeConfigService;
    private final MessageService messages;

    public NodeConfigController(NodeConfigService nodeConfigService, MessageService messages) {
        this.nodeConfigService = nodeConfigService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<NodeSettingsView> get() {
        return ApiResponse.ok(messages, nodeConfigService.getSettings());
    }

    @PutMapping
    public ApiResponse<NodeSettingsView> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateNodeSettingsRequest request) {
        return ApiResponse.ok(
                messages,
                nodeConfigService.updateSettings(UUID.fromString(jwt.getSubject()), request));
    }
}
