package com.freepark.local.nodeconfig.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.nodeconfig.dto.FeeQuoteRequest;
import com.freepark.local.nodeconfig.dto.FeeQuoteView;
import com.freepark.local.nodeconfig.dto.NodeSettingsView;
import com.freepark.local.nodeconfig.dto.UpdateNodeSettingsRequest;
import com.freepark.local.nodeconfig.service.FeeQuoteClient;
import com.freepark.local.nodeconfig.service.NodeConfigService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/node-settings")
public class NodeConfigController {

    private final NodeConfigService nodeConfigService;
    private final FeeQuoteClient feeQuoteClient;
    private final MessageService messages;

    public NodeConfigController(
            NodeConfigService nodeConfigService,
            FeeQuoteClient feeQuoteClient,
            MessageService messages) {
        this.nodeConfigService = nodeConfigService;
        this.feeQuoteClient = feeQuoteClient;
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

    /**
     * 算费请求：向节点配置的远程算费接口传入车牌与车牌颜色，返回费用金额。
     */
    @PostMapping("/fee-quote")
    public ApiResponse<FeeQuoteView> quote(@Valid @RequestBody FeeQuoteRequest request) {
        return ApiResponse.ok(
                messages,
                new FeeQuoteView(feeQuoteClient.quote(request.plateNumber(), request.plateColor())));
    }
}
