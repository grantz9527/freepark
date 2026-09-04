package com.freepark.local.nodeconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 算费请求：向远程算费服务传入车牌与车牌颜色，返回费用金额。
 */
public record FeeQuoteRequest(
        @NotBlank @Size(max = 16) String plateNumber,
        @Size(max = 16) String plateColor) {
}
