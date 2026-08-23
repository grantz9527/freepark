package com.freepark.local.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.api.PageView;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.internalvehicle.CreateInternalVehicleRequest;
import com.freepark.local.internalvehicle.InternalVehicleService;
import com.freepark.local.internalvehicle.InternalVehicleView;
import com.freepark.local.internalvehicle.UpdateInternalVehicleRequest;

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

    @DeleteMapping("/{vehicleId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID vehicleId) {
        internalVehicleService.deleteVehicle(UUID.fromString(jwt.getSubject()), lotId, vehicleId);
        return ApiResponse.ok(messages, null);
    }
}
