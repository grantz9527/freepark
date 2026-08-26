package com.freepark.local.booth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.api.PageView;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingBooth;
import com.freepark.local.domain.ParkingBoothRepository;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.UserRole;

import jakarta.persistence.criteria.Predicate;

@Service
public class ParkingBoothService {

    private final ParkingLotRepository lots;
    private final ParkingBoothRepository booths;
    private final ParkingLaneRepository lanes;
    private final LocalUserRepository users;

    public ParkingBoothService(
            ParkingLotRepository lots,
            ParkingBoothRepository booths,
            ParkingLaneRepository lanes,
            LocalUserRepository users) {
        this.lots = lots;
        this.booths = booths;
        this.lanes = lanes;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageView<BoothView> listBooths(UUID lotId, String keyword, int page, int size) {
        requireLot(lotId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String trimmedKeyword = keyword == null ? null : keyword.trim();
        Specification<ParkingBooth> spec = buildSpec(lotId, trimmedKeyword);
        Page<ParkingBooth> result = booths.findAll(spec, PageRequest.of(safePage, safeSize));
        return new PageView<>(
                result.getContent().stream().map(BoothView::from).toList(),
                result.getTotalElements(),
                safePage,
                safeSize);
    }

    @Transactional
    public BoothView createBooth(UUID requesterId, UUID lotId, CreateBoothRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        String name = request.name().trim();
        if (booths.existsByLotIdAndNameIgnoreCase(lotId, name)) {
            throw new BusinessException(ErrorCode.BOOTH_NAME_EXISTS);
        }
        String code = normalizeOptional(request.code());
        if (code != null && booths.existsByLotIdAndCodeIgnoreCase(lotId, code)) {
            throw new BusinessException(ErrorCode.BOOTH_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        ParkingBooth booth = new ParkingBooth(
                lot,
                name,
                code,
                normalizeOptional(request.location()),
                enabled);
        booth.setLanes(resolveLanes(lotId, request.laneIds()));
        return BoothView.from(booths.save(booth));
    }

    @Transactional
    public BoothView updateBooth(
            UUID requesterId, UUID lotId, UUID boothId, UpdateBoothRequest request) {
        requireAdmin(requesterId);
        requireLot(lotId);
        ParkingBooth booth = booths.findById(boothId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!booth.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String name = request.name().trim();
        if (booths.existsByLotIdAndNameIgnoreCaseAndIdNot(lotId, name, boothId)) {
            throw new BusinessException(ErrorCode.BOOTH_NAME_EXISTS);
        }
        String code = normalizeOptional(request.code());
        if (code != null && booths.existsByLotIdAndCodeIgnoreCaseAndIdNot(lotId, code, boothId)) {
            throw new BusinessException(ErrorCode.BOOTH_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null ? booth.isEnabled() : request.enabled();
        booth.updateDetails(name, code, normalizeOptional(request.location()), enabled);
        if (request.laneIds() != null) {
            booth.setLanes(resolveLanes(lotId, request.laneIds()));
        }
        return BoothView.from(booths.save(booth));
    }

    @Transactional
    public void deleteBooth(UUID requesterId, UUID lotId, UUID boothId) {
        requireAdmin(requesterId);
        requireLot(lotId);
        ParkingBooth booth = booths.findById(boothId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!booth.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        booths.delete(booth);
    }

    private Specification<ParkingBooth> buildSpec(UUID lotId, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("lot").get("id"), lotId));
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern)));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<ParkingLane> resolveLanes(UUID lotId, List<UUID> laneIds) {
        if (laneIds == null || laneIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<ParkingLane> resolved = new ArrayList<>();
        for (UUID laneId : laneIds) {
            ParkingLane lane = lanes.findById(laneId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (!lane.getLot().getId().equals(lotId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            resolved.add(lane);
        }
        return resolved;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
