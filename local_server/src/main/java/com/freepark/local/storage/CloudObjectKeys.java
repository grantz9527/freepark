package com.freepark.local.storage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class CloudObjectKeys {

    private CloudObjectKeys() {
    }

    public static String objectKey(String prefix, String relativePath) {
        String p = prefix == null ? "" : prefix.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (!p.isEmpty() && !p.endsWith("/")) {
            p = p + "/";
        }
        String r = relativePath == null ? "" : relativePath.replace('\\', '/');
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        return p + r;
    }

    public static String hostWithoutScheme(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        String value = endpoint.trim();
        if (value.contains("://")) {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                int port = uri.getPort();
                return port > 0 ? host + ":" + port : host;
            }
            value = value.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        return value;
    }

    public static String virtualHost(CloudStorageProvider provider, String endpointHost, String region, String bucket) {
        String b = bucket == null ? "" : bucket.trim();
        return switch (provider) {
            case TENCENT_COS -> b + ".cos." + (region == null ? "" : region.trim()) + ".myqcloud.com";
            case ALIYUN_OSS, HUAWEI_OBS -> b + "." + (endpointHost == null ? "" : endpointHost.trim());
        };
    }

    public static String publicUrl(String customDomain, String virtualHost, String objectKey) {
        String key = objectKey == null ? "" : objectKey.replace('\\', '/');
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        String domain = customDomain == null ? "" : customDomain.trim();
        if (!domain.isEmpty()) {
            if (!domain.contains("://")) {
                domain = "https://" + domain;
            }
            while (domain.endsWith("/")) {
                domain = domain.substring(0, domain.length() - 1);
            }
            return domain + "/" + key;
        }
        return "https://" + virtualHost + "/" + key;
    }

    public static String regionFromOssEndpoint(String host) {
        String first = firstLabel(host);
        if (first.startsWith("oss-") && first.length() > 4) {
            return first.substring(4);
        }
        return first.isEmpty() ? "us-east-1" : first;
    }

    public static String regionFromHuaweiEndpoint(String host) {
        String value = hostWithoutScheme(host);
        String[] labels = value.split("\\.");
        if (labels.length >= 2 && "obs".equalsIgnoreCase(labels[0])) {
            return labels[1];
        }
        return value.isEmpty() ? "cn-north-4" : labels[0];
    }

    public static String encodePath(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String[] parts = key.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(rfc3986(parts[i]));
        }
        return encoded.toString();
    }

    static String rfc3986(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String firstLabel(String host) {
        String value = hostWithoutScheme(host);
        int dot = value.indexOf('.');
        return dot < 0 ? value : value.substring(0, dot);
    }
}
