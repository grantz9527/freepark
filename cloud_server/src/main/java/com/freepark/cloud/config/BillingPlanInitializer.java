package com.freepark.cloud.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.freepark.cloud.domain.BillingMode;
import com.freepark.cloud.domain.BillingPlan;
import com.freepark.cloud.domain.BillingPlanRepository;
import com.freepark.cloud.domain.BillingPlanRule;
import com.freepark.cloud.domain.BillingPlanRuleRepository;
import com.freepark.cloud.domain.BillingPricingDimension;
import com.freepark.cloud.domain.PlateColor;
import com.freepark.cloud.domain.VehicleType;

@Component
public class BillingPlanInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BillingPlanInitializer.class);

    private final BillingPlanRepository plans;
    private final BillingPlanRuleRepository rules;

    public BillingPlanInitializer(BillingPlanRepository plans, BillingPlanRuleRepository rules) {
        this.plans = plans;
        this.rules = rules;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (plans.count() > 0) {
            return;
        }

        BillingPlan platePlan = plans.save(new BillingPlan(
                "按车牌颜色临停",
                "by_plate_color",
                BillingPricingDimension.PLATE_COLOR,
                true));
        rules.save(new BillingPlanRule(
                platePlan.getId(),
                PlateColor.BLUE,
                null,
                null,
                null,
                BillingMode.TEMPORARY,
                15,
                new BigDecimal("4.00"),
                new BigDecimal("35.00"),
                null));
        rules.save(new BillingPlanRule(
                platePlan.getId(),
                PlateColor.YELLOW,
                null,
                null,
                null,
                BillingMode.TEMPORARY,
                15,
                new BigDecimal("6.00"),
                new BigDecimal("50.00"),
                null));
        rules.save(new BillingPlanRule(
                platePlan.getId(),
                PlateColor.GREEN,
                null,
                null,
                null,
                BillingMode.MONTHLY,
                0,
                null,
                null,
                new BigDecimal("280.00")));

        BillingPlan lengthPlan = plans.save(new BillingPlan(
                "按车长临停",
                "by_vehicle_length",
                BillingPricingDimension.VEHICLE_LENGTH,
                true));
        rules.save(new BillingPlanRule(
                lengthPlan.getId(),
                null,
                null,
                0,
                450,
                BillingMode.TEMPORARY,
                15,
                new BigDecimal("4.00"),
                new BigDecimal("30.00"),
                null));
        rules.save(new BillingPlanRule(
                lengthPlan.getId(),
                null,
                null,
                451,
                null,
                BillingMode.TEMPORARY,
                15,
                new BigDecimal("8.00"),
                new BigDecimal("60.00"),
                null));

        BillingPlan typePlan = plans.save(new BillingPlan(
                "按车辆类型",
                "by_vehicle_type",
                BillingPricingDimension.VEHICLE_TYPE,
                true));
        rules.save(new BillingPlanRule(
                typePlan.getId(),
                null,
                VehicleType.SMALL_CAR,
                null,
                null,
                BillingMode.TEMPORARY,
                15,
                new BigDecimal("5.00"),
                new BigDecimal("40.00"),
                null));
        rules.save(new BillingPlanRule(
                typePlan.getId(),
                null,
                VehicleType.HEAVY_TRUCK,
                null,
                null,
                BillingMode.TEMPORARY,
                15,
                new BigDecimal("12.00"),
                new BigDecimal("80.00"),
                null));

        log.info("Created default billing plans");
    }
}
