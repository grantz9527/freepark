package com.freepark.local.lot.service;

import com.freepark.local.lot.dto.AccessJudgmentView;
import com.freepark.local.lot.dto.CreateLotRequest;
import com.freepark.local.lot.dto.LotInterceptView;
import com.freepark.local.lot.dto.LotOpenTimeRule;
import com.freepark.local.lot.dto.LotView;
import com.freepark.local.lot.dto.UpdateAccessJudgmentRequest;
import com.freepark.local.lot.dto.UpdateLotInterceptRequest;
import com.freepark.local.lot.dto.UpdateLotRequest;
import com.freepark.local.lot.support.LotOpenTimeRules;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.LotType;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.UserRole;

@Service
public class ParkingLotService {

    private final ParkingLotRepository lots;
    private final LocalUserRepository users;

    public ParkingLotService(ParkingLotRepository lots, LocalUserRepository users) {
        this.lots = lots;
        this.users = users;
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
        lot.updateOpenTimeRules(resolveOpenTimeRules(request.lotType(), request.openTimeRules()));
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
        if (lotType != LotType.INTERNAL) {
            // 仅内部车场支持对外开放时段，类型变更后原有的开放时段随之失效
            lot.updateOpenTimeRules(null);
        } else if (request.openTimeRules() != null) {
            lot.updateOpenTimeRules(resolveOpenTimeRules(lotType, request.openTimeRules()));
        }
        if (request.mapData() != null) {
            lot.updateMapData(request.mapData());
        }
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
    public AccessJudgmentView getAccessJudgment(UUID lotId) {
        ParkingLot lot = lots.findById(lotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return AccessJudgmentView.from(lot);
    }

    @Transactional
    public AccessJudgmentView updateAccessJudgment(
            UUID requesterId, UUID lotId, UpdateAccessJudgmentRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = lots.findById(lotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        try {
            AccessJudgmentView.validateOrder(request.ruleOrder());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.ACCESS_JUDGMENT_INVALID_ORDER);
        }
        lot.updateAccessJudgmentOrder(request.ruleOrder());
        return AccessJudgmentView.from(lots.save(lot));
    }

    /** 开放时段请求 → 入库 JSON：仅内部车场保存，未配置时段或非内部车场返回 null。 */
    private static String resolveOpenTimeRules(LotType lotType, List<LotOpenTimeRule> rules) {
        if (lotType != LotType.INTERNAL || rules == null || rules.isEmpty()) {
            return null;
        }
        List<LotOpenTimeRules.Window> windows = new ArrayList<>(rules.size());
        for (LotOpenTimeRule rule : rules) {
            Integer day = rule == null ? null : rule.day();
            LocalTime start = rule == null ? null : LotOpenTimeRules.parseTime(rule.start());
            LocalTime end = rule == null ? null : LotOpenTimeRules.parseTime(rule.end());
            if (day == null || day < 1 || day > 7 || start == null || end == null || !start.isBefore(end)) {
                throw new BusinessException(ErrorCode.LOT_OPEN_TIME_INVALID);
            }
            windows.add(new LotOpenTimeRules.Window(day, start, end));
        }
        return LotOpenTimeRules.serialize(windows);
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
