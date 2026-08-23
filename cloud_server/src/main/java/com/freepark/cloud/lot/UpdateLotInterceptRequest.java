package com.freepark.cloud.lot;

import java.util.List;

import com.freepark.cloud.domain.InterceptRuleType;

import jakarta.validation.constraints.NotNull;

public record UpdateLotInterceptRequest(
        @NotNull List<InterceptRuleType> entryRules,
        @NotNull List<InterceptRuleType> exitRules) {
}
