package com.freepark.local.internalvehicle;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.freepark.local.common.api.PageView;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.InternalVehicle;
import com.freepark.local.domain.InternalVehicleRepository;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.PlateColor;
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

    @Transactional
    public ImportInternalVehiclesResponse importVehicles(UUID requesterId, UUID lotId, MultipartFile file) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        List<String[]> rows = readRows(file);
        UUID batchId = UUID.randomUUID();
        List<PlateColor> allowedColors = systemSettings.getAllowedPlateColors();
        PlateColor defaultColor = systemSettings.getDefaultPlateColor();
        int imported = 0;
        int skipped = 0;
        for (String[] cells : rows) {
            String plate = cell(cells, 0);
            String owner = cell(cells, 1);
            if (plate.isEmpty() || plate.length() > 20 || owner.isEmpty() || owner.length() > 80) {
                skipped++;
                continue;
            }
            PlateColor color = defaultColor;
            String colorToken = cell(cells, 2);
            if (!colorToken.isEmpty()) {
                PlateColor parsed = parsePlateColor(colorToken);
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
            if (vehicles.existsByLotIdAndPlateNumberIgnoreCase(lotId, plate)) {
                skipped++;
                continue;
            }
            InternalVehicle vehicle = new InternalVehicle(
                    lot,
                    plate,
                    color,
                    owner,
                    normalizeOptional(cell(cells, 3)),
                    normalizeOptional(cell(cells, 4)),
                    normalizeOptional(cell(cells, 5)),
                    true);
            vehicle.setBatchId(batchId);
            vehicles.save(vehicle);
            imported++;
        }
        return new ImportInternalVehiclesResponse(batchId, imported, skipped);
    }

    private List<String[]> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        List<String[]> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                String[] cells = new String[6];
                for (int i = 0; i < 6; i++) {
                    Cell cell = row.getCell(i);
                    cells[i] = cell == null ? "" : formatter.formatCellValue(cell).trim();
                }
                if (cells[0].isEmpty() && cells[1].isEmpty()) {
                    continue;
                }
                if (isHeaderRow(cells[0])) {
                    continue;
                }
                rows.add(cells);
            }
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return rows;
    }

    private boolean isHeaderRow(String firstCell) {
        String header = firstCell.trim().toLowerCase();
        return header.equals("车牌号")
                || header.equals("車牌號")
                || header.equals("plate")
                || header.equals("plate number")
                || header.equals("车牌");
    }

    private String cell(String[] cells, int index) {
        return index < cells.length && cells[index] != null ? cells[index].trim() : "";
    }

    private PlateColor parsePlateColor(String token) {
        String value = token.trim().toUpperCase();
        for (PlateColor color : PlateColor.values()) {
            if (color.name().equals(value)) {
                return color;
            }
        }
        switch (value) {
            case "BLUE":
            case "蓝色":
            case "蓝":
                return PlateColor.BLUE;
            case "YELLOW":
            case "黄色":
            case "黄":
            case "黄绿":
                return PlateColor.YELLOW;
            case "GREEN":
            case "绿色":
            case "绿":
            case "新能源":
                return PlateColor.GREEN;
            case "YELLOW_GREEN":
            case "黄绿色":
                return PlateColor.YELLOW_GREEN;
            case "WHITE":
            case "白色":
            case "白":
                return PlateColor.WHITE;
            case "BLACK":
            case "黑色":
            case "黑":
                return PlateColor.BLACK;
            default:
                return null;
        }
    }

    @Transactional
    public int deleteVehiclesByBatch(UUID requesterId, UUID lotId, UUID batchId) {
        requireAdmin(requesterId);
        requireLot(lotId);
        List<InternalVehicle> batch = vehicles.findAllByLotIdAndBatchId(lotId, batchId);
        if (batch.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        vehicles.deleteAll(batch);
        return batch.size();
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
