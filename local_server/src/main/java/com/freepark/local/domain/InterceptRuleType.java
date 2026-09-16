package com.freepark.local.domain;

public enum InterceptRuleType {
    ARREARS,
    BLACKLIST,
    /** 满位拦截：仅入口生效，在场车辆数达到车位总数时禁止入场 */
    FULL
}
