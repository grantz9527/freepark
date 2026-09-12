package com.freepark.local.nodeconfig.dto;

import java.math.BigDecimal;

/**
 * 算费结果：费用金额，以及本机完成本次请求的耗时（毫秒）。
 */
public record FeeQuoteView(BigDecimal amount, long elapsedMs) {
}
