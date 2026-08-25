package com.freepark.local.domain;

import java.util.List;

public enum AccessJudgmentRuleType {
    PATTERN_ALLOWLIST,
    BLACKLIST,
    WHITELIST;

    public static List<AccessJudgmentRuleType> defaultOrder() {
        return List.of(BLACKLIST, WHITELIST, PATTERN_ALLOWLIST);
    }
}
