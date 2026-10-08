package com.freepark.local.device.controller;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.device.service.DeviceGatewayService;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 臻识白名单回执入口。
 *
 * <p>协议示例 {@code reply_url} 常为 {@code /whitelist_replay}（站点根路径），
 * 也可能配置成网关下的绝对地址。相机可能 POST JSON / 纯文本 / 表单，甚至 GET。
 */
@RestController
public class ZhenshiWhitelistReplayController {

    private static final Logger log = LoggerFactory.getLogger(ZhenshiWhitelistReplayController.class);

    private final DeviceGatewayService gatewayService;
    private final JsonMapper jsonMapper;

    public ZhenshiWhitelistReplayController(DeviceGatewayService gatewayService, JsonMapper jsonMapper) {
        this.gatewayService = gatewayService;
        this.jsonMapper = jsonMapper;
    }

    /** 协议文档示例路径（相对站点根）。 */
    @RequestMapping(
            value = "/whitelist_replay",
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.GET})
    public JsonNode replayAtRoot(HttpServletRequest request) {
        return handle("zhenshi", request);
    }

    /** 与设备网关其它接口同前缀的路径。 */
    @RequestMapping(
            value = {
                "/api/v1/device-gateway/{brand}/whitelist_replay",
                "/api/v1/device-gateway/{brand}/whitelist_reply"
            },
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.GET})
    public JsonNode replayUnderGateway(@PathVariable String brand, HttpServletRequest request) {
        return handle(brand == null || brand.isBlank() ? "zhenshi" : brand, request);
    }

    private JsonNode handle(String brand, HttpServletRequest request) {
        String raw = readBody(request);
        log.info(
                "[whitelist_replay] brand={} method={} uri={} contentType={} query={} body={}",
                brand,
                request.getMethod(),
                request.getRequestURI(),
                request.getContentType(),
                request.getQueryString(),
                raw == null || raw.isBlank() ? "(empty)" : raw);
        JsonNode body = parseBody(raw);
        if ((body == null || body.isNull() || body.isMissingNode() || (body.isObject() && body.isEmpty()))
                && request.getQueryString() != null) {
            body = queryAsJson(request);
        }
        gatewayService.ackWhitelistReplay(body);
        ObjectNode ok = JsonNodeFactory.instance.objectNode();
        ok.put("status", "ok");
        return ok;
    }

    private String readBody(HttpServletRequest request) {
        try {
            Charset charset = StandardCharsets.UTF_8;
            String ct = request.getContentType();
            if (ct != null && ct.toLowerCase().contains("charset=")) {
                try {
                    charset = MediaType.parseMediaType(ct).getCharset();
                    if (charset == null) {
                        charset = StandardCharsets.UTF_8;
                    }
                } catch (Exception ignored) {
                    charset = StandardCharsets.UTF_8;
                }
            }
            return StreamUtils.copyToString(request.getInputStream(), charset);
        } catch (IOException ex) {
            log.warn("[whitelist_replay] 读取 body 失败: {}", ex.getMessage());
            return "";
        }
    }

    private JsonNode parseBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        String text = raw.trim();
        try {
            return jsonMapper.readTree(text);
        } catch (Exception first) {
            // 表单：payload=... 或 data=...
            if (text.contains("=") && !text.startsWith("{")) {
                for (String part : text.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = java.net.URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                    String val = java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                    if ("payload".equalsIgnoreCase(key)
                            || "data".equalsIgnoreCase(key)
                            || "json".equalsIgnoreCase(key)
                            || "body".equalsIgnoreCase(key)) {
                        try {
                            return jsonMapper.readTree(val);
                        } catch (Exception ignored) {
                            // continue
                        }
                    }
                }
            }
            log.warn("[whitelist_replay] body 不是 JSON: {}", first.getMessage());
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private JsonNode queryAsJson(HttpServletRequest request) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        Enumeration<String> names = request.getParameterNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            node.put(name, request.getParameter(name));
        }
        return node;
    }
}
