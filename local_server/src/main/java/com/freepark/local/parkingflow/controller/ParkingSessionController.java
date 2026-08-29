package com.freepark.local.parkingflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.parkingflow.dto.ParkingSessionView;
import com.freepark.local.parkingflow.service.ParkingSessionService;

@RestController
@RequestMapping("/api/v1/parking-sessions")
public class ParkingSessionController {

    private final ParkingSessionService parkingSessionService;
    private final MessageService messages;

    public ParkingSessionController(
            ParkingSessionService parkingSessionService,
            MessageService messages) {
        this.parkingSessionService = parkingSessionService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<ParkingSessionView>> list(
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ParkingSessionStatus status) {
        return ApiResponse.ok(messages, parkingSessionService.listSessions(lotId, keyword, status));
    }

    @GetMapping("/has-open")
    public ApiResponse<Boolean> hasOpen(
            @RequestParam UUID lotId,
            @RequestParam String plateNumber) {
        return ApiResponse.ok(messages, parkingSessionService.hasOpenSession(lotId, plateNumber));
    }

    @PostMapping("/{sessionId}/void")
    public ApiResponse<ParkingSessionView> voidSession(@PathVariable UUID sessionId) {
        return ApiResponse.ok(messages, parkingSessionService.voidSession(sessionId));
    }
}
