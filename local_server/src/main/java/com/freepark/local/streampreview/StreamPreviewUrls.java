package com.freepark.local.streampreview;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;

final class StreamPreviewUrls {

    private static final Set<String> SCHEMES = Set.of("rtsp", "rtsps", "rtmp", "rtmps", "http", "https");

    private StreamPreviewUrls() {
    }

    static String requireTranscodable(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.STREAM_URL_INVALID);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 512) {
            throw new BusinessException(ErrorCode.STREAM_URL_INVALID);
        }
        if (isGo2rtcStreamName(trimmed)) {
            return trimmed;
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.STREAM_URL_INVALID);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.STREAM_URL_INVALID);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(ErrorCode.STREAM_URL_INVALID);
        }
        return trimmed;
    }

    /** Frigate/go2rtc 已登记的流名称，例如 cam_acfb0350。 */
    static boolean isGo2rtcStreamName(String value) {
        return value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,119}");
    }

    static URI go2rtcStreamMp4(String httpBase, String streamUrl) {
        String src = URLEncoder.encode(streamUrl, StandardCharsets.UTF_8).replace("+", "%20");
        String base = httpBase.endsWith("/") ? httpBase.substring(0, httpBase.length() - 1) : httpBase;
        return URI.create(base + "/api/stream.mp4?src=" + src);
    }

    static String go2rtcBase(String host, int port) {
        return "http://" + host.trim() + ":" + port;
    }

    static String frigateGo2rtcProxyBase(String host, int apiPort) {
        return go2rtcBase(host, apiPort) + "/api/go2rtc";
    }
}
