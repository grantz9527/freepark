package com.freepark.local.blacklist.service;

import com.freepark.local.blacklist.dto.BlacklistVehicleView;
import com.freepark.local.blacklist.dto.CreateBlacklistVehicleRequest;
import com.freepark.local.blacklist.dto.UpdateBlacklistVehicleRequest;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import com.freepark.local.common.importing.VehicleSpreadsheetImportSupport;
import com.freepark.local.domain.BlacklistVehicle;
import com.freepark.local.domain.BlacklistVehicleRepository;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.UserRole;
import com.freepark.local.internalvehicle.dto.ImportInternalVehiclesResponse;
import com.freepark.local.sitesettings.service.SystemSettingsService;

import jakarta.persistence.criteria.Predicate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BlacklistVehicleService {

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ParkingLotRepository lots;
    private final BlacklistVehicleRepository vehicles;
    private final LocalUserRepository users;
    private final SystemSettingsService systemSettings;

    public BlacklistVehicleService(
            ParkingLotRepository lots,
            BlacklistVehicleRepository vehicles,
            LocalUserRepository users,
            SystemSettingsService systemSettings) {
        this.lots = lots;
        this.vehicles = vehicles;
        this.users = users;
        this.systemSettings = systemSettings;
    }

    @Transactional(readOnly = true)
    public PageView<BlacklistVehicleView> listVehicles(UUID lotId, String plate, int page, int size) {
        requireLot(lotId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String trimmedPlate = plate == null ? null : plate.trim();
        Specification<BlacklistVehicle> spec = buildSpec(lotId, trimmedPlate);
        Page<BlacklistVehicle> result = vehicles.findAll(spec, PageRequest.of(safePage, safeSize));
        return new PageView<>(
                result.getContent().stream().map(BlacklistVehicleView::from).toList(),
                result.getTotalElements(),
                safePage,
                safeSize);
    }

    @Transactional
    public BlacklistVehicleView createVehicle(UUID requesterId, UUID lotId, CreateBlacklistVehicleRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        String plateNumber = request.plateNumber().trim();
        if (vehicles.existsByLotIdAndPlateNumberIgnoreCase(lotId, plateNumber)) {
            throw new BusinessException(ErrorCode.BLACKLIST_VEHICLE_PLATE_EXISTS);
        }
        systemSettings.ensurePlateColorAllowed(request.plateColor());
        Instant startTime = requireTimeRange(request.startTime(), request.endTime());
        boolean enabled = request.enabled() == null || request.enabled();
        BlacklistVehicle vehicle = new BlacklistVehicle(
                lot,
                plateNumber,
                request.plateColor(),
                request.ownerName(),
                normalizeOptional(request.phone()),
                normalizeOptional(request.department()),
                normalizeOptional(request.remark()),
                startTime,
                request.endTime(),
                enabled);
        return BlacklistVehicleView.from(vehicles.save(vehicle));
    }

    @Transactional
    public BlacklistVehicleView updateVehicle(
            UUID requesterId,
            UUID lotId,
            UUID vehicleId,
            UpdateBlacklistVehicleRequest request) {
        requireAdmin(requesterId);
        requireLot(lotId);
        BlacklistVehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!vehicle.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String plateNumber = request.plateNumber().trim();
        if (vehicles.existsByLotIdAndPlateNumberIgnoreCaseAndIdNot(lotId, plateNumber, vehicleId)) {
            throw new BusinessException(ErrorCode.BLACKLIST_VEHICLE_PLATE_EXISTS);
        }
        systemSettings.ensurePlateColorAllowed(request.plateColor());
        Instant startTime = requireTimeRange(request.startTime(), request.endTime());
        boolean enabled = request.enabled() == null ? vehicle.isEnabled() : request.enabled();
        vehicle.updateDetails(
                plateNumber,
                request.plateColor(),
                request.ownerName(),
                normalizeOptional(request.phone()),
                normalizeOptional(request.department()),
                normalizeOptional(request.remark()),
                startTime,
                request.endTime(),
                enabled);
        return BlacklistVehicleView.from(vehicles.save(vehicle));
    }

    @Transactional
    public void deleteVehicle(UUID requesterId, UUID lotId, UUID vehicleId) {
        requireAdmin(requesterId);
        requireLot(lotId);
        BlacklistVehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!vehicle.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        vehicles.delete(vehicle);
    }

    @Transactional
    public ImportInternalVehiclesResponse importVehicles(UUID requesterId, UUID lotId, MultipartFile file) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        List<String[]> rows = VehicleSpreadsheetImportSupport.readRows(
                file, VehicleSpreadsheetImportSupport.ACCESS_LIST_COLUMN_COUNT);
        List<PlateColor> allowedColors = systemSettings.getAllowedPlateColors();
        PlateColor defaultColor = systemSettings.getDefaultPlateColor();
        var zoneId = systemSettings.getTimezone();
        int imported = 0;
        int skipped = 0;
        for (String[] cells : rows) {
            String plate = VehicleSpreadsheetImportSupport.cell(cells, 0);
            String owner = VehicleSpreadsheetImportSupport.cell(cells, 1);
            if (plate.isEmpty() || plate.length() > 20 || owner.isEmpty() || owner.length() > 80) {
                skipped++;
                continue;
            }
            PlateColor color = defaultColor;
            String colorToken = VehicleSpreadsheetImportSupport.cell(cells, 2);
            if (!colorToken.isEmpty()) {
                PlateColor parsed = VehicleSpreadsheetImportSupport.parsePlateColor(colorToken);
                if (parsed == null) {
                    skipped++;
                    continue;
                }
                color = parsed;
            }
            if (!allowedColors.contains(color)) {
                skipped++;
                continue;
            }
            Instant startTime = VehicleSpreadsheetImportSupport.parseDateTime(
                    VehicleSpreadsheetImportSupport.cell(cells, 6), zoneId);
            Instant endTime = VehicleSpreadsheetImportSupport.parseDateTime(
                    VehicleSpreadsheetImportSupport.cell(cells, 7), zoneId);
            if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
                skipped++;
                continue;
            }
            if (vehicles.existsByLotIdAndPlateNumberIgnoreCase(lotId, plate)) {
                skipped++;
                continue;
            }
            BlacklistVehicle vehicle = new BlacklistVehicle(
                    lot,
                    plate,
                    color,
                    owner,
                    normalizeOptional(VehicleSpreadsheetImportSupport.cell(cells, 3)),
                    normalizeOptional(VehicleSpreadsheetImportSupport.cell(cells, 4)),
                    normalizeOptional(VehicleSpreadsheetImportSupport.cell(cells, 5)),
                    startTime,
                    endTime,
                    true);
            vehicles.save(vehicle);
            imported++;
        }
        return new ImportInternalVehiclesResponse(null, imported, skipped);
    }

    @Transactional(readOnly = true)
    public byte[] buildImportTemplate(UUID lotId) {
        requireLot(lotId);
        return VehicleSpreadsheetImportSupport.buildTemplate(
                "黑名单", VehicleSpreadsheetImportSupport.ACCESS_LIST_TEMPLATE_COLUMNS);
    }

    @Transactional(readOnly = true)
    public byte[] exportVehicles(UUID lotId, String plate) {
        requireLot(lotId);
        String trimmedPlate = plate == null ? null : plate.trim();
        List<BlacklistVehicle> result = vehicles.findAll(buildSpec(lotId, trimmedPlate));
        ZoneId zoneId = systemSettings.getTimezone();
        List<String[]> rows = new ArrayList<>();
        for (BlacklistVehicle v : result) {
            rows.add(new String[] {
                    v.getPlateNumber(),
                    v.getOwnerName(),
                    v.getPlateColor().name(),
                    nullToEmpty(v.getPhone()),
                    nullToEmpty(v.getDepartment()),
                    nullToEmpty(v.getRemark()),
                    formatInstant(v.getStartTime(), zoneId),
                    formatInstant(v.getEndTime(), zoneId),
            });
        }
        return VehicleSpreadsheetImportSupport.buildExport(
                "黑名单", VehicleSpreadsheetImportSupport.ACCESS_LIST_TEMPLATE_COLUMNS, rows);
    }

    private Specification<BlacklistVehicle> buildSpec(UUID lotId, String plate) {
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

    private Instant requireTimeRange(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.BLACKLIST_VEHICLE_INVALID_TIME_RANGE);
        }
        return startTime;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatInstant(Instant instant, ZoneId zoneId) {
        return instant == null ? "" : EXPORT_TIME_FORMATTER.withZone(zoneId).format(instant);
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
