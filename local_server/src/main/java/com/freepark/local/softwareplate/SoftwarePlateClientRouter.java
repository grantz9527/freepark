package com.freepark.local.softwareplate;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.hyperlpr3.HyperLpr3Client;
import com.freepark.local.sitesettings.dto.Yolo26PlateSettings;
import com.freepark.local.sitesettings.service.SystemSettingsService;
import com.freepark.local.softwareplate.dto.HyperLpr3Settings;
import com.freepark.local.yolo26plate.Yolo26PlateClient;

@Service
public class SoftwarePlateClientRouter {

    private final SystemSettingsService systemSettingsService;
    private final Yolo26PlateClient yolo26;
    private final HyperLpr3Client hyperLpr3;

    public SoftwarePlateClientRouter(SystemSettingsService systemSettingsService, Yolo26PlateClient yolo26,
            HyperLpr3Client hyperLpr3) {
        this.systemSettingsService = systemSettingsService;
        this.yolo26 = yolo26;
        this.hyperLpr3 = hyperLpr3;
    }

    @Transactional(readOnly = true)
    public SoftwarePlateProvider currentProvider() {
        return systemSettingsService.getSoftwarePlateProvider();
    }

    @Transactional(readOnly = true)
    public Yolo26PlateClient.RecognitionResult recognize(byte[] imageBytes, String originalName, String imageId,
            Double minConfidenceOverride, SoftwarePlateProvider providerOverride) throws IOException {
        SoftwarePlateProvider provider = providerOverride == null ? currentProvider() : providerOverride;
        if (!systemSettingsService.isSoftwarePlateEnabledForCurrentProvider()) {
            throw new BusinessException(ErrorCode.SOFTWARE_PLATE_DISABLED);
        }
        String actualName = (imageId != null && !imageId.isBlank()) ? imageId : originalName;
        return switch (provider) {
            case YOLO26_PLATE -> {
                Yolo26PlateSettings s = systemSettingsService.getYolo26PlateSettings();
                yield yolo26.recognize(s, imageBytes, actualName, minConfidenceOverride);
            }
            case HYPER_LPR3 -> {
                HyperLpr3Settings s = systemSettingsService.getHyperLpr3Settings();
                yield hyperLpr3.recognize(s, imageBytes, actualName, minConfidenceOverride);
            }
        };
    }
}
