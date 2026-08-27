package com.freepark.local.lot.dto;

import java.util.List;

import com.freepark.local.domain.InterceptRuleType;
import com.freepark.local.domain.ParkingLot;

public record LotInterceptView(List<InterceptRuleType> entryRules, List<InterceptRuleType> exitRules) {

    public static LotInterceptView from(ParkingLot lot) {
        return new LotInterceptView(
                rulesForEntry(lot),
                rulesForExit(lot));
    }

    private static List<InterceptRuleType> rulesForEntry(ParkingLot lot) {
        return buildRules(lot.isEntryInterceptArrears(), lot.isEntryInterceptBlacklist());
    }

    private static List<InterceptRuleType> rulesForExit(ParkingLot lot) {
        return buildRules(lot.isExitInterceptArrears(), lot.isExitInterceptBlacklist());
    }

    private static List<InterceptRuleType> buildRules(boolean arrears, boolean blacklist) {
        java.util.ArrayList<InterceptRuleType> rules = new java.util.ArrayList<>();
        if (arrears) {
            rules.add(InterceptRuleType.ARREARS);
        }
        if (blacklist) {
            rules.add(InterceptRuleType.BLACKLIST);
        }
        return rules;
    }
}
