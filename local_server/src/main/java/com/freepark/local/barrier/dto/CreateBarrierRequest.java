package com.freepark.local.barrier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建设备档案请求。
 *
 * @param brand 设备品牌/协议标识（如 ZHENSHI）；驱动通道按品牌路由，可后续编辑
 * @param host  一体机命令通道地址；不填则暂不启用平台主动下发命令
 * @param port  一体机命令通道端口，不填由驱动按默认处理
 */
public record CreateBarrierRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 2, max = 64) String code,
        @Size(max = 64) String brand,
        @Size(max = 64) String host,
        @Min(1) @Max(65535) Integer port,
        Boolean enabled) {
}
