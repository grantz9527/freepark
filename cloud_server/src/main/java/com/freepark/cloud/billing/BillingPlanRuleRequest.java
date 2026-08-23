package com.freepark.cloud.billing;

import java.math.BigDecimal;

import com.freepark.cloud.domain.BillingMode;
import com.freepark.cloud.domain.PlateColor;
import com.freepark.cloud.domain.VehicleType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BillingPlanRuleRequest(
        PlateColor plateColor,
        VehicleType vehicleType,
        @Min(0) Integer minLengthCm,
        @Min(0) Integer maxLengthCm,
        @NotNull BillingMode billingMode,
        @Min(0) Integer freeMinutes,
        @DecimalMin("0") BigDecimal hourlyRate,
        @DecimalMin("0") BigDecimal dailyCap,
        @DecimalMin("0") BigDecimal monthlyRate) {
}
