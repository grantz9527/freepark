package com.freepark.local.barrier.controller;

import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.barrier.dto.BarrierView;
import com.freepark.local.barrier.dto.BindBarrierRequest;
import com.freepark.local.barrier.dto.CreateBarrierRequest;
import com.freepark.local.barrier.dto.UpdateBarrierRequest;
import com.freepark.local.barrier.service.ParkingBarrierService;
import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;

import jakarta.validation.Valid;

/**
 * 识别一体机全局管理（设备可先登记、后绑定车道）：
 * - GET  /                      全部设备（含未绑定车道）
 * - POST /                      创建设备（暂不绑定车道）
 * - PUT  /{barrierId}           更新名称/启用状态
 * - DELETE /{barrierId}         删除设备
 * - POST /{barrierId}/bind      绑定车道（{laneId}）
 * - DELETE /{barrierId}/bind    解绑车道
 */
@RestController
@RequestMapping("/api/v1/barriers")
public class BarrierController {

    private final ParkingBarrierService parkingBarrierService;
    private final MessageService messages;

    public BarrierController(ParkingBarrierService parkingBarrierService, MessageService messages) {
        this.parkingBarrierService = parkingBarrierService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<BarrierView>> list() {
        return ApiResponse.ok(messages, parkingBarrierService.listAll());
    }

    @PostMapping
    public ApiResponse<BarrierView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBarrierRequest request) {
        return ApiResponse.ok(
                messages,
                parkingBarrierService.createBarrier(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping("/{barrierId}")
    public ApiResponse<BarrierView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID barrierId,
            @Valid @RequestBody UpdateBarrierRequest request) {
        return ApiResponse.ok(
                messages,
                parkingBarrierService.updateBarrier(
                        UUID.fromString(jwt.getSubject()), barrierId, request));
    }

    @DeleteMapping("/{barrierId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID barrierId) {
        parkingBarrierService.deleteBarrier(UUID.fromString(jwt.getSubject()), barrierId);
        return ApiResponse.ok(messages, null);
    }

    @PostMapping("/{barrierId}/bind")
    public ApiResponse<BarrierView> bind(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID barrierId,
            @Valid @RequestBody BindBarrierRequest request) {
        return ApiResponse.ok(
                messages,
                parkingBarrierService.bindToLane(
                        UUID.fromString(jwt.getSubject()), barrierId, request.laneId()));
    }

    @DeleteMapping("/{barrierId}/bind")
    public ApiResponse<BarrierView> unbind(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID barrierId) {
        return ApiResponse.ok(
                messages,
                parkingBarrierService.unbind(UUID.fromString(jwt.getSubject()), barrierId));
    }
}
