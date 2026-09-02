package com.freepark.local.aiodriver.controller;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.aiodriver.service.AioDriverService;
import com.freepark.local.aiodriver.service.AioDriverService.Action;
import com.freepark.local.aiodriver.service.AioDriverService.CommandResult;
import com.freepark.local.aiodriver.service.AioDriverService.DeviceRuntimeView;
import com.freepark.local.aiodriver.service.AioDriverService.FactoryView;
import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.common.i18n.MessageService;

/**
 * 一体机驱动接入 API（平台侧骨架，供管理员联调/验证驱动通道）。
 *
 * <p>设备档案复用设备管理里的 barrier（code=设备序列号，brand=品牌），
 * 连接地址等网络参数来自 {@code freepark.aio-driver.endpoints} 配置。
 *
 * - GET  /api/v1/aio-drivers                    已装配的驱动工厂（品牌/型号/能力）
 * - GET  /api/v1/aio-drivers/devices            已实例化的设备运行时状态
 * - POST /api/v1/aio-drivers/{code}/commands    下发统一命令（管理员）
 */
@RestController
@RequestMapping("/api/v1/aio-drivers")
public class AioDriverController {

    private final AioDriverService driverService;
    private final MessageService messages;

    public AioDriverController(AioDriverService driverService, MessageService messages) {
        this.driverService = driverService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<FactoryView>> factories() {
        return ApiResponse.ok(messages, driverService.factories());
    }

    @GetMapping("/devices")
    public ApiResponse<List<DeviceRuntimeView>> devices() {
        return ApiResponse.ok(messages, driverService.devices());
    }

    /** 下发统一命令。body: { "action": "OPEN|CLOSE|ALWAYS_ON|ALWAYS_OFF|SHOW|SPEAK|STATUS", "kind"?, "text"? } */
    @PostMapping("/{code}/commands")
    public ApiResponse<CommandResult> command(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String code,
            @RequestBody AioCommandRequest request) {
        Action action = parseAction(request.action());
        CommandResult result = driverService.execute(
                UUID.fromString(jwt.getSubject()), code, action, request.kind(), request.text());
        return ApiResponse.ok(messages, result);
    }

    /** 命令请求体。kind 仅 SHOW（DisplayKind）/ SPEAK（VoiceKind）使用，缺省 FREE_TEXT。 */
    public record AioCommandRequest(String action, String kind, String text) {
    }

    private Action parseAction(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "action 不能为空");
        }
        try {
            return Action.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "未知命令 action=" + raw);
        }
    }
}
