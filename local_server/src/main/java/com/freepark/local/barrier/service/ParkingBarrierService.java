package com.freepark.local.barrier.service;

import com.freepark.local.barrier.dto.BarrierView;
import com.freepark.local.barrier.dto.CreateBarrierRequest;
import com.freepark.local.barrier.dto.UpdateBarrierRequest;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.UserRole;

@Service
public class ParkingBarrierService {

    private final ParkingLaneRepository lanes;
    private final ParkingBarrierRepository barriers;
    private final LocalUserRepository users;

    public ParkingBarrierService(
            ParkingLaneRepository lanes, ParkingBarrierRepository barriers, LocalUserRepository users) {
        this.lanes = lanes;
        this.barriers = barriers;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<BarrierView> listBarriers(UUID laneId) {
        requireLane(laneId);
        return barriers.findAllByLaneIdOrderByCreatedAtDesc(laneId).stream()
                .map(BarrierView::from)
                .toList();
    }

    @Transactional
    public BarrierView createBarrier(UUID requesterId, UUID laneId, CreateBarrierRequest request) {
        requireAdmin(requesterId);
        ParkingLane lane = requireLane(laneId);
        String code = request.code().trim();
        if (barriers.existsByLaneIdAndCodeIgnoreCase(laneId, code)) {
            throw new BusinessException(ErrorCode.BARRIER_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        ParkingBarrier barrier = new ParkingBarrier(lane, request.name(), code, enabled);
        return BarrierView.from(barriers.save(barrier));
    }

    @Transactional
    public BarrierView updateBarrier(
            UUID requesterId, UUID laneId, UUID barrierId, UpdateBarrierRequest request) {
        requireAdmin(requesterId);
        requireLane(laneId);
        ParkingBarrier barrier = requireBarrier(laneId, barrierId);
        boolean enabled = request.enabled() == null ? barrier.isEnabled() : request.enabled();
        barrier.updateDetails(request.name(), enabled);
        return BarrierView.from(barriers.save(barrier));
    }

    private ParkingBarrier requireBarrier(UUID laneId, UUID barrierId) {
        ParkingBarrier barrier = barriers.findById(barrierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!barrier.getLane().getId().equals(laneId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return barrier;
    }

    private ParkingLane requireLane(UUID laneId) {
        return lanes.findById(laneId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
