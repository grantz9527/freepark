package com.freepark.cloud.lot;

import java.util.List;
import java.util.UUID;

import com.freepark.cloud.billing.BillingPlanRuleView;
import com.freepark.cloud.domain.BillingPlan;
import com.freepark.cloud.domain.ParkingLot;

public record LotBillingView(
        UUID billingPlanId,
        String billingPlanName,
        String billingPlanCode,
        String pricingDimension,
        List<BillingPlanRuleView> rules) {

    public static LotBillingView from(ParkingLot lot, BillingPlan plan, List<BillingPlanRuleView> rules) {
        if (plan == null) {
            return new LotBillingView(null, null, null, null, List.of());
        }
        return new LotBillingView(
                plan.getId(),
                plan.getName(),
                plan.getCode(),
                plan.getPricingDimension().name(),
                rules);
    }
}
