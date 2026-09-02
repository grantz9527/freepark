package com.freepark.driver.api.model;

/** 设备在线状态快照。 */
public record DeviceStatus(
        boolean online,
        GateState gateState,
        String message,
        long epochMillis) {

    public static DeviceStatus online(GateState gateState, String message) {
        return new DeviceStatus(true, gateState, message, System.currentTimeMillis());
    }

    public static DeviceStatus offline(String message) {
        return new DeviceStatus(false, GateState.UNKNOWN, message, System.currentTimeMillis());
    }
}
