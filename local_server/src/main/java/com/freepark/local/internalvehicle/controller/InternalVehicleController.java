package com.freepark.local.internalvehicle.controller;

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
import com.freepark.local.internalvehicle.dto.CreateInternalVehicleRequest;
import com.freepark.local.internalvehicle.dto.ImportInternalVehiclesResponse;
import com.freepark.local.internalvehicle.service.InternalVehicleService;
import com.freepark.local.internalvehicle.dto.InternalVehicleView;
import com.freepark.local.internalvehicle.dto.UpdateInternalVehicleRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lots/{lotId}/internal-vehicles")
public class InternalVehicleController {

    private final InternalVehicleService internalVehicleService;
    private final MessageService messages;

    public InternalVehicleController(InternalVehicleService internalVehicleService, MessageService messages) {
        this.internalVehicleService = internalVehicleService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<PageView<InternalVehicleView>> list(
            @PathVariable UUID lotId,
            @RequestParam(required = false) String plate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(messages, internalVehicleService.listVehicles(lotId, plate, page, size));
    }

    @PostMapping
    public ApiResponse<InternalVehicleView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @Valid @RequestBody CreateInternalVehicleRequest request) {
        return ApiResponse.ok(
                messages,
                internalVehicleService.createVehicle(UUID.fromString(jwt.getSubject()), lotId, request));
    }

    @PutMapping("/{vehicleId}")
    public ApiResponse<InternalVehicleView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID vehicleId,
            @Valid @RequestBody UpdateInternalVehicleRequest request) {
        return ApiResponse.ok(
                messages,
                internalVehicleService.updateVehicle(
                        UUID.fromString(jwt.getSubject()), lotId, vehicleId, request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportInternalVehiclesResponse> importVehicles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(
                messages,
                internalVehicleService.importVehicles(
                        UUID.fromString(jwt.getSubject()), lotId, file));
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate(@PathVariable UUID lotId) {
        byte[] body = internalVehicleService.buildImportTemplate(lotId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"internal-vehicles-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportVehicles(
            @PathVariable UUID lotId,
            @RequestParam(required = false) String plate) {
        byte[] body = internalVehicleService.exportVehicles(lotId, plate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"internal-vehicles.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @DeleteMapping("/batch/{batchId}")
    public ApiResponse<Integer> deleteBatch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID batchId) {
        int deleted = internalVehicleService.deleteVehiclesByBatch(
                UUID.fromString(jwt.getSubject()), lotId, batchId);
        return ApiResponse.ok(messages, deleted);
    }

    @DeleteMapping("/{vehicleId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID vehicleId) {
        internalVehicleService.deleteVehicle(UUID.fromString(jwt.getSubject()), lotId, vehicleId);
        return ApiResponse.ok(messages, null);
    }
}
