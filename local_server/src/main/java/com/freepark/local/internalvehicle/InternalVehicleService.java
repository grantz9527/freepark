package com.freepark.local.internalvehicle;

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
import com.freepark.local.domain.InternalVehicle;
import com.freepark.local.domain.InternalVehicleRepository;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.UserRole;
import com.freepark.local.sitesettings.SystemSettingsService;

import jakarta.persistence.criteria.Predicate;

@Service
public class InternalVehicleService {

    private final ParkingLotRepository lots;
    private final InternalVehicleRepository vehicles;
    private final LocalUserRepository users;
    private final SystemSettingsService systemSettings;

    public InternalVehicleService(
            ParkingLotRepository lots,
            InternalVehicleRepository vehicles,
            LocalUserRepository users,
            SystemSettingsService systemSettings) {
        this.lots = lots;
        this.vehicles = vehicles;
        this.users = users;
        this.systemSettings = systemSettings;
    }

    @Transactional(readOnly = true)
    public PageView<InternalVehicleView> listVehicles(
            UUID lotId,
            String plate,
            int page,
            int size) {
        requireLot(lotId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String trimmedPlate = plate == null ? null : plate.trim();
        Specification<InternalVehicle> spec = buildSpec(lotId, trimmedPlate);
        Page<InternalVehicle> result = vehicles.findAll(spec, PageRequest.of(safePage, safeSize));
        return new PageView<>(
                result.getContent().stream().map(InternalVehicleView::from).toList(),
                result.getTotalElements(),
                safePage,
                safeSize);
    }

    @Transactional
    public InternalVehicleView createVehicle(
            UUID requesterId,
            UUID lotId,
            CreateInternalVehicleRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        String plateNumber = request.plateNumber().trim();
        if (vehicles.existsByLotIdAndPlateNumberIgnoreCase(lotId, plateNumber)) {
            throw new BusinessException(ErrorCode.INTERNAL_VEHICLE_PLATE_EXISTS);
        }
        systemSettings.ensurePlateColorAllowed(request.plateColor());
        boolean enabled = request.enabled() == null || request.enabled();
        InternalVehicle vehicle = new InternalVehicle(
                lot,
                plateNumber,
                request.plateColor(),
                request.ownerName(),
                normalizeOptional(request.phone()),
                normalizeOptional(request.department()),
                normalizeOptional(request.remark()),
                enabled);
        return InternalVehicleView.from(vehicles.save(vehicle));
    }

    @Transactional
    public InternalVehicleView updateVehicle(
            UUID requesterId,
            UUID lotId,
            UUID vehicleId,
            UpdateInternalVehicleRequest request) {
        requireAdmin(requesterId);
        requireLot(lotId);
        InternalVehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!vehicle.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String plateNumber = request.plateNumber().trim();
        if (vehicles.existsByLotIdAndPlateNumberIgnoreCaseAndIdNot(lotId, plateNumber, vehicleId)) {
            throw new BusinessException(ErrorCode.INTERNAL_VEHICLE_PLATE_EXISTS);
        }
        systemSettings.ensurePlateColorAllowed(request.plateColor());
        boolean enabled = request.enabled() == null ? vehicle.isEnabled() : request.enabled();
        vehicle.updateDetails(
                plateNumber,
                request.plateColor(),
                request.ownerName(),
                normalizeOptional(request.phone()),
                normalizeOptional(request.department()),
                normalizeOptional(request.remark()),
                enabled);
        return InternalVehicleView.from(vehicles.save(vehicle));
    }

    @Transactional
    public void deleteVehicle(UUID requesterId, UUID lotId, UUID vehicleId) {
        requireAdmin(requesterId);
        requireLot(lotId);
        InternalVehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!vehicle.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        vehicles.delete(vehicle);
    }

    private Specification<InternalVehicle> buildSpec(UUID lotId, String plate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("lot").get("id"), lotId));
            if (plate != null && !plate.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("plateNumber")), "%" + plate.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("plateNumber")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
