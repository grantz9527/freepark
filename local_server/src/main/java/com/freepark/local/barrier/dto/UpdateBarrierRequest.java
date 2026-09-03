package com.freepark.local.barrier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新设备档案请求（PUT 全量语义）：name 必填；enabled 传 null 保持原值；
 * brand/model/host/port 直接覆盖为请求值（null/缺省即清空），调用方应回填当前值后整体提交。
 */
public record UpdateBarrierRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 64) String brand,
        @Size(max = 64) String model,
        @Size(max = 64) String host,
        @Min(1) @Max(65535) Integer port,
        Boolean enabled) {
}
