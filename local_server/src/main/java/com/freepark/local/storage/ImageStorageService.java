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

import org.springframework.stereotype.Service;

import com.freepark.local.sitesettings.service.SystemSettingsService;

/**
 * 图片本地存储：把 base64 图片解码落盘到「系统设置」指定的图片存储目录，
 * 数据库仅保存相对路径（recognition/yyyyMMdd/uuid.ext），通过 HTTP 提供访问。
 */
@Service
public class ImageStorageService {

    /** 图片访问 URL 前缀，前端通过该前缀加载图片。 */
    public static final String IMAGE_URL_PREFIX = "/api/v1/images/";

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String RECOGNITION_SUBDIR = "recognition";

    private final SystemSettingsService settings;

    public ImageStorageService(SystemSettingsService settings) {
        this.settings = settings;
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
        String safeMime = mime == null || mime.isBlank() ? "image/jpeg" : mime;
        try {
            String dateDir = DATE_DIR.format(LocalDate.now());
            String fileName = (deviceCode == null || deviceCode.isBlank() ? "" : sanitize(deviceCode) + "-")
                    + UUID.randomUUID() + extensionFor(safeMime);
            Path relative = Paths.get(RECOGNITION_SUBDIR, dateDir, fileName);
            Path target = resolveImagePath(relative.toString());
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return relative.toString().replace('\\', '/');
        } catch (IOException e) {
            return null;
        }
    }

    /** 把相对路径拼成前端可访问的 URL（/api/v1/images/xxx）。 */
    public String toPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return IMAGE_URL_PREFIX + relativePath.replace('\\', '/');
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
