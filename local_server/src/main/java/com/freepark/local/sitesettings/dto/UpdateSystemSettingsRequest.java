package com.freepark.local.sitesettings.dto;

import java.util.List;

import com.freepark.local.domain.PlateColor;
import com.freepark.local.softwareplate.SoftwarePlateProvider;
import com.freepark.local.storage.CloudStorageProvider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSystemSettingsRequest(
        @NotBlank String defaultLocale,
        @NotBlank String timezone,
        @NotNull PlateColor defaultPlateColor,
        @NotEmpty List<PlateColor> allowedPlateColors,
        @NotBlank @Size(max = 512) String imageStoragePath,
        Boolean imageStorageEnabled,
        @NotNull SoftwarePlateProvider softwarePlateProvider,
        @NotNull @Valid Yolo26Update yolo26Plate,
        @NotNull @Valid HyperLpr3Update hyperLpr3,
        @Valid CloudStorageUpdate cloudStorage) {

    public record Yolo26Update(
            boolean enabled,
            @Size(max = 512) String baseUrl,
            @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double minConfidence,
            @Min(500) @Max(600_000) Integer connectTimeoutMs,
            @Min(1000) @Max(600_000) Integer readTimeoutMs) {
    }

    public record HyperLpr3Update(
            boolean enabled,
            @Size(max = 512) String baseUrl,
            @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double minConfidence,
            @Min(500) @Max(600_000) Integer connectTimeoutMs,
            @Min(1000) @Max(600_000) Integer readTimeoutMs) {
    }

    public record CloudStorageUpdate(
            boolean enabled,
            CloudStorageProvider provider,
            @Valid AliyunOssUpdate aliyun,
            @Valid HuaweiObsUpdate huawei,
            @Valid TencentCosUpdate tencent) {
    }

    public record AliyunOssUpdate(
            @Size(max = 256) String endpoint,
            @Size(max = 128) String accessKeyId,
            @Size(max = 256) String accessKeySecret,
            @Size(max = 128) String bucket,
            @Size(max = 256) String pathPrefix,
            @Size(max = 256) String customDomain) {
    }

    public record HuaweiObsUpdate(
            @Size(max = 256) String endpoint,
            @Size(max = 128) String accessKey,
            @Size(max = 256) String secretKey,
            @Size(max = 128) String bucket,
            @Size(max = 256) String pathPrefix,
            @Size(max = 256) String customDomain) {
    }

    public record TencentCosUpdate(
            @Size(max = 64) String region,
            @Size(max = 128) String secretId,
            @Size(max = 256) String secretKey,
            @Size(max = 128) String bucket,
            @Size(max = 256) String pathPrefix,
            @Size(max = 256) String customDomain) {
    }
}
