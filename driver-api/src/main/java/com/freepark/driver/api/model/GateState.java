package com.freepark.driver.api.model;

/** 道闸状态。 */
public enum GateState {
    /** 抬起（放行） */
    OPEN,
    /** 落下（拦截） */
    CLOSED,
    /** 常开模式（持续放行） */
    ALWAYS_OPEN,
    /** 未知/中间态 */
    UNKNOWN,
    /** 故障 */
    FAULT
}
