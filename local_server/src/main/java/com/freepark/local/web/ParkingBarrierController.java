package com.freepark.local.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.barrier.BarrierView;
import com.freepark.local.barrier.CreateBarrierRequest;
import com.freepark.local.barrier.ParkingBarrierService;
import com.freepark.local.barrier.UpdateBarrierRequest;
import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lanes/{laneId}/barriers")
public class ParkingBarrierController {

    private final ParkingBarrierService parkingBarrierService;
    private final MessageService messages;

    public ParkingBarrierController(ParkingBarrierService parkingBarrierService, MessageService messages) {
        this.parkingBarrierService = parkingBarrierService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<BarrierView>> list(@PathVariable UUID laneId) {
        return ApiResponse.ok(messages, parkingBarrierService.listBarriers(laneId));
    }

    @PostMapping
    public ApiResponse<BarrierView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID laneId,
            @Valid @RequestBody CreateBarrierRequest request) {
        return ApiResponse.ok(
                messages,
                parkingBarrierService.createBarrier(UUID.fromString(jwt.getSubject()), laneId, request));
    }

    @PutMapping("/{barrierId}")
    public ApiResponse<BarrierView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID laneId,
            @PathVariable UUID barrierId,
            @Valid @RequestBody UpdateBarrierRequest request) {
        return ApiResponse.ok(
                messages,
                parkingBarrierService.updateBarrier(
                        UUID.fromString(jwt.getSubject()), laneId, barrierId, request));
    }
}
