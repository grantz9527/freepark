package com.freepark.local.lot;

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

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
