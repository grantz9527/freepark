package com.freepark.cloud.billing;

import java.math.BigDecimal;
import java.util.UUID;

import com.freepark.cloud.domain.BillingMode;
import com.freepark.cloud.domain.BillingPlanRule;
import com.freepark.cloud.domain.PlateColor;
import com.freepark.cloud.domain.VehicleType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BillingPlanRuleView(
        UUID id,
        String plateColor,
        String vehicleType,
        Integer minLengthCm,
        Integer maxLengthCm,
        String billingMode,
        int freeMinutes,
        BigDecimal hourlyRate,
        BigDecimal dailyCap,
        BigDecimal monthlyRate) {

    public static BillingPlanRuleView from(BillingPlanRule rule) {
        return new BillingPlanRuleView(
                rule.getId(),
                rule.getPlateColor() == null ? null : rule.getPlateColor().name(),
                rule.getVehicleType() == null ? null : rule.getVehicleType().name(),
                rule.getMinLengthCm(),
                rule.getMaxLengthCm(),
                rule.getBillingMode().name(),
                rule.getFreeMinutes(),
                rule.getHourlyRate(),
                rule.getDailyCap(),
                rule.getMonthlyRate());
    }
}
