package com.freepark.cloud.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, UUID> {

    boolean existsByCode(String code);

    List<BillingPlan> findAllByOrderByCreatedAtDesc();
}
