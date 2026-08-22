package com.freepark.local.lot;

import java.util.List;

import com.freepark.local.domain.InterceptRuleType;

import jakarta.validation.constraints.NotNull;

public record UpdateLotInterceptRequest(
        @NotNull List<InterceptRuleType> entryRules,
        @NotNull List<InterceptRuleType> exitRules) {
}
