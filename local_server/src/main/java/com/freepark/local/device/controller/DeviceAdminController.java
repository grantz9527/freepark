package com.freepark.local.device.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.device.dto.DeviceCommandView;
import com.freepark.local.device.dto.DeviceStatusView;
import com.freepark.local.device.service.DeviceCommandService;
import com.freepark.local.device.service.DeviceQueryService;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.RecognitionRecord;

import jakarta.validation.Valid;

/**
 * 管理员侧设备管理接口：设备状态、指令下发、识别记录查询。
 * 所有接口需 JWT 鉴权，且下发指令需管理员角色。
 *
 * - GET    /api/v1/devices                       列出所有设备状态（可按 laneId 过滤）
 * - GET    /api/v1/devices/{deviceId}/commands   列出设备最近指令
 * - POST   /api/v1/devices/{deviceId}/commands  下发指令（开闸/关闸/查询）
 * - GET    /api/v1/devices/{deviceId}/records   列出设备识别记录
 * - GET    /api/v1/devices/records              按车牌查询识别记录
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceAdminController {

    private static final int MAX_LIMIT = 200;

    private final DeviceQueryService queryService;
    private final DeviceCommandService commandService;
    private final MessageService messages;

    public DeviceAdminController(
            DeviceQueryService queryService,
            DeviceCommandService commandService,
            MessageService messages) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.messages = messages;
    }

    /** 列出设备状态；可选 laneId 过滤某一通道下的设备。 */
    @GetMapping
    public ApiResponse<List<DeviceStatusView>> listStatuses(@RequestParam(required = false) UUID laneId) {
        List<DeviceStatusView> statuses = (laneId != null)
                ? queryService.listStatusesByLane(laneId)
                : queryService.listStatuses();
        return ApiResponse.ok(messages, statuses);
    }

    /** 列出设备最近的指令记录。 */
    @GetMapping("/{deviceId}/commands")
    public ApiResponse<List<DeviceCommandView>> listCommands(
            @PathVariable UUID deviceId,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ApiResponse.ok(messages, queryService.listCommands(deviceId, clamp(limit)));
    }

    /** 下发指令到设备排队（仅管理员）。action 取值：OPEN/CLOSE/QUERY。 */
    @PostMapping("/{deviceId}/commands")
    public ApiResponse<DeviceCommandView> enqueueCommand(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID deviceId,
            @Valid @RequestBody EnqueueCommandRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        DeviceCommand.Action action = DeviceCommand.Action.valueOf(request.action().toUpperCase());
        DeviceCommandView view = commandService.enqueue(requesterId, deviceId, action, request.source());
        return ApiResponse.ok(messages, view);
    }

    /** 列出设备的识别记录。 */
    @GetMapping("/{deviceId}/records")
    public ApiResponse<List<RecognitionRecord>> listRecords(
            @PathVariable UUID deviceId,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ApiResponse.ok(messages, queryService.listRecords(deviceId, clamp(limit)));
    }

    /** 按车牌查询识别记录。 */
    @GetMapping("/records")
    public ApiResponse<List<RecognitionRecord>> listRecordsByPlate(
            @RequestParam String plate,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ApiResponse.ok(messages, queryService.listRecordsByPlate(plate, clamp(limit)));
    }

    private int clamp(int limit) {
        return Math.min(Math.max(1, limit), MAX_LIMIT);
    }

    /** 下发指令请求体：action 为 OPEN/CLOSE/QUERY，source 为来源说明。 */
    public record EnqueueCommandRequest(String action, String source) {
    }
}
