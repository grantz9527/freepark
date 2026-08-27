package com.freepark.local.device.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 设备轮询返回：待执行指令。
 * 无指令时 action 为 "none"，设备据此空转。
 */
public record DevicePollResponse(String action, UUID commandId, Instant issuedAt) {

    public static DevicePollResponse empty() {
        return new DevicePollResponse("none", null, null);
    }

    public static DevicePollResponse of(String action, UUID commandId, Instant issuedAt) {
        return new DevicePollResponse(action, commandId, issuedAt);
    }
}
