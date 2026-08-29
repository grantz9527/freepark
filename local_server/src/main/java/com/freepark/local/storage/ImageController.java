package com.freepark.local.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图片访问接口：按相对路径从「系统设置」指定的图片存储目录返回文件。
 * 识别抓拍图等落盘图片通过 /api/v1/images/{相对路径} 访问，无需鉴权。
 */
@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageStorageService imageStorage;

    public ImageController(ImageStorageService imageStorage) {
        this.imageStorage = imageStorage;
    }

    @GetMapping("/{*path}")
    public ResponseEntity<Resource> serve(@PathVariable String path) throws IOException {
        String relative = path == null ? "" : path.replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.isEmpty() || relative.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        Path file = imageStorage.resolveImagePath(relative).normalize();
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(mediaTypeFor(relative))
                .body(new FileSystemResource(file));
    }

    private static MediaType mediaTypeFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".bmp")) {
            return MediaType.parseMediaType("image/bmp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
