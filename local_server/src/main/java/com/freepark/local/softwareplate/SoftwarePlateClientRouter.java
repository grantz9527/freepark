package com.freepark.local.softwareplate;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.hyperlpr3.HyperLpr3Client;
import com.freepark.local.sitesettings.service.SystemSettingsService;
import com.freepark.local.softwareplate.SoftwarePlateModels.RecognitionResult;
import com.freepark.local.softwareplate.dto.HyperLpr3Settings;

@Service
public class SoftwarePlateClientRouter {

    private final SystemSettingsService systemSettingsService;
    private final HyperLpr3Client hyperLpr3;

    public SoftwarePlateClientRouter(SystemSettingsService systemSettingsService, HyperLpr3Client hyperLpr3) {
        this.systemSettingsService = systemSettingsService;
        this.hyperLpr3 = hyperLpr3;
    }

    @Transactional(readOnly = true)
    public SoftwarePlateProvider currentProvider() {
        return SoftwarePlateProvider.HYPER_LPR3;
    }

    @Transactional(readOnly = true)
    public RecognitionResult recognize(byte[] imageBytes, String originalName, String imageId,
            Double minConfidenceOverride, SoftwarePlateProvider providerOverride) throws IOException {
        if (!systemSettingsService.isSoftwarePlateEnabledForCurrentProvider()) {
            throw new BusinessException(ErrorCode.SOFTWARE_PLATE_DISABLED);
        }
        String actualName = (imageId != null && !imageId.isBlank()) ? imageId : originalName;
        HyperLpr3Settings s = systemSettingsService.getHyperLpr3Settings();
        return hyperLpr3.recognize(s, imageBytes, actualName, minConfidenceOverride);
    }
}
