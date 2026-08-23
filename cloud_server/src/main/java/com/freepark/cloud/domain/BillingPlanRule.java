package com.freepark.cloud.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "billing_plan_rule")
public class BillingPlanRule extends BaseEntity {

    @Column(name = "billing_plan_id", nullable = false)
    private UUID billingPlanId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PlateColor plateColor;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private VehicleType vehicleType;

    @Column(name = "min_length_cm")
    private Integer minLengthCm;

    @Column(name = "max_length_cm")
    private Integer maxLengthCm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BillingMode billingMode = BillingMode.TEMPORARY;

    @Column(nullable = false)
    private int freeMinutes = 0;

    @Column(precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal dailyCap;

    @Column(precision = 12, scale = 2)
    private BigDecimal monthlyRate;

    protected BillingPlanRule() {
    }

    public BillingPlanRule(
            UUID billingPlanId,
            PlateColor plateColor,
            VehicleType vehicleType,
            Integer minLengthCm,
            Integer maxLengthCm,
            BillingMode billingMode,
            int freeMinutes,
            BigDecimal hourlyRate,
            BigDecimal dailyCap,
            BigDecimal monthlyRate) {
        this.billingPlanId = billingPlanId;
        this.plateColor = plateColor;
        this.vehicleType = vehicleType;
        this.minLengthCm = minLengthCm;
        this.maxLengthCm = maxLengthCm;
        this.billingMode = billingMode;
        this.freeMinutes = freeMinutes;
        this.hourlyRate = hourlyRate;
        this.dailyCap = dailyCap;
        this.monthlyRate = monthlyRate;
    }

    public UUID getBillingPlanId() {
        return billingPlanId;
    }

    public PlateColor getPlateColor() {
        return plateColor;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public Integer getMinLengthCm() {
        return minLengthCm;
    }

    public Integer getMaxLengthCm() {
        return maxLengthCm;
    }

    public BillingMode getBillingMode() {
        return billingMode;
    }

    public int getFreeMinutes() {
        return freeMinutes;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public BigDecimal getDailyCap() {
        return dailyCap;
    }

    public BigDecimal getMonthlyRate() {
        return monthlyRate;
    }
}
