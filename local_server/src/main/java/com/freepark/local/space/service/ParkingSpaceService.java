package com.freepark.local.space.service;

import com.freepark.local.space.dto.AreaView;
import com.freepark.local.space.dto.CreateAreaRequest;
import com.freepark.local.space.dto.CreateLocationRequest;
import com.freepark.local.space.dto.CreateSpaceRequest;
import com.freepark.local.space.dto.LocationView;
import com.freepark.local.space.dto.SpaceView;
import com.freepark.local.space.dto.UpdateSpaceRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.freepark.local.common.api.PageView;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.common.importing.VehicleSpreadsheetImportSupport;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingArea;
import com.freepark.local.domain.ParkingAreaRepository;
import com.freepark.local.domain.ParkingLocation;
import com.freepark.local.domain.ParkingLocationRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.ParkingSpace;
import com.freepark.local.domain.ParkingSpaceRepository;
import com.freepark.local.domain.UserRole;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

@Service
public class ParkingSpaceService {

    private final ParkingLotRepository lots;
    private final ParkingLocationRepository locations;
    private final ParkingAreaRepository areas;
    private final ParkingSpaceRepository spaces;
    private final LocalUserRepository users;

    public ParkingSpaceService(
            ParkingLotRepository lots,
            ParkingLocationRepository locations,
            ParkingAreaRepository areas,
            ParkingSpaceRepository spaces,
            LocalUserRepository users) {
        this.lots = lots;
        this.locations = locations;
        this.areas = areas;
        this.spaces = spaces;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<LocationView> listLocations(UUID lotId) {
        requireLot(lotId);
        return locations.findByLotIdOrderByNameAsc(lotId).stream()
                .map(LocationView::from)
                .toList();
    }

    @Transactional
    public LocationView createLocation(UUID requesterId, UUID lotId, CreateLocationRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        String name = request.name().trim();
        if (locations.existsByLotIdAndNameIgnoreCase(lotId, name)) {
            throw new BusinessException(ErrorCode.LOCATION_NAME_EXISTS);
        }
        return LocationView.from(locations.save(new ParkingLocation(lot, name)));
    }

    @Transactional(readOnly = true)
    public List<AreaView> listAreas(UUID lotId, UUID locationId) {
        requireLot(lotId);
        if (locationId != null) {
            ParkingLocation location = locations.findById(locationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (!location.getLot().getId().equals(lotId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return areas.findByLocationIdOrderByNameAsc(locationId).stream()
                    .map(AreaView::from)
                    .toList();
        }
        return areas.findByLocationLotIdOrderByNameAsc(lotId).stream()
                .map(AreaView::from)
                .toList();
    }

    @Transactional
    public AreaView createArea(UUID requesterId, UUID lotId, CreateAreaRequest request) {
        requireAdmin(requesterId);
        requireLot(lotId);
        ParkingLocation location = locations.findById(request.locationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!location.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String name = request.name().trim();
        if (areas.existsByLocationIdAndNameIgnoreCase(request.locationId(), name)) {
            throw new BusinessException(ErrorCode.AREA_NAME_EXISTS);
        }
        return AreaView.from(areas.save(new ParkingArea(location, name)));
    }

    @Transactional(readOnly = true)
    public PageView<SpaceView> listSpaces(
            UUID lotId,
            UUID locationId,
            UUID areaId,
            String code,
            int page,
            int size) {
        requireLot(lotId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String trimmedCode = code == null ? null : code.trim();
        Specification<ParkingSpace> spec = buildSpaceSpec(lotId, locationId, areaId, trimmedCode);
        Page<ParkingSpace> result = spaces.findAll(spec, PageRequest.of(safePage, safeSize));
        return new PageView<>(
                result.getContent().stream().map(SpaceView::from).toList(),
                result.getTotalElements(),
                safePage,
                safeSize);
    }

    @Transactional
    public SpaceView createSpace(UUID requesterId, UUID lotId, CreateSpaceRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        ParkingArea area = requireAreaForLot(lotId, request.areaId());
        String code = request.code().trim();
        if (spaces.existsByLotIdAndCodeIgnoreCase(lotId, code)) {
            throw new BusinessException(ErrorCode.SPACE_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        return SpaceView.from(spaces.save(new ParkingSpace(lot, area, code, enabled)));
    }

    @Transactional
    public SpaceView updateSpace(UUID requesterId, UUID lotId, UUID spaceId, UpdateSpaceRequest request) {
        requireAdmin(requesterId);
        requireLot(lotId);
        ParkingSpace space = spaces.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!space.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ParkingArea area = requireAreaForLot(lotId, request.areaId());
        String code = request.code().trim();
        if (spaces.existsByLotIdAndCodeIgnoreCaseAndIdNot(lotId, code, spaceId)) {
            throw new BusinessException(ErrorCode.SPACE_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null ? space.isEnabled() : request.enabled();
        space.updateDetails(area, code, enabled);
        return SpaceView.from(spaces.save(space));
    }

    @Transactional
    public void deleteSpace(UUID requesterId, UUID lotId, UUID spaceId) {
        requireAdmin(requesterId);
        requireLot(lotId);
        ParkingSpace space = spaces.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!space.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        spaces.delete(space);
    }

    @Transactional(readOnly = true)
    public byte[] buildImportTemplate(UUID lotId) {
        requireLot(lotId);
        return VehicleSpreadsheetImportSupport.buildTemplate(
                "泊位", VehicleSpreadsheetImportSupport.SPACE_TEMPLATE_COLUMNS);
    }

    @Transactional
    public int importSpaces(UUID requesterId, UUID lotId, UUID areaId, MultipartFile file) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        ParkingArea area = requireAreaForLot(lotId, areaId);
        List<String[]> rows = VehicleSpreadsheetImportSupport.readRows(
                file, VehicleSpreadsheetImportSupport.SPACE_COLUMN_COUNT);
        int imported = 0;
        for (String[] cells : rows) {
            String code = VehicleSpreadsheetImportSupport.cell(cells, 0);
            if (code.isEmpty()) {
                continue;
            }
            if (spaces.existsByLotIdAndCodeIgnoreCase(lotId, code)) {
                continue;
            }
            spaces.save(new ParkingSpace(lot, area, code, true));
            imported++;
        }
        return imported;
    }

    private Specification<ParkingSpace> buildSpaceSpec(
            UUID lotId,
            UUID locationId,
            UUID areaId,
            String code) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("lot").get("id"), lotId));
            Join<ParkingSpace, ParkingArea> areaJoin = root.join("area");
            if (areaId != null) {
                predicates.add(cb.equal(areaJoin.get("id"), areaId));
            } else if (locationId != null) {
                predicates.add(cb.equal(areaJoin.get("location").get("id"), locationId));
            }
            if (code != null && !code.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + code.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("code")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ParkingLot requireLot(UUID lotId) {
        return lots.findById(lotId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private ParkingArea requireAreaForLot(UUID lotId, UUID areaId) {
        ParkingArea area = areas.findById(areaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!area.getLocation().getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return area;
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
