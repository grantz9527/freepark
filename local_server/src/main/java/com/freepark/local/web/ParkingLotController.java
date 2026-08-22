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

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.lot.CreateLotRequest;
import com.freepark.local.lot.LotView;
import com.freepark.local.lot.ParkingLotService;
import com.freepark.local.lot.UpdateLotRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lots")
public class ParkingLotController {

    private final ParkingLotService parkingLotService;
    private final MessageService messages;

    public ParkingLotController(ParkingLotService parkingLotService, MessageService messages) {
        this.parkingLotService = parkingLotService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<LotView>> list() {
        return ApiResponse.ok(messages, parkingLotService.listLots());
    }

    @PostMapping
    public ApiResponse<LotView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateLotRequest request) {
        return ApiResponse.ok(messages, parkingLotService.createLot(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping("/{lotId}")
    public ApiResponse<LotView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @Valid @RequestBody UpdateLotRequest request) {
        return ApiResponse.ok(messages, parkingLotService.updateLot(UUID.fromString(jwt.getSubject()), lotId, request));
    }
}
