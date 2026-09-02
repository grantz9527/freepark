package com.freepark.driver.api.model;

/**
 * 驱动执行失败（设备离线、协议错误、设备拒绝等）。
 * 平台调用驱动方法时捕获并记录，不因单个设备故障中断整体流程。
 */
public class AioDriverException extends RuntimeException {

    public AioDriverException(String message) {
        super(message);
    }

    public AioDriverException(String message, Throwable cause) {
        super(message, cause);
    }
}
