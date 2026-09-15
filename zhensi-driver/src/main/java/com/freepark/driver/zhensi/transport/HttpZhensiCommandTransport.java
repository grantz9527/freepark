package com.freepark.driver.zhensi.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freepark.driver.api.model.AioDriverException;
import com.freepark.driver.api.model.DeviceConfig;

/**
 * 臻识设备 HTTP 命令通道（JDK HttpClient + Jackson，驱动自带依赖）。
 *
 * <p>演示用的默认实现：向 {@code http://host:80/v1/device/command} POST
 * JSON {@code { "cmd": "...", "text": "...", "durationMs": n }}。
 * {@link DeviceConfig#port()} 为视频流 RTSP 端口，HTTP 命令默认 80，
 * 可用 params {@code httpPort} 覆盖。
 *
 * <p>【厂商接入 TODO】真实臻识一体机/相机控制接口请按厂商文档替换：
 * 命令端点路径（默认可取 DeviceConfig.params 的 "commandPath" 覆盖）、
 * 报文结构、鉴权（Digest/Token）、以及指令集（开闸/常开/屏显/语音在
 * 不同固件中可能是独立接口或不同路径）。
 */
public final class HttpZhensiCommandTransport implements ZhensiCommandTransport {

    private static final String DEFAULT_PATH = "/v1/device/command";

    private final DeviceConfig config;
    private final String endpoint;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    public HttpZhensiCommandTransport(DeviceConfig config) {
        this.config = config;
        String path = config.params() != null
                ? config.params().getOrDefault("commandPath", DEFAULT_PATH)
                : DEFAULT_PATH;
        this.endpoint = "http://%s:%d%s".formatted(config.host(), httpPort(config), path);
    }

    @Override
    public void send(ZhensiCommand command) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "cmd", command.action(),
                    "text", command.text() == null ? "" : command.text(),
                    "durationMs", command.durationMs()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new AioDriverException("臻识命令失败 HTTP " + resp.statusCode()
                        + " cmd=" + command.action());
            }
        } catch (AioDriverException e) {
            throw e;
        } catch (Exception e) {
            throw new AioDriverException("臻识命令发送异常 device=" + config.deviceKey()
                    + " cmd=" + command.action(), e);
        }
    }

    @Override
    public boolean ping() {
        // TODO 真实健康探测接口；示范实现直接尝试 TCP 可达性即可，细节按厂商文档补充
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://%s:%d".formatted(config.host(), httpPort(config))))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    /** 档案 port 为 RTSP；HTTP 命令默认 80，params.httpPort 可覆盖。 */
    private static int httpPort(DeviceConfig config) {
        String override = config.params() == null ? null : config.params().get("httpPort");
        if (override != null && !override.isBlank()) {
            try {
                int parsed = Integer.parseInt(override.trim());
                if (parsed >= 1 && parsed <= 65535) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 80;
    }
}
