package com.freepark.driver.api;

import java.util.List;

import com.freepark.driver.api.model.DeviceStatus;
import com.freepark.driver.api.model.DisplayMessage;
import com.freepark.driver.api.model.GateState;
import com.freepark.driver.api.model.RecognitionEvent;
import com.freepark.driver.api.model.VoiceMessage;

/**
 * 出入口识别一体机【统一功能规范】。
 *
 * <p>平台业务侧只面向本规范编程：不感知任何厂商私有协议。
 * 一个完整的一体机应实现：道闸控制（开闸/落闸/常开）、
 * LED/LCD 屏显示、语音播报、识别上报、状态查询。
 *
 * <p>驱动包由各厂商独立开发并实现本接口；
 * 每台物理设备对应一个本接口实例（见 {@link AIODriverFactory#create}）。
 *
 * <p>若某型号确实不具备某项能力（如纯识别相机无 LED），
 * 驱动工厂在 capabilities() 中声明缺失项；平台编排前应查询，
 * 避免调用到抛 UnsupportedOperationException 的实现。
 */
public interface ParkingAIODevice {

    /** 该实例绑定的物理设备唯一键（通常为序列号）。 */
    String deviceKey();

    // ==================== 道闸控制 ====================

    /** 触发开闸（放行）。 */
    void openGate();

    /** 落闸（拦截）。 */
    void closeGate();

    /** 常开 / 解除常开。高峰车流时可保持常开。 */
    void setAlwaysOpen(boolean alwaysOpen);

    /** 查询当前闸态。 */
    GateState gateState();

    // ==================== LED / LCD 屏显示 ====================

    /** 下发屏显语义内容（欢迎语/车牌/余位/自由文本），协议翻译由驱动完成。 */
    void show(DisplayMessage message);

    // ==================== 语音播报 ====================

    /** 下发语音播报语义内容。 */
    void speak(VoiceMessage voice);

    // ==================== 识别上报 ====================

    /** 拉取该设备自识别到的新车牌（平台轮询调用，返回后清空内部队列）。 */
    List<RecognitionEvent> pullRecognition();

    // ==================== 状态 ====================

    /** 查询在线状态与闸态快照。 */
    DeviceStatus status();
}
