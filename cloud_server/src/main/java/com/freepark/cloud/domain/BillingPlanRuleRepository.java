package com.freepark.cloud.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRuleRepository extends JpaRepository<BillingPlanRule, UUID> {

    List<BillingPlanRule> findAllByBillingPlanIdOrderByCreatedAtAsc(UUID billingPlanId);

    void deleteByBillingPlanId(UUID billingPlanId);
}
