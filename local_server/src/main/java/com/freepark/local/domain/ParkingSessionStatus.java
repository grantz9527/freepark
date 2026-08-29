package com.freepark.local.domain;

/** 停车流水状态：在场（OPEN）/ 已出场（CLOSED）/ 已作废（VOIDED）。 */
public enum ParkingSessionStatus {
    OPEN,
    CLOSED,
    VOIDED
}
