package com.freepark.cloud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "billing_plan")
public class BillingPlan extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BillingPricingDimension pricingDimension = BillingPricingDimension.PLATE_COLOR;

    @Column(nullable = false)
    private boolean enabled = true;

    protected BillingPlan() {
    }

    public BillingPlan(String name, String code, BillingPricingDimension pricingDimension, boolean enabled) {
        this.name = name;
        this.code = code;
        this.pricingDimension = pricingDimension;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public BillingPricingDimension getPricingDimension() {
        return pricingDimension;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateDetails(String name, BillingPricingDimension pricingDimension, boolean enabled) {
        this.name = name;
        this.pricingDimension = pricingDimension;
        this.enabled = enabled;
    }
}
