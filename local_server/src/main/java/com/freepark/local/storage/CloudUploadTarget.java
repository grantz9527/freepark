package com.freepark.local.storage;

/**
 * 当前站点生效的云存储凭据快照，仅供内部上传使用，不对外返回密钥。
 */
public record CloudUploadTarget(
        CloudStorageProvider provider,
        String endpointHost,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String objectPrefix,
        String customDomain,
        int maxImageBytes) {

    public String virtualHost() {
        return CloudObjectKeys.virtualHost(provider, endpointHost, region, bucket);
    }
}
