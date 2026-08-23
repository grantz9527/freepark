package com.freepark.cloud.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.freepark.cloud.domain.BillingPlan;
import com.freepark.cloud.domain.BillingPlanRule;

public record BillingPlanView(
        UUID id,
        String name,
        String code,
        String pricingDimension,
        boolean enabled,
        List<BillingPlanRuleView> rules,
        Instant createdAt,
        Instant updatedAt) {

    public static BillingPlanView from(BillingPlan plan, List<BillingPlanRule> rules) {
        return new BillingPlanView(
                plan.getId(),
                plan.getName(),
                plan.getCode(),
                plan.getPricingDimension().name(),
                plan.isEnabled(),
                rules.stream().map(BillingPlanRuleView::from).toList(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }
}
