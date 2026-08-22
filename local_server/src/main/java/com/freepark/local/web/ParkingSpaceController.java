package com.freepark.local.web;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.api.PageView;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.space.AreaView;
import com.freepark.local.space.CreateAreaRequest;
import com.freepark.local.space.CreateLocationRequest;
import com.freepark.local.space.CreateSpaceRequest;
import com.freepark.local.space.LocationView;
import com.freepark.local.space.ParkingSpaceService;
import com.freepark.local.space.SpaceView;
import com.freepark.local.space.UpdateSpaceRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lots/{lotId}")
public class ParkingSpaceController {

    private final ParkingSpaceService parkingSpaceService;
    private final MessageService messages;

    public ParkingSpaceController(ParkingSpaceService parkingSpaceService, MessageService messages) {
        this.parkingSpaceService = parkingSpaceService;
        this.messages = messages;
    }

    @GetMapping("/locations")
    public ApiResponse<List<LocationView>> listLocations(@PathVariable UUID lotId) {
        return ApiResponse.ok(messages, parkingSpaceService.listLocations(lotId));
    }

    @PostMapping("/locations")
    public ApiResponse<LocationView> createLocation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @Valid @RequestBody CreateLocationRequest request) {
        return ApiResponse.ok(
                messages,
                parkingSpaceService.createLocation(UUID.fromString(jwt.getSubject()), lotId, request));
    }

    @GetMapping("/areas")
    public ApiResponse<List<AreaView>> listAreas(
            @PathVariable UUID lotId,
            @RequestParam(required = false) UUID locationId) {
        return ApiResponse.ok(messages, parkingSpaceService.listAreas(lotId, locationId));
    }

    @PostMapping("/areas")
    public ApiResponse<AreaView> createArea(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @Valid @RequestBody CreateAreaRequest request) {
        return ApiResponse.ok(
                messages,
                parkingSpaceService.createArea(UUID.fromString(jwt.getSubject()), lotId, request));
    }

    @GetMapping("/spaces")
    public ApiResponse<PageView<SpaceView>> listSpaces(
            @PathVariable UUID lotId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID areaId,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(
                messages,
                parkingSpaceService.listSpaces(lotId, locationId, areaId, code, page, size));
    }

    @PostMapping("/spaces")
    public ApiResponse<SpaceView> createSpace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @Valid @RequestBody CreateSpaceRequest request) {
        return ApiResponse.ok(
                messages,
                parkingSpaceService.createSpace(UUID.fromString(jwt.getSubject()), lotId, request));
    }

    @PutMapping("/spaces/{spaceId}")
    public ApiResponse<SpaceView> updateSpace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID spaceId,
            @Valid @RequestBody UpdateSpaceRequest request) {
        return ApiResponse.ok(
                messages,
                parkingSpaceService.updateSpace(UUID.fromString(jwt.getSubject()), lotId, spaceId, request));
    }

    @DeleteMapping("/spaces/{spaceId}")
    public ApiResponse<Void> deleteSpace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @PathVariable UUID spaceId) {
        parkingSpaceService.deleteSpace(UUID.fromString(jwt.getSubject()), lotId, spaceId);
        return ApiResponse.ok(messages, null);
    }

    @PostMapping("/spaces/import")
    public ApiResponse<Integer> importSpaces(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID lotId,
            @RequestParam UUID areaId,
            @RequestParam("file") MultipartFile file) {
        int imported = parkingSpaceService.importSpaces(
                UUID.fromString(jwt.getSubject()), lotId, areaId, file);
        return ApiResponse.ok(messages, imported);
    }
}
