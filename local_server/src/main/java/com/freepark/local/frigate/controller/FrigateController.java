package com.freepark.local.frigate.controller;

import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.frigate.dto.BindFrigateCameraRequest;
import com.freepark.local.frigate.dto.CreateFrigateCameraRequest;
import com.freepark.local.frigate.dto.FrigateCameraView;
import com.freepark.local.frigate.dto.FrigateSettingsView;
import com.freepark.local.frigate.dto.SimulateFrigateEventRequest;
import com.freepark.local.frigate.dto.UpdateFrigateCameraRequest;
import com.freepark.local.frigate.dto.UpdateFrigateSettingsRequest;
import com.freepark.local.frigate.service.FrigateService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/frigate")
public class FrigateController {

    private final FrigateService frigateService;
    private final MessageService messages;

    public FrigateController(FrigateService frigateService, MessageService messages) {
        this.frigateService = frigateService;
        this.messages = messages;
    }

    @GetMapping("/settings")
    public ApiResponse<FrigateSettingsView> getSettings() {
        return ApiResponse.ok(messages, frigateService.getSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<FrigateSettingsView> updateSettings(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateFrigateSettingsRequest request) {
        return ApiResponse.ok(
                messages, frigateService.updateSettings(UUID.fromString(jwt.getSubject()), request));
    }

    @PostMapping("/settings/test")
    public ApiResponse<FrigateSettingsView> testSettings(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(messages, frigateService.testSettings(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/cameras")
    public ApiResponse<List<FrigateCameraView>> listCameras() {
        return ApiResponse.ok(messages, frigateService.listCameras());
    }

    @PostMapping("/cameras")
    public ApiResponse<FrigateCameraView> createCamera(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateFrigateCameraRequest request) {
        return ApiResponse.ok(
                messages, frigateService.createCamera(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping("/cameras/{cameraId}")
    public ApiResponse<FrigateCameraView> updateCamera(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cameraId,
            @Valid @RequestBody UpdateFrigateCameraRequest request) {
        return ApiResponse.ok(
                messages, frigateService.updateCamera(UUID.fromString(jwt.getSubject()), cameraId, request));
    }

    @PostMapping("/cameras/{cameraId}/test")
    public ApiResponse<FrigateCameraView> testCamera(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID cameraId) {
        return ApiResponse.ok(
                messages, frigateService.testCamera(UUID.fromString(jwt.getSubject()), cameraId));
    }

    @PutMapping("/cameras/{cameraId}/bind")
    public ApiResponse<FrigateCameraView> bindCamera(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cameraId,
            @Valid @RequestBody BindFrigateCameraRequest request) {
        return ApiResponse.ok(
                messages, frigateService.bindCamera(UUID.fromString(jwt.getSubject()), cameraId, request));
    }

    @DeleteMapping("/cameras/{cameraId}/bind")
    public ApiResponse<FrigateCameraView> unbindCamera(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID cameraId) {
        return ApiResponse.ok(
                messages, frigateService.unbindCamera(UUID.fromString(jwt.getSubject()), cameraId));
    }

    @PostMapping("/cameras/{cameraId}/simulate")
    public ApiResponse<FrigateCameraView> simulate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cameraId,
            @Valid @RequestBody SimulateFrigateEventRequest request) {
        return ApiResponse.ok(
                messages, frigateService.simulateEvent(UUID.fromString(jwt.getSubject()), cameraId, request));
    }
}
