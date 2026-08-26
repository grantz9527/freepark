package com.freepark.local.common.importing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.InternalVehicleType;
import com.freepark.local.domain.PlateColor;

public final class VehicleSpreadsheetImportSupport {

    public static final int INTERNAL_COLUMN_COUNT = 7;

    public static final int ACCESS_LIST_COLUMN_COUNT = 8;

    public static final int WHITELIST_COLUMN_COUNT = 9;

    public static final int SPACE_COLUMN_COUNT = 1;

    public static final String[] SPACE_TEMPLATE_COLUMNS = {
            "泊位编号"
    };

    public static final String[] INTERNAL_TEMPLATE_COLUMNS = {
            "车牌号", "车主姓名", "车牌颜色", "电话", "部门", "备注", "类型"
    };

    public static final String[] ACCESS_LIST_TEMPLATE_COLUMNS = {
            "车牌号", "车主姓名", "车牌颜色", "电话", "部门", "备注", "开始时间", "结束时间"
    };

    public static final String[] WHITELIST_TEMPLATE_COLUMNS = {
            "车牌号", "车主姓名", "车牌颜色", "电话", "部门", "备注", "开始时间", "结束时间", "类型"
    };

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
    };

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
    };

    private VehicleSpreadsheetImportSupport() {
    }

    public static List<String[]> readRows(MultipartFile file, int columnCount) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        List<String[]> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                String[] cells = new String[columnCount];
                for (int i = 0; i < columnCount; i++) {
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

    public static byte[] buildTemplate(String sheetName, String[] columns) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
                sheet.setColumnWidth(i, 4200);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public static byte[] buildExport(String sheetName, String[] columns, List<String[]> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
                sheet.setColumnWidth(i, 4200);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] cells = rows.get(r);
                for (int i = 0; i < cells.length; i++) {
                    row.createCell(i).setCellValue(cells[i] == null ? "" : cells[i]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public static boolean isHeaderRow(String firstCell) {
        String header = firstCell.trim().toLowerCase();
        return header.equals("车牌号")
                || header.equals("車牌號")
                || header.equals("plate")
                || header.equals("plate number")
                || header.equals("车牌")
                || header.equals("泊位编号")
                || header.equals("泊位編號")
                || header.equals("车位编号")
                || header.equals("車位編號")
                || header.equals("code")
                || header.equals("space code");
    }

    public static String cell(String[] cells, int index) {
        return index < cells.length && cells[index] != null ? cells[index].trim() : "";
    }

    public static PlateColor parsePlateColor(String token) {
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

    public static InternalVehicleType parseInternalVehicleType(String token) {
        if (token == null || token.isBlank()) {
            return InternalVehicleType.OTHER;
        }
        String value = token.trim().toUpperCase();
        for (InternalVehicleType type : InternalVehicleType.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        switch (value) {
            case "TENANT":
            case "租户":
            case "租戶":
                return InternalVehicleType.TENANT;
            case "OWNER":
            case "业主":
            case "業主":
                return InternalVehicleType.OWNER;
            case "APPOINTMENT":
            case "预约":
            case "預約":
                return InternalVehicleType.APPOINTMENT;
            case "VISITOR":
            case "访客":
            case "訪客":
                return InternalVehicleType.VISITOR;
            case "OTHER":
            case "其它":
            case "其他":
                return InternalVehicleType.OTHER;
            default:
                return null;
        }
    }

    public static Instant parseDateTime(String value, ZoneId zoneId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(trimmed, formatter);
                return dateTime.atZone(zoneId).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(trimmed, formatter);
                return date.atStartOfDay(zoneId).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }
}
