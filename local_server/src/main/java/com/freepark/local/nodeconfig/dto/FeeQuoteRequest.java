package com.freepark.local.nodeconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 算费请求：向远程算费服务传入车场编码、车牌与车牌颜色，返回费用金额。
 * {@code lotCode} 可选：不传时由算费服务按全局口径统计。
 */
public record FeeQuoteRequest(
        @Size(max = 64) String lotCode,
        @NotBlank @Size(max = 16) String plateNumber,
        @Size(max = 16) String plateColor) {
}
