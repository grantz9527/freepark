package com.freepark.local.device.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.device.dto.DeviceRecognizeRequest;
import com.freepark.local.device.service.DeviceGatewayService;

import jakarta.validation.Valid;

/**
 * 设备侧网关接口：识别相机（臻识等）通过 HTTP 主动轮询/上报。
 * 此接口面向设备，不经过 JWT 鉴权（permitAll），仅以设备 code 识别身份。
 *
 * 交互方向恒为 设备 → 服务器：
 * - GET  /{code}/poll      设备轮询待执行指令（开闸/关闸等）。
 * - POST /{code}/recognize 设备识别到车牌后上报抓拍结果。
 */
@RestController
@RequestMapping("/api/v1/device-gateway/{code}")
public class DeviceGatewayController {

    private final DeviceGatewayService gatewayService;

    public DeviceGatewayController(DeviceGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /**
     * 设备轮询：返回待执行指令；无指令时 action="none"。
     * 每次调用都会刷新设备 lastPollAt 心跳，用于推导在线状态。
     */
    @GetMapping("/poll")
    public DevicePollResponse poll(@PathVariable String code) {
        return gatewayService.handlePoll(code);
    }

    /**
     * 识别上报：设备识别到车牌后 POST 上报。
     * 返回新生成识别记录的 id。
     */
    @PostMapping("/recognize")
    public UUID recognize(@PathVariable String code, @Valid @RequestBody DeviceRecognizeRequest request) {
        return gatewayService.handleRecognize(code, request);
    }
}
