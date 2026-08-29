package com.freepark.local.device.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.device.service.DeviceGatewayService;

/**
 * 设备侧网关接口：识别相机（臻识等）通过 HTTP 主动轮询/推送。
 * 此接口面向设备，不经过 JWT 鉴权（permitAll），仅以设备 code/serialno 识别身份。
 *
 * 交互方向恒为 设备 → 服务器：
 * - GET  /{code}/poll    设备轮询待执行指令（开闸/关闸等），无指令时 action="none"。
 *                        每次调用刷新 lastPollAt 心跳，用于推导在线状态。
 * - POST /{brand}/plate  设备识别到车牌后推送识别结果（如臻识500的 AlarmInfoPlate）。
 *                        服务器保存识别记录，并在 HTTP 响应中返回开闸/不开闸指令。
 */
@RestController
@RequestMapping("/api/v1/device-gateway")
public class DeviceGatewayController {

    private final DeviceGatewayService gatewayService;

    public DeviceGatewayController(DeviceGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /**
     * 设备轮询：返回待执行指令；无指令时 action="none"。
     * 适用于轮询模型的设备（如臻识老款一体机）。
     * 兼容不同设备的轮询习惯：部分老款一体机使用 POST 轮询（body 多为 multipart 或空），
     * 轮询逻辑不读取请求体，故同时接受 GET 与 POST，POST 的 body 直接忽略。
     */
    @RequestMapping(value = "/{code}/poll", method = {RequestMethod.GET, RequestMethod.POST})
    public DevicePollResponse poll(@PathVariable String code) {
        return gatewayService.handlePoll(code);
    }

    /**
     * 识别结果推送：设备识别到车牌后 POST 推送。
     * brand 用于路由到对应品牌协议（如 zhenshi），设备序列号从推送报文中提取。
     * 响应体为品牌协议定义的开闸/不开闸指令。
     */
    @PostMapping("/{brand}/plate")
    public JsonNode pushPlate(@PathVariable String brand, @RequestBody JsonNode pushData) {
        return gatewayService.handlePush(brand, pushData);
    }
}
