package com.freepark.local.lot;

import java.util.List;

import com.freepark.local.domain.AccessJudgmentRuleType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAccessJudgmentRequest(
        @NotNull @Size(min = 3, max = 3) List<AccessJudgmentRuleType> ruleOrder) {
}
