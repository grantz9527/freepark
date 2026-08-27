package com.freepark.local.device.protocol;

import tools.jackson.databind.JsonNode;
import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.RecognitionRecord;

/**
 * 识别设备品牌协议适配。
 * 不同品牌相机推送/轮询报文格式不同，各自实现本接口。
 */
public interface CameraProtocol {

    /** 本协议支持的品牌标识（与 ParkingBarrier.brand 对应）。 */
    String brand();

    /** 从推送报文中提取设备标识（序列号），用于匹配 ParkingBarrier.code。 */
    String extractDeviceId(JsonNode pushData);

    /** 将品牌推送报文解析为识别记录。 */
    RecognitionRecord parsePush(ParkingBarrier device, JsonNode pushData);

    /** 生成品牌推送响应报文（开闸/关闸指令），返回 JsonNode 由 Spring MVC 序列化。 */
    JsonNode buildPushResponse(boolean openGate);

    /** 将排队指令格式化为轮询返回报文（可选，支持轮询的品牌实现）。无指令时返回空。 */
    DevicePollResponse buildPollResponse(DeviceCommand command);
}
