package com.freepark.local.storage;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.freepark.local.sitesettings.service.SystemSettingsService;

import jakarta.annotation.PreDestroy;

/**
 * 识别抓拍图异步上传到已配置的云存储，避免堵住相机推送。
 */
@Service
public class CloudImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(CloudImageUploadService.class);

    private final SystemSettingsService settings;
    private final CloudObjectStorageClient client;
    private final ThreadPoolExecutor executor;

    public CloudImageUploadService(SystemSettingsService settings, CloudObjectStorageClient client) {
        this.settings = settings;
        this.client = client;
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable, "cloud-image-upload");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void uploadAsync(byte[] bytes, String relativePath, String mime) {
        if (bytes == null || bytes.length == 0 || relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            executor.execute(() -> upload(bytes, relativePath, mime));
        } catch (RejectedExecutionException e) {
            log.warn("cloud image upload queue full, dropped relative={}", relativePath.replace('\\', '/'));
        }
    }

    void upload(byte[] bytes, String relativePath, String mime) {
        CloudUploadTarget target = settings.cloudUploadTarget();
        if (target == null) {
            return;
        }
        String key = CloudObjectKeys.objectKey(target.objectPrefix(), relativePath);
        ImageCompressor.Result payload = ImageCompressor.limit(bytes, mime, target.maxImageBytes());
        try {
            client.put(target, key, payload.bytes(), payload.mime());
            if (payload.bytes().length != bytes.length) {
                log.info("cloud image compressed provider={} bucket={} key={} from={} to={} limit={}",
                        target.provider(), target.bucket(), key, bytes.length, payload.bytes().length, target.maxImageBytes());
            }
            log.info("cloud image uploaded provider={} bucket={} key={}", target.provider(), target.bucket(), key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("cloud image upload interrupted provider={} bucket={} key={}",
                    target.provider(), target.bucket(), key);
        } catch (Exception e) {
            log.warn("cloud image upload failed provider={} bucket={} key={}: {}",
                    target.provider(), target.bucket(), key, e.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
