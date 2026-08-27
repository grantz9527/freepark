package com.freepark.local.accessdecision.service;

import com.freepark.local.accessdecision.dto.AccessDecisionRequest;
import com.freepark.local.accessdecision.dto.AccessDecisionView;
import com.freepark.local.accessdecision.dto.AccessDirection;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.domain.AccessJudgmentRuleType;
import com.freepark.local.domain.BlacklistVehicleRepository;
import com.freepark.local.domain.InternalVehicleRepository;
import com.freepark.local.domain.LotType;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.PatternAllowlist;
import com.freepark.local.domain.PatternAllowlistRepository;
import com.freepark.local.domain.WhitelistVehicleRepository;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;

/**
 * Decides whether a lane event should be allowed or intercepted.
 *
 * <p>Evaluation order:
 * <ol>
 *   <li>Access judgment rules in the lot's configured order (WHITELIST /
 *       BLACKLIST / PATTERN_ALLOWLIST); the first matching rule decides.</li>
 *   <li>For INTERNAL lots on entry: the plate must be a registered internal vehicle.</li>
 *   <li>Lane plate-color intercept (provided by the caller when available).</li>
 *   <li>Exit without an open in-lot session: still allowed, flagged in remark.</li>
 * </ol>
 */
@Service
public class AccessDecisionService {

    private final ParkingLotRepository lots;
    private final ParkingLaneRepository lanes;
    private final InternalVehicleRepository internalVehicles;
    private final WhitelistVehicleRepository whitelistVehicles;
    private final BlacklistVehicleRepository blacklistVehicles;
    private final PatternAllowlistRepository patternAllowlist;

    public AccessDecisionService(
            ParkingLotRepository lots,
            ParkingLaneRepository lanes,
            InternalVehicleRepository internalVehicles,
            WhitelistVehicleRepository whitelistVehicles,
            BlacklistVehicleRepository blacklistVehicles,
            PatternAllowlistRepository patternAllowlist) {
        this.lots = lots;
        this.lanes = lanes;
        this.internalVehicles = internalVehicles;
        this.whitelistVehicles = whitelistVehicles;
        this.blacklistVehicles = blacklistVehicles;
        this.patternAllowlist = patternAllowlist;
    }

    @Transactional(readOnly = true)
    public AccessDecisionView decide(UUID lotId, AccessDecisionRequest request) {
        ParkingLot lot = requireLot(lotId);
        requireLane(lotId, request.laneId());

        String plate = request.plateNumber().trim().toUpperCase();
        boolean isEntry = request.direction() == AccessDirection.ENTRANCE;

        // 1. Access judgment rules in configured order; first match wins.
        boolean whitelisted = whitelistVehicles.existsByLotIdAndPlateNumberIgnoreCaseAndEnabledTrue(lotId, plate);
        boolean blacklisted = blacklistVehicles.existsByLotIdAndPlateNumberIgnoreCaseAndEnabledTrue(lotId, plate);
        boolean interceptBlacklisted = isEntry ? lot.isEntryInterceptBlacklist() : lot.isExitInterceptBlacklist();
        boolean patternMatched = matchesPattern(lotId, plate);

        for (AccessJudgmentRuleType rule : lot.effectiveAccessJudgmentOrder()) {
            if (rule == AccessJudgmentRuleType.WHITELIST && whitelisted) {
                return AccessDecisionView.allowed("whitelist_match");
            }
            if (rule == AccessJudgmentRuleType.BLACKLIST && interceptBlacklisted && blacklisted) {
                return AccessDecisionView.intercepted("blacklisted_vehicle");
            }
            if (rule == AccessJudgmentRuleType.PATTERN_ALLOWLIST && patternMatched) {
                return AccessDecisionView.allowed("pattern_allowlist_match");
            }
        }

        // 2. Internal lot entry requires a registered internal vehicle.
        if (isEntry
                && lot.getLotType() == LotType.INTERNAL
                && !internalVehicles.existsByLotIdAndPlateNumberIgnoreCaseAndEnabledTrue(lotId, plate)) {
            return AccessDecisionView.intercepted("not_internal_vehicle");
        }

        // 3. Lane plate-color intercept (provided by the caller when available).
        if (request.interceptColors() != null && request.interceptColors().contains(request.plateColor())) {
            return AccessDecisionView.intercepted("plate_color_intercept");
        }

        // 4. Exit without an open in-lot session is allowed but flagged.
        if (!isEntry && Boolean.FALSE.equals(request.hasOpenSession())) {
            return AccessDecisionView.allowed("no_open_session");
        }

        return AccessDecisionView.allowed("");
    }

    private boolean matchesPattern(UUID lotId, String plate) {
        for (PatternAllowlist entry : patternAllowlist.findByLotIdAndEnabledTrue(lotId)) {
            try {
                if (Pattern.compile(entry.getPattern()).matcher(plate).find()) {
                    return true;
                }
            } catch (PatternSyntaxException ignored) {
                // Patterns are validated on save; skip any invalid leftovers.
            }
        }
        return false;
    }

    private ParkingLot requireLot(UUID lotId) {
        return lots.findById(lotId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void requireLane(UUID lotId, UUID laneId) {
        ParkingLane lane = lanes.findById(laneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!lane.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
