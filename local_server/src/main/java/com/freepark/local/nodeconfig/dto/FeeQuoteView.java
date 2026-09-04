package com.freepark.local.nodeconfig.dto;

import java.math.BigDecimal;

/**
 * 算费结果：远程算费服务返回的费用金额。
 */
public record FeeQuoteView(BigDecimal amount) {
}
