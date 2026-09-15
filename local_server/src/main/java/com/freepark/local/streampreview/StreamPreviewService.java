package com.freepark.local.streampreview;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.config.FreeparkProperties;
import com.freepark.local.domain.FrigateCamera;
import com.freepark.local.domain.FrigateCameraRepository;
import com.freepark.local.domain.FrigateSettings;
import com.freepark.local.domain.FrigateSettingsRepository;
import com.freepark.local.streampreview.dto.StreamPreviewSourceView;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class StreamPreviewService {

    private static final Logger log = LoggerFactory.getLogger(StreamPreviewService.class);
    private static final int FIRST_CHUNK_BYTES = 8 * 1024;

    private final FrigateSettingsRepository frigateSettings;
    private final FrigateCameraRepository frigateCameras;
    private final FreeparkProperties properties;
    private final JsonMapper jsonMapper;
    private final Semaphore slots;
    private final HttpClient http;

    public StreamPreviewService(
            FrigateSettingsRepository frigateSettings,
            FrigateCameraRepository frigateCameras,
            FreeparkProperties properties,
            JsonMapper jsonMapper) {
        this.frigateSettings = frigateSettings;
        this.frigateCameras = frigateCameras;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.slots = new Semaphore(properties.preview().maxSessions());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public PreviewPipe open(String rawUrl) {
        String url = StreamPreviewUrls.requireTranscodable(resolveSourceName(rawUrl, listSources()));
        if (!slots.tryAcquire()) {
            throw new BusinessException(ErrorCode.STREAM_PREVIEW_BUSY);
        }
        try {
            return openGo2rtc(url);
        } catch (RuntimeException ex) {
            slots.release();
            throw ex;
        }
    }

    public List<StreamPreviewSourceView> listSources() {
        Map<String, String> labelsByName = new LinkedHashMap<>();
        FrigateSettings settings = frigateSettings.findById(FrigateSettings.SINGLETON_ID).orElse(null);
        if (settings != null) {
            mergeGo2rtcStreams(settings, labelsByName);
            mergeFrigateConfigCameras(settings, labelsByName);
        }
        for (FrigateCamera camera : frigateCameras.findAll()) {
            putSource(labelsByName, camera.getCameraName(), camera.getName());
        }
        List<StreamPreviewSourceView> sources = new ArrayList<>();
        labelsByName.forEach((name, label) -> sources.add(new StreamPreviewSourceView(name, label)));
        sources.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return sources;
    }

    static String resolveSourceName(String raw, List<StreamPreviewSourceView> sources) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (sources != null) {
            for (StreamPreviewSourceView source : sources) {
                if (source.matches(trimmed)) {
                    return source.name();
                }
            }
        }
        return trimmed;
    }

    private void mergeGo2rtcStreams(FrigateSettings settings, Map<String, String> labelsByName) {
        FreeparkProperties.Preview preview = properties.preview();
        List<URI> candidates = new ArrayList<>();
        if (Boolean.TRUE.equals(preview.tryFrigateApiProxy())) {
            candidates.add(URI.create(
                    StreamPreviewUrls.frigateGo2rtcProxyBase(settings.getApiHost(), settings.getApiPort())
                            + "/api/streams"));
        }
        candidates.add(URI.create(
                StreamPreviewUrls.go2rtcBase(settings.getApiHost(), preview.go2rtcPort()) + "/api/streams"));
        for (URI uri : candidates) {
            JsonNode root = fetchJson(uri);
            JsonNode streams = unwrapStreams(root);
            if (streams == null) {
                continue;
            }
            forEachObjectField(streams, (name, ignored) -> putSource(labelsByName, name, name));
            if (!labelsByName.isEmpty()) {
                return;
            }
        }
    }

    private void mergeFrigateConfigCameras(FrigateSettings settings, Map<String, String> labelsByName) {
        URI uri = URI.create(StreamPreviewUrls.go2rtcBase(settings.getApiHost(), settings.getApiPort()) + "/api/config");
        JsonNode cameras = fetchJson(uri);
        if (cameras != null) {
            cameras = cameras.path("cameras");
        }
        if (cameras == null || !cameras.isObject()) {
            return;
        }
        forEachObjectField(cameras, (name, node) -> {
            String friendly = text(node.path("friendly_name"));
            putSource(labelsByName, name, friendly);
        });
    }

    private JsonNode fetchJson(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return jsonMapper.readTree(response.body());
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("Failed to fetch {} : {}", uri, ex.getMessage());
            return null;
        }
    }

    private static JsonNode unwrapStreams(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode nested = root.get("streams");
        if (nested != null && nested.isObject()) {
            return nested;
        }
        return root;
    }

    private static void putSource(Map<String, String> labelsByName, String name, String label) {
        if (name == null || name.isBlank()) {
            return;
        }
        String id = name.trim();
        String existing = labelsByName.get(id);
        String friendly = label == null ? "" : label.trim();
        if (existing == null || existing.equals(id)) {
            labelsByName.put(id, friendly.isEmpty() ? id : friendly);
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.asText("");
        return value == null ? "" : value.trim();
    }

    private static void forEachObjectField(JsonNode node, java.util.function.BiConsumer<String, JsonNode> consumer) {
        if (node == null || !node.isObject()) {
            return;
        }
        node.properties().forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
    }

    private PreviewPipe openGo2rtc(String streamUrl) {
        FrigateSettings settings = frigateSettings.findById(FrigateSettings.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException(ErrorCode.STREAM_PREVIEW_FAILED, "frigate settings"));
        FreeparkProperties.Preview preview = properties.preview();
        List<URI> candidates = new ArrayList<>();
        // 本机 Docker 已映射 Frigate 5000，/api/go2rtc 可直达内置 go2rtc；1984 作为兜底
        if (Boolean.TRUE.equals(preview.tryFrigateApiProxy())) {
            candidates.add(StreamPreviewUrls.go2rtcStreamMp4(
                    StreamPreviewUrls.frigateGo2rtcProxyBase(settings.getApiHost(), settings.getApiPort()),
                    streamUrl));
        }
        candidates.add(StreamPreviewUrls.go2rtcStreamMp4(
                StreamPreviewUrls.go2rtcBase(settings.getApiHost(), preview.go2rtcPort()), streamUrl));
        BusinessException last = null;
        for (int i = 0; i < candidates.size(); i++) {
            URI uri = candidates.get(i);
            try {
                return openUri(uri, preview.connectTimeout());
            } catch (BusinessException ex) {
                last = ex;
                if (!isRetryable(ex) || i == candidates.size() - 1) {
                    throw ex;
                }
                log.debug("go2rtc candidate failed, trying next: {}", ex.getMessage());
            }
        }
        throw last != null ? last : new BusinessException(ErrorCode.STREAM_PREVIEW_FAILED, "go2rtc");
    }

    private boolean isRetryable(BusinessException ex) {
        if (ex.errorCode() != ErrorCode.STREAM_PREVIEW_FAILED || ex.args() == null || ex.args().length == 0) {
            return false;
        }
        String detail = String.valueOf(ex.args()[0]).toLowerCase();
        return detail.contains("connection") || detail.contains("refused") || detail.contains("timed out")
                || detail.contains("timeout") || detail.contains("unreachable")
                || detail.contains("无法连接") || detail.contains("連線") || detail.contains("连接");
    }

    private PreviewPipe openUri(URI uri, Duration firstByteTimeout) {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (ConnectException | HttpTimeoutException ex) {
            throw new BusinessException(ErrorCode.STREAM_PREVIEW_FAILED, "connection refused: " + ex.getMessage());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.STREAM_PREVIEW_FAILED, "connection refused: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.STREAM_PREVIEW_FAILED, "interrupted");
        }
        InputStream body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = readLimited(body);
            try {
                body.close();
            } catch (IOException ignored) {
                // ignore
            }
            throw new BusinessException(
                    ErrorCode.STREAM_PREVIEW_FAILED,
                    "HTTP " + response.statusCode() + (detail.isBlank() ? "" : ": " + detail));
        }
        byte[] first = readFirstChunk(body, firstByteTimeout);
        return new PreviewPipe(body, first, slots);
    }

    private byte[] readFirstChunk(InputStream in, Duration timeout) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().name("go2rtc-preview-head").start(() -> {
            try {
                byte[] buf = new byte[FIRST_CHUNK_BYTES];
                int n = in.read(buf);
                if (n > 0) {
                    synchronized (buffer) {
                        buffer.write(buf, 0, n);
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        });
        try {
            reader.join(timeout.toMillis());
            if (reader.isAlive()) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // abort
                }
                throw new BusinessException(ErrorCode.STREAM_PREVIEW_FAILED, "timeout");
            }
            byte[] first;
            synchronized (buffer) {
                first = buffer.toByteArray();
            }
            if (first.length == 0) {
                throw new BusinessException(ErrorCode.STREAM_PREVIEW_FAILED, "empty");
            }
            return first;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            try {
                in.close();
            } catch (IOException ignored) {
                // abort
            }
            throw new BusinessException(ErrorCode.STREAM_PREVIEW_FAILED, "interrupted");
        }
    }

    private static String readLimited(InputStream in) {
        try {
            byte[] buf = in.readNBytes(400);
            return new String(buf, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            return "";
        }
    }

    public static final class PreviewPipe implements Closeable {
        private final InputStream upstream;
        private final byte[] firstChunk;
        private final Semaphore slots;
        private boolean closed;

        PreviewPipe(InputStream upstream, byte[] firstChunk, Semaphore slots) {
            this.upstream = upstream;
            this.firstChunk = firstChunk;
            this.slots = slots;
        }

        public void copyTo(OutputStream out) throws IOException {
            out.write(firstChunk);
            out.flush();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = upstream.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                upstream.close();
            } catch (IOException ignored) {
                // ignore
            }
            slots.release();
        }
    }
}
