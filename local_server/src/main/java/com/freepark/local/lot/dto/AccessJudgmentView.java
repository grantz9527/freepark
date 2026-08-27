package com.freepark.local.lot.dto;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.freepark.local.domain.AccessJudgmentRuleType;
import com.freepark.local.domain.ParkingLot;

public record AccessJudgmentView(List<AccessJudgmentRuleType> ruleOrder) {

    public static AccessJudgmentView from(ParkingLot lot) {
        return new AccessJudgmentView(lot.effectiveAccessJudgmentOrder());
    }

    public static List<AccessJudgmentRuleType> defaultOrder() {
        return AccessJudgmentRuleType.defaultOrder();
    }

    public static void validateOrder(List<AccessJudgmentRuleType> ruleOrder) {
        if (ruleOrder == null || ruleOrder.size() != AccessJudgmentRuleType.values().length) {
            throw new IllegalArgumentException("invalid access judgment order size");
        }
        if (!EnumSet.copyOf(ruleOrder).equals(EnumSet.allOf(AccessJudgmentRuleType.class))) {
            throw new IllegalArgumentException("invalid access judgment order values");
        }
    }
}
