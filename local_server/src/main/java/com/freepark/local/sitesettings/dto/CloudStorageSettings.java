package com.freepark.local.sitesettings.dto;

import com.freepark.local.storage.CloudStorageProvider;

public record CloudStorageSettings(
        boolean enabled,
        CloudStorageProvider provider,
        int maxImageKb,
        AliyunOssSettings aliyun,
        HuaweiObsSettings huawei,
        TencentCosSettings tencent) {

    public record AliyunOssSettings(
            String endpoint,
            String accessKeyId,
            boolean accessKeySecretSet,
            String bucket,
            String pathPrefix,
            String customDomain) {
    }

    public record HuaweiObsSettings(
            String endpoint,
            String accessKey,
            boolean secretKeySet,
            String bucket,
            String pathPrefix,
            String customDomain) {
    }

    public record TencentCosSettings(
            String region,
            String secretId,
            boolean secretKeySet,
            String bucket,
            String pathPrefix,
            String customDomain) {
    }
}
