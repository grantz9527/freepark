package com.freepark.local.lane;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.LaneType;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.UserRole;

@Service
public class ParkingLaneService {

    private final ParkingLotRepository lots;
    private final ParkingLaneRepository lanes;
    private final LocalUserRepository users;

    public ParkingLaneService(
            ParkingLotRepository lots, ParkingLaneRepository lanes, LocalUserRepository users) {
        this.lots = lots;
        this.lanes = lanes;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<LaneView> listLanes(UUID lotId) {
        List<ParkingLane> items =
                lotId == null
                        ? lanes.findAllByOrderByCreatedAtDesc()
                        : lanes.findAllByLot_IdOrLinkedLot_IdOrderByCreatedAtDesc(lotId, lotId);
        return items.stream().map(LaneView::from).toList();
    }

    @Transactional
    public LaneView createLane(UUID requesterId, CreateLaneRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(request.lotId());
        ParkingLot linkedLot = resolveLinkedLot(request.lotId(), request.linkedLotId());
        String code = request.code().trim();
        if (lanes.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.LANE_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        ParkingLane lane = new ParkingLane(lot, linkedLot, request.name(), code, request.laneType(), enabled);
        return LaneView.from(lanes.save(lane));
    }

    @Transactional
    public LaneView updateLane(UUID requesterId, UUID laneId, UpdateLaneRequest request) {
        requireAdmin(requesterId);
        ParkingLane lane = requireLane(laneId);
        ParkingLot lot = requireLot(request.lotId());
        ParkingLot linkedLot = resolveLinkedLot(request.lotId(), request.linkedLotId());
        boolean enabled = request.enabled() == null ? lane.isEnabled() : request.enabled();
        LaneType laneType = request.laneType() == null ? lane.getLaneType() : request.laneType();
        lane.updateDetails(lot, linkedLot, request.name(), laneType, enabled);
        return LaneView.from(lanes.save(lane));
    }

    private ParkingLot resolveLinkedLot(UUID lotId, UUID linkedLotId) {
        if (linkedLotId == null) {
            return null;
        }
        if (linkedLotId.equals(lotId)) {
            throw new BusinessException(ErrorCode.LANE_LOTS_DUPLICATE);
        }
        return requireLot(linkedLotId);
    }

    private ParkingLane requireLane(UUID laneId) {
        return lanes.findById(laneId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private ParkingLot requireLot(UUID lotId) {
        return lots.findById(lotId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
