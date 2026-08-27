package com.freepark.local.device.protocol;

import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.RecognitionRecord;

/**
 * 识别设备品牌协议适配：解析上报报文、生成轮询返回指令。
 * 不同品牌相机报文格式不同，各自实现本接口。
 */
public interface CameraProtocol {

    /** 本协议支持的品牌标识（与 ParkingBarrier.brand 对应）。 */
    String brand();

    /** 将上报请求解析为识别记录。 */
    RecognitionRecord parseRecognize(ParkingBarrier device, com.freepark.local.device.dto.DeviceRecognizeRequest request);

    /** 将排队指令格式化为本品牌轮询返回报文。无指令时返回空。 */
    DevicePollResponse buildPollResponse(DeviceCommand command);
}
