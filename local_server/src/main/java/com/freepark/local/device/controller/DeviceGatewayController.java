package com.freepark.local.device.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import com.freepark.local.device.service.DeviceGatewayService;

/**
 * 设备侧网关接口：识别相机（臻识等）通过 HTTP 主动轮询/推送。
 * 此接口面向设备，不经过 JWT 鉴权（permitAll），仅以设备 code/serialno 识别身份。
 *
 * 交互方向恒为 设备 → 服务器：
 * - GET  /{code}/poll    设备轮询（如臻识 comet 轮询）：有排队开闸指令时返回协议开闸结构
 *                        （{"Response_AlarmInfoPlate":{"info":"ok"}}），无指令时返回心跳确认（{"status":"ok"}）。
 *                        每次调用刷新 lastPollAt 心跳，用于推导在线状态。
 * - POST /{brand}/plate  设备识别到车牌后推送识别结果（如臻识500的 AlarmInfoPlate）。
 *                        服务器保存识别记录，并在 HTTP 响应中返回开闸/不开闸指令。
 */
@RestController
@RequestMapping("/api/v1/device-gateway")
public class DeviceGatewayController {

    private static final Logger log = LoggerFactory.getLogger(DeviceGatewayController.class);

    private final DeviceGatewayService gatewayService;

    public DeviceGatewayController(DeviceGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /** 以 JSON 文本形式打印轮询返回，便于对照设备是否来轮询、是否取到开闸指令。 */
    private static String toLogJson(JsonNode res) {
        return res == null ? "null" : res.toString();
    }

    /**
     * 设备轮询：返回品牌协议规定的应答。
     * 有排队开闸指令时返回协议触发结构（臻识为 {"Response_AlarmInfoPlate":{"info":"ok"}}），
     * 无指令时返回心跳确认（臻识为 {"status":"ok"}），相机据此继续下一轮询。
     * 适用于轮询模型的设备（如臻识老款一体机）。
     * 兼容不同设备的轮询习惯：部分老款一体机使用 POST 轮询（body 多为 multipart 或空），
     * 轮询逻辑不读取请求体，故同时接受 GET 与 POST，POST 的 body 直接忽略。
     */
    @RequestMapping(value = "/{code}/poll", method = {RequestMethod.GET, RequestMethod.POST})
    public JsonNode poll(@PathVariable String code) {
        JsonNode res = gatewayService.handlePoll(code);
        log.info("[poll] code={} 返回: {}", code, toLogJson(res));
        return res;
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
