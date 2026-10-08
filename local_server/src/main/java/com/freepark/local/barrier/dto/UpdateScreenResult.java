package com.freepark.local.barrier.dto;

import java.util.UUID;

/** 显示屏配置保存结果。commandId 为空表示已落库但没有排队下发。 */
public record UpdateScreenResult(BarrierView device, UUID commandId) {
}
