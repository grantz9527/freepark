package com.freepark.local.barrier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建设备档案请求。
 *
 * @param brand 设备品牌/协议标识（如 ZHENSHI）；驱动通道按品牌路由，可后续编辑
 * @param model 品牌下具体型号（如 YELLOW_CARD_LED_LINE4），驱动按型号路由；空表示整条产品线通配
 * @param host  一体机命令通道地址；不填则暂不启用平台主动下发命令
 * @param port  已废弃，可空
 * @param streamUrl 完整视频流地址（RTSP 等），各厂商路径不同
 */
public record CreateBarrierRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 2, max = 64) String code,
        @Size(max = 64) String brand,
        @Size(max = 64) String model,
        @Size(max = 64) String host,
        @Min(1) @Max(65535) Integer port,
        @Size(max = 512) String streamUrl,
        Boolean enabled) {
}
