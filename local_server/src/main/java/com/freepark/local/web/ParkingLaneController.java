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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.lane.CreateLaneRequest;
import com.freepark.local.lane.LaneView;
import com.freepark.local.lane.ParkingLaneService;
import com.freepark.local.lane.UpdateLaneRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lanes")
public class ParkingLaneController {

    private final ParkingLaneService parkingLaneService;
    private final MessageService messages;

    public ParkingLaneController(ParkingLaneService parkingLaneService, MessageService messages) {
        this.parkingLaneService = parkingLaneService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<LaneView>> list(@RequestParam(required = false) UUID lotId) {
        return ApiResponse.ok(messages, parkingLaneService.listLanes(lotId));
    }

    @PostMapping
    public ApiResponse<LaneView> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateLaneRequest request) {
        return ApiResponse.ok(
                messages, parkingLaneService.createLane(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping("/{laneId}")
    public ApiResponse<LaneView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID laneId,
            @Valid @RequestBody UpdateLaneRequest request) {
        return ApiResponse.ok(
                messages,
                parkingLaneService.updateLane(UUID.fromString(jwt.getSubject()), laneId, request));
    }
}
