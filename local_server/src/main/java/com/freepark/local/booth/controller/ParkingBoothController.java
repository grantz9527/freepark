package com.freepark.local.booth.controller;

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

import com.freepark.local.booth.dto.BoothView;
import com.freepark.local.booth.dto.CreateBoothRequest;
import com.freepark.local.booth.service.ParkingBoothService;
import com.freepark.local.booth.dto.UpdateBoothRequest;
import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.api.PageView;
import com.freepark.local.common.i18n.MessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lots/{lotId}/booths")
public class ParkingBoothController {

    private final ParkingBoothService parkingBoothService;
    private final MessageService messages;

    public ParkingBoothController(ParkingBoothService parkingBoothService, MessageService messages) {
        this.parkingBoothService = parkingBoothService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<PageView<BoothView>> list(
            @PathVariable UUID lotId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(messages, parkingBoothService.listBooths(lotId, keyword, page, size));
    }

    @PostMapping
    public ApiResponse<BoothView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @Valid @RequestBody CreateBoothRequest request) {
        return ApiResponse.ok(
                messages,
                parkingBoothService.createBooth(UUID.fromString(jwt.getSubject()), lotId, request));
    }

    @PutMapping("/{boothId}")
    public ApiResponse<BoothView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID boothId,
            @Valid @RequestBody UpdateBoothRequest request) {
        return ApiResponse.ok(
                messages,
                parkingBoothService.updateBooth(
                        UUID.fromString(jwt.getSubject()), lotId, boothId, request));
    }

    @DeleteMapping("/{boothId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID boothId) {
        parkingBoothService.deleteBooth(UUID.fromString(jwt.getSubject()), lotId, boothId);
        return ApiResponse.ok(messages, null);
    }
}
