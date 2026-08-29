package com.freepark.local.recognition.dto;

import com.freepark.local.parkingflow.dto.ParkingSessionView;

/**
 * 识别记录应用到停车流水的联动结果。
 * kind 取值：entry / exit_matched / exit_unmatched / skipped（与前端约定一致）。
 */
public record ParkingFlowResult(String kind, ParkingSessionView session) {

    public static ParkingFlowResult entry(ParkingSessionView session) {
        return new ParkingFlowResult("entry", session);
    }

    public static ParkingFlowResult exitMatched(ParkingSessionView session) {
        return new ParkingFlowResult("exit_matched", session);
    }

    public static ParkingFlowResult exitUnmatched() {
        return new ParkingFlowResult("exit_unmatched", null);
    }

    public static ParkingFlowResult skipped() {
        return new ParkingFlowResult("skipped", null);
    }
}
