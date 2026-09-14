package com.freepark.local.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.freepark.local.sitesettings.service.SystemSettingsService;

/**
 * 识别抓拍图存储：按系统设置写入本地目录，并在开启云存储时异步上传同一对象。
 * 本地与云存储可独立开关；两者都关时不保存，返回 null。
 */
@Service
public class ImageStorageService {

    /** 图片访问 URL 前缀，前端通过该前缀加载本机落盘图片。 */
    public static final String IMAGE_URL_PREFIX = "/api/v1/images/";

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String RECOGNITION_SUBDIR = "recognition";

    private final SystemSettingsService settings;
    private final CloudImageUploadService cloudUploads;

    public ImageStorageService(SystemSettingsService settings, CloudImageUploadService cloudUploads) {
        this.settings = settings;
        this.cloudUploads = cloudUploads;
    }

    /**
     * 保存 base64 图片并返回相对存储路径（如 recognition/20260829/xxxx.jpg）。
     * base64Data 可为纯 base64 或 data URL（data:image/png;base64,...）。
     * 解码或写入失败时返回 null，避免阻断识别链路入库。
     */
    public String saveBase64Image(String base64Data, String deviceCode) {
        if (base64Data == null || base64Data.isBlank()) {
            return null;
        }
        String mime = "image/jpeg";
        String payload = base64Data.trim();
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:") && comma > 0) {
            String header = payload.substring(5, comma);
            payload = payload.substring(comma + 1);
            int semi = header.indexOf(';');
            if (semi > 0) {
                mime = header.substring(0, semi);
            }
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (bytes.length == 0) {
            return null;
        }
        return saveImage(bytes, mime, deviceCode);
    }

    /** 保存原始图片字节（Frigate 快照等二进制来源），返回相对存储路径或 null。 */
    public String saveImage(byte[] bytes, String mime, String deviceCode) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        boolean localOn = settings.isImageStorageEnabled();
        boolean cloudOn = settings.isCloudStorageEnabled();
        if (!localOn && !cloudOn) {
            return null;
        }
        String safeMime = mime == null || mime.isBlank() ? "image/jpeg" : mime;
        String dateDir = DATE_DIR.format(LocalDate.now());
        String fileName = (deviceCode == null || deviceCode.isBlank() ? "" : sanitize(deviceCode) + "-")
                + UUID.randomUUID() + extensionFor(safeMime);
        String relative = Paths.get(RECOGNITION_SUBDIR, dateDir, fileName).toString().replace('\\', '/');
        if (localOn) {
            try {
                Path target = resolveImagePath(relative);
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            } catch (IOException e) {
                log.warn("local image write failed relative={}: {}", relative, e.toString());
                if (!cloudOn) {
                    return null;
                }
            }
        }
        if (cloudOn) {
            cloudUploads.uploadAsync(bytes, relative, safeMime);
        }
        return relative;
    }

    /**
     * 把相对路径拼成前端可访问的 URL。
     * 本地存储开启时走本机 /api/v1/images/；仅云存储时走自定义域名或默认对象地址。
     */
    public String toPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String relative = relativePath.replace('\\', '/');
        if (settings.isImageStorageEnabled()) {
            return IMAGE_URL_PREFIX + relative;
        }
        CloudUploadTarget target = settings.cloudUploadTarget();
        if (target != null) {
            String key = CloudObjectKeys.objectKey(target.objectPrefix(), relative);
            return CloudObjectKeys.publicUrl(target.customDomain(), target.virtualHost(), key);
        }
        return IMAGE_URL_PREFIX + relative;
    }

    /** 把相对路径解析为图片存储目录下的文件路径（用于 HTTP 提供图片）。 */
    public Path resolveImagePath(String relativePath) {
        Path root = Paths.get(settings.getImageStoragePath()).toAbsolutePath().normalize();
        return root.resolve(relativePath).normalize();
    }

    private static String extensionFor(String mime) {
        if (mime == null) {
            return ".jpg";
        }
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            default -> ".jpg";
        };
    }

    private static String sanitize(String code) {
        return code.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
