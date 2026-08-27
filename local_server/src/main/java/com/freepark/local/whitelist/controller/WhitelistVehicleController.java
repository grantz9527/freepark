package com.freepark.local.whitelist.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.api.PageView;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.internalvehicle.dto.ImportInternalVehiclesResponse;
import com.freepark.local.whitelist.dto.CreateWhitelistVehicleRequest;
import com.freepark.local.whitelist.dto.UpdateWhitelistVehicleRequest;
import com.freepark.local.whitelist.service.WhitelistVehicleService;
import com.freepark.local.whitelist.dto.WhitelistVehicleView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lots/{lotId}/whitelist-vehicles")
public class WhitelistVehicleController {

    private final WhitelistVehicleService whitelistVehicleService;
    private final MessageService messages;

    public WhitelistVehicleController(WhitelistVehicleService whitelistVehicleService, MessageService messages) {
        this.whitelistVehicleService = whitelistVehicleService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<PageView<WhitelistVehicleView>> list(
            @PathVariable UUID lotId,
            @RequestParam(required = false) String plate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(messages, whitelistVehicleService.listVehicles(lotId, plate, page, size));
    }

    @PostMapping
    public ApiResponse<WhitelistVehicleView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @Valid @RequestBody CreateWhitelistVehicleRequest request) {
        return ApiResponse.ok(
                messages,
                whitelistVehicleService.createVehicle(UUID.fromString(jwt.getSubject()), lotId, request));
    }

    @PutMapping("/{vehicleId}")
    public ApiResponse<WhitelistVehicleView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID vehicleId,
            @Valid @RequestBody UpdateWhitelistVehicleRequest request) {
        return ApiResponse.ok(
                messages,
                whitelistVehicleService.updateVehicle(
                        UUID.fromString(jwt.getSubject()), lotId, vehicleId, request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportInternalVehiclesResponse> importVehicles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(
                messages,
                whitelistVehicleService.importVehicles(UUID.fromString(jwt.getSubject()), lotId, file));
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate(@PathVariable UUID lotId) {
        byte[] body = whitelistVehicleService.buildImportTemplate(lotId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"whitelist-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportVehicles(
            @PathVariable UUID lotId,
            @RequestParam(required = false) String plate) {
        byte[] body = whitelistVehicleService.exportVehicles(lotId, plate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"whitelist-vehicles.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @DeleteMapping("/{vehicleId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID vehicleId) {
        whitelistVehicleService.deleteVehicle(UUID.fromString(jwt.getSubject()), lotId, vehicleId);
        return ApiResponse.ok(messages, null);
    }
}
