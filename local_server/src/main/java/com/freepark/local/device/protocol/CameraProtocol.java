package com.freepark.local.device.protocol;

import tools.jackson.databind.JsonNode;
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

    /**
     * 生成品牌推送响应报文并附带一路语音播报（如识别放行按方向的欢迎语）。
     * 默认忽略语音文本（不支持语音回传的品牌直接复用 {@link #buildPushResponse(boolean)}）；
     * 支持语音回传的品牌（如臻识 3.6.6 playserver_json_request）覆写为组合响应。
     *
     * @param openGate  是否指示设备开闸
     * @param voiceText 播报文本；为空/null 时不附带语音
     */
    default JsonNode buildPushResponse(boolean openGate, String voiceText) {
        return buildPushResponse(openGate);
    }

    /**
     * 生成品牌推送响应报文并附带显示屏（LED）文字提示与语音播报（拦截类提示，如黑名单车辆）。
     * 默认忽略文字提示，仅转发 {@link #buildPushResponse(boolean, String)}；
     * 支持「文字+语音」一体下发的品牌（如臻识 0x6E 单包多行）覆写为组合响应。
     *
     * @param openGate  是否指示设备开闸
     * @param voiceText 播报文本（词组匹配控制板语音库）；为空/null 时不附带语音
     * @param ledText   显示屏文字（多行以 \n 分隔）；为空/null 时仅按语音处理
     */
    default JsonNode buildPushResponse(boolean openGate, String voiceText, String ledText) {
        return buildPushResponse(openGate, voiceText);
    }

    /**
     * 生成品牌推送响应报文（识别推送命中预排命令时）。
     * 默认按动作映射：OPEN → 开闸，其余 → 不开闸；支持 IO/串口控制的品牌可覆写
     * 以实现 CLOSE 等动作（如臻识 ivs_ioctrl 落闸）。
     */
    default JsonNode buildPushResponse(DeviceCommand command) {
        return buildPushResponse(command != null && command.getAction() == DeviceCommand.Action.OPEN);
    }

    /**
     * 生成品牌推送响应报文并附带一路语音播报（识别推送命中预排开闸时按行进方向推断的欢迎语）。
     * 默认忽略语音文本，仅转发 {@link #buildPushResponse(DeviceCommand)}；
     * 支持语音回传的品牌（如臻识）覆写为组合响应。
     *
     * @param command  排队指令（OPEN/CLOSE/HOLD_OPEN 等）
     * @param voiceText 播报文本；为空/null 时不附带语音
     */
    default JsonNode buildPushResponse(DeviceCommand command, String voiceText) {
        return buildPushResponse(command);
    }

    /**
     * 将排队指令格式化为轮询返回报文（可选，支持轮询的品牌实现）。
     * 返回品牌协议规定的 JSON：无指令时应返回普通心跳确认（如 {"status":"ok"}），
     * 有开闸指令时返回协议触发结构（如 {"Response_AlarmInfoPlate":{"info":"ok"}}）。
     */
    JsonNode buildPollResponse(DeviceCommand command);

    /**
     * 将排队指令格式化为轮询返回报文并附带一路语音播报（指令开闸按设备绑定车道方向推断的欢迎语）。
     * 默认忽略语音文本，仅转发 {@link #buildPollResponse(DeviceCommand)}；
     * 支持语音回传的品牌（如臻识）覆写为组合响应。
     *
     * @param command  排队指令（OPEN/CLOSE/HOLD_OPEN 等）
     * @param voiceText 播报文本；为空/null 时不附带语音
     */
    default JsonNode buildPollResponse(DeviceCommand command, String voiceText) {
        return buildPollResponse(command);
    }

    /**
     * 把「主板时间同步」随响应一并下发（可选，支持的品牌实现）。
     * 默认忽略（返回原报文）；臻识等支持 serialData 回传的品牌把 0x05 同步时间
     * 485 帧追加进响应，设备随本次轮询/推送转发给出入口控制主板校准 RTC。
     *
     * @param response 已生成、待返回给设备的响应报文
     */
    default JsonNode appendTimeSync(JsonNode response) {
        return response;
    }
}
