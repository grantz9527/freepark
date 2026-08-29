package com.freepark.local.device.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.device.dto.AutoRegisteredDeviceView;
import com.freepark.local.device.service.AutoRegisteredDeviceService;

/**
 * 自动发现设备管理接口（需登录）。
 * 设备轮询网关后自动登记于此，管理人员可查看、移除。
 */
@RestController
@RequestMapping("/api/v1/auto-devices")
public class AutoRegisteredDeviceController {

    private final AutoRegisteredDeviceService autoDeviceService;
    private final MessageService messages;

    public AutoRegisteredDeviceController(
            AutoRegisteredDeviceService autoDeviceService, MessageService messages) {
        this.autoDeviceService = autoDeviceService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<AutoRegisteredDeviceView>> list() {
        return ApiResponse.ok(messages, autoDeviceService.listAll());
    }

    /** 收录：标记为已收录，该设备轮询时不再重复自动发现。 */
    @PostMapping("/{deviceId}/adopt")
    public ApiResponse<Void> adopt(@PathVariable UUID deviceId) {
        autoDeviceService.adopt(deviceId);
        return ApiResponse.ok(messages, null);
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> delete(@PathVariable UUID deviceId) {
        autoDeviceService.delete(deviceId);
        return ApiResponse.ok(messages, null);
    }
}
