package com.freepark.local.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.freepark.local.sitesettings.service.SystemSettingsService;

class CloudImageUploadServiceTest {

    private CloudImageUploadService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void uploadPutsPrefixedObjectKey() throws Exception {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        CloudObjectStorageClient client = mock(CloudObjectStorageClient.class);
        CloudUploadTarget target = new CloudUploadTarget(
                CloudStorageProvider.ALIYUN_OSS,
                "oss-cn-hangzhou.aliyuncs.com",
                "cn-hangzhou",
                "id",
                "secret",
                "parking-ui-test",
                "freepark-local/",
                "https://test-www.yuanzunzun.com",
                200 * 1024);
        when(settings.cloudUploadTarget()).thenReturn(target);

        service = new CloudImageUploadService(settings, client);
        byte[] bytes = new byte[] {1, 2, 3};
        service.upload(bytes, "recognition/20260914/cam.jpg", "image/jpeg");

        verify(client).put(target, "freepark-local/recognition/20260914/cam.jpg", bytes, "image/jpeg");
    }

    @Test
    void uploadCompressesWhenOverMaxBytes() throws Exception {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        CloudObjectStorageClient client = mock(CloudObjectStorageClient.class);
        CloudUploadTarget target = new CloudUploadTarget(
                CloudStorageProvider.ALIYUN_OSS,
                "oss-cn-hangzhou.aliyuncs.com",
                "cn-hangzhou",
                "id",
                "secret",
                "parking-ui-test",
                "freepark-local/",
                "https://test-www.yuanzunzun.com",
                20_000);
        when(settings.cloudUploadTarget()).thenReturn(target);

        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 600; y++) {
            for (int x = 0; x < 800; x++) {
                image.setRGB(x, y, (x * 51 + y * 19) << 8);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] original = out.toByteArray();

        service = new CloudImageUploadService(settings, client);
        service.upload(original, "recognition/a.png", "image/png");

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(client).put(eq(target), eq("freepark-local/recognition/a.png"), body.capture(), eq("image/jpeg"));
        assertTrue(body.getValue().length <= 20_000);
        assertTrue(body.getValue().length < original.length);
    }
}
