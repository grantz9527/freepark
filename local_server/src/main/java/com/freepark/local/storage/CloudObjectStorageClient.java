package com.freepark.local.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 向阿里云 OSS / 华为 OBS / 腾讯 COS 发送对象 PUT。
 * 阿里云走 OSS HMAC-SHA1；华为、腾讯走 S3 兼容 AWS SigV4。
 */
@Component
public class CloudObjectStorageClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;

    public CloudObjectStorageClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build());
    }

    CloudObjectStorageClient(HttpClient http) {
        this.http = http;
    }

    public void put(CloudUploadTarget target, String objectKey, byte[] body, String contentType)
            throws IOException, InterruptedException {
        String mime = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        Instant now = Instant.now();
        String host = target.virtualHost();
        URI uri = URI.create("https://" + host + "/" + CloudObjectKeys.encodePath(objectKey));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        if (target.provider() == CloudStorageProvider.ALIYUN_OSS) {
            String date = CloudObjectSigner.rfc1123(now);
            builder.header("Date", date);
            builder.header("Content-Type", mime);
            builder.header("Authorization", CloudObjectSigner.aliyunAuthorization(
                    "PUT", target.bucket(), objectKey, mime, date, target.accessKey(), target.secretKey()));
        } else {
            Map<String, String> headers = CloudObjectSigner.awsV4Headers(
                    "PUT", host, objectKey, mime, body, now, target.region(), target.accessKey(), target.secretKey());
            headers.forEach(builder::header);
        }
        HttpResponse<String> response = http.send(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("cloud put HTTP " + status + " " + snippet(response.body()));
        }
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= 200 ? compact : compact.substring(0, 200);
    }
}
