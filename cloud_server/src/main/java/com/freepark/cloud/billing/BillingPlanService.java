package com.freepark.cloud.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.cloud.common.exception.BusinessException;
import com.freepark.cloud.common.exception.ErrorCode;
import com.freepark.cloud.domain.BillingMode;
import com.freepark.cloud.domain.BillingPlan;
import com.freepark.cloud.domain.BillingPlanRepository;
import com.freepark.cloud.domain.BillingPlanRule;
import com.freepark.cloud.domain.BillingPlanRuleRepository;
import com.freepark.cloud.domain.BillingPricingDimension;
import com.freepark.cloud.domain.CloudUser;
import com.freepark.cloud.domain.CloudUserRepository;
import com.freepark.cloud.domain.PlateColor;
import com.freepark.cloud.domain.UserRole;
import com.freepark.cloud.domain.VehicleType;

@Service
public class BillingPlanService {

    private final BillingPlanRepository plans;
    private final BillingPlanRuleRepository rules;
    private final CloudUserRepository users;

    public BillingPlanService(
            BillingPlanRepository plans,
            BillingPlanRuleRepository rules,
            CloudUserRepository users) {
        this.plans = plans;
        this.rules = rules;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<BillingPlanView> listPlans() {
        return plans.findAllByOrderByCreatedAtDesc().stream()
                .map(plan -> BillingPlanView.from(plan, rules.findAllByBillingPlanIdOrderByCreatedAtAsc(plan.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BillingPlan requirePlan(UUID planId) {
        return plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<BillingPlanRule> rulesForPlan(UUID planId) {
        return rules.findAllByBillingPlanIdOrderByCreatedAtAsc(planId);
    }

    @Transactional
    public BillingPlanView createPlan(UUID requesterId, CreateBillingPlanRequest request) {
        requireAdmin(requesterId);
        String code = request.code().trim();
        if (plans.existsByCode(code)) {
            throw new BusinessException(ErrorCode.BILLING_PLAN_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        BillingPricingDimension dimension = request.pricingDimension();
        validateRules(dimension, request.rules());
        BillingPlan plan = new BillingPlan(request.name().trim(), code, dimension, enabled);
        plan = plans.save(plan);
        List<BillingPlanRule> savedRules = saveRules(plan.getId(), dimension, request.rules());
        return BillingPlanView.from(plan, savedRules);
    }

    @Transactional
    public BillingPlanView updatePlan(UUID requesterId, UUID planId, UpdateBillingPlanRequest request) {
        requireAdmin(requesterId);
        BillingPlan plan = plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        boolean enabled = request.enabled() == null ? plan.isEnabled() : request.enabled();
        BillingPricingDimension dimension = request.pricingDimension() == null
                ? plan.getPricingDimension()
                : request.pricingDimension();
        validateRules(dimension, request.rules());
        plan.updateDetails(request.name().trim(), dimension, enabled);
        plan = plans.save(plan);
        rules.deleteByBillingPlanId(planId);
        List<BillingPlanRule> savedRules = saveRules(planId, dimension, request.rules());
        return BillingPlanView.from(plan, savedRules);
    }

    private List<BillingPlanRule> saveRules(
            UUID planId,
            BillingPricingDimension dimension,
            List<BillingPlanRuleRequest> ruleRequests) {
        List<BillingPlanRule> saved = new ArrayList<>();
        for (BillingPlanRuleRequest ruleRequest : ruleRequests) {
            int freeMinutes = ruleRequest.freeMinutes() == null ? 0 : ruleRequest.freeMinutes();
            BillingPlanRule rule = new BillingPlanRule(
                    planId,
                    ruleRequest.plateColor(),
                    ruleRequest.vehicleType(),
                    ruleRequest.minLengthCm(),
                    ruleRequest.maxLengthCm(),
                    ruleRequest.billingMode(),
                    freeMinutes,
                    ruleRequest.hourlyRate(),
                    ruleRequest.dailyCap(),
                    ruleRequest.monthlyRate());
            saved.add(rules.save(rule));
        }
        return saved;
    }

    private void validateRules(BillingPricingDimension dimension, List<BillingPlanRuleRequest> ruleRequests) {
        for (BillingPlanRuleRequest rule : ruleRequests) {
            switch (dimension) {
                case PLATE_COLOR -> {
                    if (rule.plateColor() == null) {
                        throw new BusinessException(ErrorCode.BILLING_RULE_PLATE_COLOR_REQUIRED);
                    }
                    if (rule.vehicleType() != null || rule.minLengthCm() != null || rule.maxLengthCm() != null) {
                        throw new BusinessException(ErrorCode.BILLING_RULE_INVALID_DIMENSION);
                    }
                }
                case VEHICLE_TYPE -> {
                    if (rule.vehicleType() == null) {
                        throw new BusinessException(ErrorCode.BILLING_RULE_VEHICLE_TYPE_REQUIRED);
                    }
                    if (rule.plateColor() != null || rule.minLengthCm() != null || rule.maxLengthCm() != null) {
                        throw new BusinessException(ErrorCode.BILLING_RULE_INVALID_DIMENSION);
                    }
                }
                case VEHICLE_LENGTH -> {
                    if (rule.minLengthCm() == null) {
                        throw new BusinessException(ErrorCode.BILLING_RULE_LENGTH_REQUIRED);
                    }
                    if (rule.maxLengthCm() != null && rule.maxLengthCm() < rule.minLengthCm()) {
                        throw new BusinessException(ErrorCode.BILLING_RULE_LENGTH_INVALID);
                    }
                    if (rule.plateColor() != null || rule.vehicleType() != null) {
                        throw new BusinessException(ErrorCode.BILLING_RULE_INVALID_DIMENSION);
                    }
                }
            }
            if (rule.billingMode() == BillingMode.TEMPORARY && rule.hourlyRate() == null) {
                throw new BusinessException(ErrorCode.BILLING_RULE_HOURLY_RATE_REQUIRED);
            }
            if (rule.billingMode() == BillingMode.MONTHLY && rule.monthlyRate() == null) {
                throw new BusinessException(ErrorCode.BILLING_RULE_MONTHLY_RATE_REQUIRED);
            }
        }
    }

    private void requireAdmin(UUID userId) {
        CloudUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
