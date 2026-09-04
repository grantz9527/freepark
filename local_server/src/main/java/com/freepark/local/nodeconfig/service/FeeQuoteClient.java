package com.freepark.local.nodeconfig.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.NodeSettings;
import com.freepark.local.domain.NodeSettingsRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 边缘节点算费客户端：按“车牌 + 车牌颜色”请求远程算费服务并返回费用金额。
 * 算费接口地址在节点配置（NodeSettings.feeApiUrl）中维护，本机为调用方。
 */
@Service
public class FeeQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(FeeQuoteClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final NodeSettingsRepository settingsRepository;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public FeeQuoteClient(NodeSettingsRepository settingsRepository, JsonMapper jsonMapper) {
        this.settingsRepository = settingsRepository;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 本节点是否具备算费数据源：开启模拟金额，或配置了远程算费接口地址。
     * 本地服务自身不提供计价，欠费金额只能来自这两个来源之一；
     * 两者都无时欠费拦截不生效。
     */
    public boolean hasFeeQuoteSource() {
        NodeSettings settings = settingsRepository.findById(NodeSettings.SINGLETON_ID).orElse(null);
        if (settings == null) {
            return false;
        }
        if (settings.isFeeMockEnabled()) {
            return true;
        }
        String apiUrl = settings.getFeeApiUrl();
        return apiUrl != null && !apiUrl.isBlank();
    }

    /**
     * 请求算费：POST {plateNumber, plateColor} 到节点配置的算费接口地址。
     * 响应 JSON 约定为 {"amount": 12.5}（也兼容响应体直接是金额数字）。
     *
     * @return 费用金额；响应中缺失金额时抛 FEE_API_CALL_FAILED
     */
    public BigDecimal quote(String plateNumber, String plateColor) {
        NodeSettings settings = settingsRepository.findById(NodeSettings.SINGLETON_ID).orElse(null);
        if (settings != null && settings.isFeeMockEnabled()) {
            // 本地调试：启用模拟金额时直接返回固定金额，不调用远程算费接口
            BigDecimal mock = settings.getFeeMockAmount();
            if (mock == null) {
                throw new BusinessException(ErrorCode.INVALID_FEE_MOCK_CONFIG);
            }
            log.info("算费请求（模拟）plate={} color={} amount={}", plateNumber, plateColor, mock);
            return mock;
        }
        String apiUrl = settings == null ? null : settings.getFeeApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new BusinessException(ErrorCode.FEE_API_NOT_CONFIGURED);
        }
        String url = apiUrl.trim();

        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("plateNumber", plateNumber.trim());
        if (plateColor != null && !plateColor.isBlank()) {
            body.put("plateColor", plateColor.trim());
        }
        String json = jsonMapper.writeValueAsString(body);
        log.info("算费请求 {} plate={} color={}", url, plateNumber, plateColor);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("算费接口调用失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FEE_API_CALL_FAILED, e.getMessage());
        }

        String responseBody = response.body() == null ? "" : response.body().trim();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String hint = responseBody.length() > 400 ? responseBody.substring(0, 400) : responseBody;
            log.warn("算费接口 HTTP {}: {}", response.statusCode(), hint);
            throw new BusinessException(ErrorCode.FEE_API_CALL_FAILED,
                    "HTTP " + response.statusCode() + (hint.isBlank() ? "" : (" :: " + hint)));
        }
        try {
            BigDecimal amount = parseAmount(responseBody);
            log.info("算费结果 plate={} amount={}", plateNumber, amount);
            return amount;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String hint = responseBody.length() > 400 ? responseBody.substring(0, 400) : responseBody;
            throw new BusinessException(ErrorCode.FEE_API_CALL_FAILED,
                    "bad amount response: " + (hint.isBlank() ? e.getMessage() : hint));
        }
    }

    /**
     * 解析金额：优先取顶层 amount 字段；若无则兼容响应体直接为金额数字或纯文本数字。
     */
    private BigDecimal parseAmount(String responseBody) {
        JsonNode root = jsonMapper.readTree(responseBody);
        JsonNode amountNode = root.path("amount");
        if (amountNode.isMissingNode() || amountNode.isNull()) {
            if (root.isNumber() || (root.isTextual() && !root.asText().isBlank())) {
                amountNode = root;
            }
        }
        if (amountNode.isMissingNode() || amountNode.isNull() || amountNode.asText().isBlank()) {
            throw new BusinessException(ErrorCode.FEE_API_CALL_FAILED, "response missing amount: " + responseBody);
        }
        return new BigDecimal(amountNode.asText());
    }
}
