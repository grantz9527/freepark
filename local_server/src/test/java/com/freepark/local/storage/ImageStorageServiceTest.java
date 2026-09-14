package com.freepark.local.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.freepark.local.sitesettings.service.SystemSettingsService;

class ImageStorageServiceTest {

    @Test
    void skipsWhenLocalAndCloudAreOff() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        CloudImageUploadService uploads = mock(CloudImageUploadService.class);
        when(settings.isImageStorageEnabled()).thenReturn(false);
        when(settings.isCloudStorageEnabled()).thenReturn(false);

        ImageStorageService service = new ImageStorageService(settings, uploads);
        assertNull(service.saveImage(new byte[] {1, 2, 3}, "image/jpeg", "cam1"));
        verify(uploads, never()).uploadAsync(any(), any(), any());
    }

    @Test
    void writesLocalAndDoesNotUploadWhenCloudOff(@TempDir Path dir) throws Exception {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        CloudImageUploadService uploads = mock(CloudImageUploadService.class);
        when(settings.isImageStorageEnabled()).thenReturn(true);
        when(settings.isCloudStorageEnabled()).thenReturn(false);
        when(settings.getImageStoragePath()).thenReturn(dir.toString());

        ImageStorageService service = new ImageStorageService(settings, uploads);
        byte[] bytes = new byte[] {1, 2, 3, 4};
        String relative = service.saveImage(bytes, "image/jpeg", "cam1");
        assertNotNull(relative);
        assertTrue(relative.startsWith("recognition/"));
        assertTrue(Files.isRegularFile(service.resolveImagePath(relative)));
        verify(uploads, never()).uploadAsync(any(), any(), any());
        assertEquals("/api/v1/images/" + relative, service.toPublicUrl(relative));
    }

    @Test
    void uploadsWhenCloudOnEvenIfLocalOff() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        CloudImageUploadService uploads = mock(CloudImageUploadService.class);
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
        when(settings.isImageStorageEnabled()).thenReturn(false);
        when(settings.isCloudStorageEnabled()).thenReturn(true);
        when(settings.cloudUploadTarget()).thenReturn(target);

        ImageStorageService service = new ImageStorageService(settings, uploads);
        byte[] bytes = new byte[] {9, 8, 7};
        String relative = service.saveImage(bytes, "image/jpeg", "gate-1");
        assertNotNull(relative);
        verify(uploads).uploadAsync(eq(bytes), eq(relative), eq("image/jpeg"));
        assertEquals(
                "https://test-www.yuanzunzun.com/freepark-local/" + relative,
                service.toPublicUrl(relative));
    }

    @Test
    void writesLocalAndUploadsWhenBothOn(@TempDir Path dir) throws Exception {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        CloudImageUploadService uploads = mock(CloudImageUploadService.class);
        when(settings.isImageStorageEnabled()).thenReturn(true);
        when(settings.isCloudStorageEnabled()).thenReturn(true);
        when(settings.getImageStoragePath()).thenReturn(dir.toString());

        ImageStorageService service = new ImageStorageService(settings, uploads);
        byte[] bytes = new byte[] {5, 6};
        String relative = service.saveImage(bytes, "image/png", "booth");
        assertNotNull(relative);
        assertTrue(relative.endsWith(".png"));
        assertTrue(Files.isRegularFile(service.resolveImagePath(relative)));
        verify(uploads).uploadAsync(eq(bytes), eq(relative), eq("image/png"));
        assertEquals("/api/v1/images/" + relative, service.toPublicUrl(relative));
    }
}
