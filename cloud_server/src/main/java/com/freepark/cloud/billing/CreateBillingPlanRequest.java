package com.freepark.cloud.billing;

import com.freepark.cloud.domain.BillingPricingDimension;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBillingPlanRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 2, max = 64) String code,
        @NotNull BillingPricingDimension pricingDimension,
        Boolean enabled,
        @NotEmpty List<@Valid BillingPlanRuleRequest> rules) {
}
