package com.freepark.cloud.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.cloud.billing.BillingPlanService;
import com.freepark.cloud.billing.BillingPlanView;
import com.freepark.cloud.billing.CreateBillingPlanRequest;
import com.freepark.cloud.billing.UpdateBillingPlanRequest;
import com.freepark.cloud.common.api.ApiResponse;
import com.freepark.cloud.common.i18n.MessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/billing-plans")
public class BillingPlanController {

    private final BillingPlanService billingPlanService;
    private final MessageService messages;

    public BillingPlanController(BillingPlanService billingPlanService, MessageService messages) {
        this.billingPlanService = billingPlanService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<BillingPlanView>> list() {
        return ApiResponse.ok(messages, billingPlanService.listPlans());
    }

    @PostMapping
    public ApiResponse<BillingPlanView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBillingPlanRequest request) {
        return ApiResponse.ok(messages, billingPlanService.createPlan(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping("/{planId}")
    public ApiResponse<BillingPlanView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateBillingPlanRequest request) {
        return ApiResponse.ok(messages, billingPlanService.updatePlan(UUID.fromString(jwt.getSubject()), planId, request));
    }
}
