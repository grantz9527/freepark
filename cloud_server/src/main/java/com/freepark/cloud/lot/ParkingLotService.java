package com.freepark.cloud.lot;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.cloud.billing.BillingPlanService;
import com.freepark.cloud.common.exception.BusinessException;
import com.freepark.cloud.common.exception.ErrorCode;
import com.freepark.cloud.domain.BillingPlan;
import com.freepark.cloud.domain.CloudUser;
import com.freepark.cloud.domain.CloudUserRepository;
import com.freepark.cloud.domain.LotType;
import com.freepark.cloud.domain.ParkingLot;
import com.freepark.cloud.domain.ParkingLotRepository;
import com.freepark.cloud.domain.UserRole;

@Service
public class ParkingLotService {

    private final ParkingLotRepository lots;
    private final CloudUserRepository users;
    private final BillingPlanService billingPlans;

    public ParkingLotService(
            ParkingLotRepository lots,
            CloudUserRepository users,
            BillingPlanService billingPlans) {
        this.lots = lots;
        this.users = users;
        this.billingPlans = billingPlans;
    }

    @Transactional(readOnly = true)
    public List<LotView> listLots() {
        return lots.findAllByOrderByCreatedAtDesc().stream()
                .map(LotView::from)
                .toList();
    }

    @Transactional
    public LotView createLot(UUID requesterId, CreateLotRequest request) {
        requireAdmin(requesterId);
        String code = request.code().trim();
        if (lots.existsByCode(code)) {
            throw new BusinessException(ErrorCode.LOT_CODE_EXISTS);
        }
        String address = request.address() == null ? null : request.address().trim();
        if (address != null && address.isEmpty()) {
            address = null;
        }
        int totalSpaces = request.totalSpaces() == null ? 0 : request.totalSpaces();
        boolean enabled = request.enabled() == null || request.enabled();
        ParkingLot lot = new ParkingLot(
                request.name().trim(),
                code,
                request.lotType(),
                address,
                totalSpaces,
                enabled);
        return LotView.from(lots.save(lot));
    }

    @Transactional
    public LotView updateLot(UUID requesterId, UUID lotId, UpdateLotRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = lots.findById(lotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String address = request.address() == null ? null : request.address().trim();
        if (address != null && address.isEmpty()) {
            address = null;
        }
        int totalSpaces = request.totalSpaces() == null ? lot.getTotalSpaces() : request.totalSpaces();
        boolean enabled = request.enabled() == null ? lot.isEnabled() : request.enabled();
        LotType lotType = request.lotType() == null ? lot.getLotType() : request.lotType();
        lot.updateDetails(
                request.name().trim(),
                lotType,
                address,
                totalSpaces,
                enabled);
        return LotView.from(lots.save(lot));
    }

    @Transactional(readOnly = true)
    public LotInterceptView getLotIntercept(UUID lotId) {
        ParkingLot lot = lots.findById(lotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return LotInterceptView.from(lot);
    }

    @Transactional
    public LotInterceptView updateLotIntercept(UUID requesterId, UUID lotId, UpdateLotInterceptRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = lots.findById(lotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        lot.updateInterceptRules(request.entryRules(), request.exitRules());
        return LotInterceptView.from(lots.save(lot));
    }

    @Transactional(readOnly = true)
    public LotBillingView getLotBilling(UUID lotId) {
        ParkingLot lot = lots.findById(lotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        BillingPlan plan = resolveBillingPlan(lot.getBillingPlanId());
        if (plan == null) {
            return LotBillingView.from(lot, null, List.of());
        }
        return LotBillingView.from(
                lot,
                plan,
                billingPlans.rulesForPlan(plan.getId()).stream()
                        .map(com.freepark.cloud.billing.BillingPlanRuleView::from)
                        .toList());
    }

    @Transactional
    public LotBillingView updateLotBilling(UUID requesterId, UUID lotId, UpdateLotBillingRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = lots.findById(lotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        UUID planId = request.billingPlanId();
        BillingPlan plan = null;
        if (planId != null) {
            plan = billingPlans.requirePlan(planId);
            if (!plan.isEnabled()) {
                throw new BusinessException(ErrorCode.BILLING_PLAN_DISABLED);
            }
        }
        lot.assignBillingPlan(planId);
        lots.save(lot);
        if (plan == null) {
            return LotBillingView.from(lot, null, List.of());
        }
        return LotBillingView.from(
                lot,
                plan,
                billingPlans.rulesForPlan(plan.getId()).stream()
                        .map(com.freepark.cloud.billing.BillingPlanRuleView::from)
                        .toList());
    }

    private BillingPlan resolveBillingPlan(UUID planId) {
        if (planId == null) {
            return null;
        }
        return billingPlans.requirePlan(planId);
    }

    private void requireAdmin(UUID userId) {
        CloudUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
