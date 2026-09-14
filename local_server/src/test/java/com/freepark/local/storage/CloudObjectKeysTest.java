package com.freepark.local.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CloudObjectKeysTest {

    @Test
    void joinsPrefixAndRelativePath() {
        assertEquals(
                "freepark-local/recognition/20260914/cam.jpg",
                CloudObjectKeys.objectKey("freepark-local/", "recognition/20260914/cam.jpg"));
        assertEquals(
                "freepark-local/recognition/20260914/cam.jpg",
                CloudObjectKeys.objectKey("/freepark-local", "\\recognition\\20260914\\cam.jpg"));
        assertEquals("recognition/a.jpg", CloudObjectKeys.objectKey("", "recognition/a.jpg"));
    }

    @Test
    void stripsSchemeFromEndpoint() {
        assertEquals("oss-cn-hangzhou.aliyuncs.com",
                CloudObjectKeys.hostWithoutScheme("https://oss-cn-hangzhou.aliyuncs.com/"));
        assertEquals("oss-cn-hangzhou.aliyuncs.com",
                CloudObjectKeys.hostWithoutScheme("oss-cn-hangzhou.aliyuncs.com"));
    }

    @Test
    void buildsVirtualHosts() {
        assertEquals(
                "parking-ui-test.oss-cn-hangzhou.aliyuncs.com",
                CloudObjectKeys.virtualHost(
                        CloudStorageProvider.ALIYUN_OSS, "oss-cn-hangzhou.aliyuncs.com", "cn-hangzhou", "parking-ui-test"));
        assertEquals(
                "my-bucket.obs.cn-north-4.myhuaweicloud.com",
                CloudObjectKeys.virtualHost(
                        CloudStorageProvider.HUAWEI_OBS, "obs.cn-north-4.myhuaweicloud.com", "cn-north-4", "my-bucket"));
        assertEquals(
                "name-123.cos.ap-guangzhou.myqcloud.com",
                CloudObjectKeys.virtualHost(
                        CloudStorageProvider.TENCENT_COS, "", "ap-guangzhou", "name-123"));
    }

    @Test
    void prefersCustomDomainForPublicUrl() {
        assertEquals(
                "https://test-www.yuanzunzun.com/freepark-local/recognition/a.jpg",
                CloudObjectKeys.publicUrl(
                        "https://test-www.yuanzunzun.com/",
                        "parking-ui-test.oss-cn-hangzhou.aliyuncs.com",
                        "freepark-local/recognition/a.jpg"));
        assertEquals(
                "https://parking-ui-test.oss-cn-hangzhou.aliyuncs.com/freepark-local/recognition/a.jpg",
                CloudObjectKeys.publicUrl(
                        "",
                        "parking-ui-test.oss-cn-hangzhou.aliyuncs.com",
                        "freepark-local/recognition/a.jpg"));
    }

    @Test
    void extractsRegions() {
        assertEquals("cn-hangzhou", CloudObjectKeys.regionFromOssEndpoint("oss-cn-hangzhou.aliyuncs.com"));
        assertEquals("cn-north-4", CloudObjectKeys.regionFromHuaweiEndpoint("obs.cn-north-4.myhuaweicloud.com"));
        assertEquals("cn-east-3", CloudObjectKeys.regionFromHuaweiEndpoint("https://obs.cn-east-3.myhuaweicloud.com"));
    }
}
