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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.NodeSettings;
import com.freepark.local.domain.NodeSettingsRepository;
import com.freepark.local.nodeconfig.dto.FeeQuoteView;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 边缘节点算费客户端：按“车场编码 + 车牌 + 车牌颜色”请求远程算费服务并返回费用金额。
 * 算费接口地址在节点配置（NodeSettings.feeApiUrl）中维护，本机为调用方。
 * {@code lotCode} 用于让云端定位“请求来自哪个车场”并按车场的欠费统计范围计算。
 *
 * <p>识别放行走 {@link #quoteForAccess}：后台探活用在停车辆或随机车牌打算费接口，
 * 15 秒无响应则 30 秒内跳过识别路径上的算费。节点配置页试算仍走 {@link #quoteTimed}。
 */
@Service
public class FeeQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(FeeQuoteClient.class);

    private static final Duration ADMIN_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ADMIN_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    /** 识别推送最多等 2 秒，避免堵住开闸。 */
    private static final Duration ACCESS_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ACCESS_REQUEST_TIMEOUT = Duration.ofSeconds(2);
    /** 后台探活：15 秒无响应则判定网络不稳。 */
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CIRCUIT_OPEN_TTL = Duration.ofSeconds(30);

    private final NodeSettingsRepository settingsRepository;
    private final JsonMapper jsonMapper;
    private final HttpClient adminHttpClient;
    private final HttpClient accessHttpClient;
    private final HttpClient probeHttpClient;
    private final Duration accessRequestTimeout;
    private final Duration circuitOpenTtl;
    private final AtomicLong circuitOpenUntilMillis = new AtomicLong(0);
    private final AtomicReference<String> lastRemoteUrl = new AtomicReference<>();

    public FeeQuoteClient(NodeSettingsRepository settingsRepository, JsonMapper jsonMapper) {
        this.settingsRepository = settingsRepository;
        this.jsonMapper = jsonMapper;
        this.accessRequestTimeout = ACCESS_REQUEST_TIMEOUT;
        this.circuitOpenTtl = CIRCUIT_OPEN_TTL;
        this.adminHttpClient = HttpClient.newBuilder()
                .connectTimeout(ADMIN_CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.accessHttpClient = HttpClient.newBuilder()
                .connectTimeout(ACCESS_CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.probeHttpClient = HttpClient.newBuilder()
                .connectTimeout(PROBE_TIMEOUT)
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
     * 识别/联动放行用的算费：永不抛异常、绝不长时间阻塞。
     * <ul>
     *   <li>模拟金额：本机立即返回；</li>
     *   <li>未配置远程地址：空，欠费拦截不生效；</li>
     *   <li>后台探活失败（15 秒无响应）后的 30 秒熔断期内：立即跳过；</li>
     *   <li>熔断关闭时仍最多等 2 秒，超时同样进入 30 秒跳过。</li>
     * </ul>
     */
    public Optional<BigDecimal> quoteForAccess(String lotCode, String plateNumber, String plateColor) {
        return quoteForAccess(lotCode, plateNumber, plateColor, null);
    }

    /**
     * 识别放行算费。{@code laneCode} 有值时随请求带给云端，用于记下该通道欠费拦截等待；
     * 探活/试算不传通道。
     */
    public Optional<BigDecimal> quoteForAccess(String lotCode, String plateNumber, String plateColor, String laneCode) {
        NodeSettings settings = settingsRepository.findById(NodeSettings.SINGLETON_ID).orElse(null);
        if (settings != null && settings.isFeeMockEnabled()) {
            BigDecimal mock = settings.getFeeMockAmount();
            if (mock == null) {
                log.warn("模拟算费金额未配置，欠费拦截跳过 plate={}", plateNumber);
                return Optional.empty();
            }
            return Optional.of(mock);
        }
        String apiUrl = settings == null ? null : settings.getFeeApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return Optional.empty();
        }
        noteRemoteUrl(apiUrl.trim());
        if (isCircuitOpen()) {
            log.debug("算费熔断中，欠费拦截跳过 plate={}", plateNumber);
            return Optional.empty();
        }
        try {
            BigDecimal amount = requestRemote(settings, lotCode, plateNumber, plateColor, laneCode,
                    accessHttpClient, accessRequestTimeout);
            closeCircuit();
            return Optional.of(amount);
        } catch (RuntimeException ex) {
            tripCircuit();
            log.warn("云端算费不可用（本机继续通行，{} 秒内不再询问）：plate={} reason={}",
                    circuitOpenTtl.toSeconds(), plateNumber, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 后台网络探活：用在场车或随机车牌打一次算费接口。
     * 15 秒无响应则打开 30 秒熔断，识别路径在熔断期内直接跳过算费。
     */
    public void probeHealth(String lotCode, String plateNumber, String plateColor) {
        probeHealth(lotCode, plateNumber, plateColor, PROBE_TIMEOUT);
    }

    void probeHealth(String lotCode, String plateNumber, String plateColor, Duration timeout) {
        NodeSettings settings = settingsRepository.findById(NodeSettings.SINGLETON_ID).orElse(null);
        if (settings == null || settings.isFeeMockEnabled()) {
            return;
        }
        String apiUrl = settings.getFeeApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return;
        }
        noteRemoteUrl(apiUrl.trim());
        try {
            BigDecimal amount = requestRemote(settings, lotCode, plateNumber, plateColor, null, probeHttpClient, timeout);
            closeCircuit();
            log.info("算费探活成功 lot={} plate={} amount={}", lotCode, plateNumber, amount);
        } catch (RuntimeException ex) {
            tripCircuit();
            log.warn("算费探活失败（识别路径 {} 秒内跳过算费）：lot={} plate={} reason={}",
                    circuitOpenTtl.toSeconds(), lotCode, plateNumber, ex.getMessage());
        }
    }

    /**
     * 请求算费：POST {lotCode, plateNumber, plateColor} 到节点配置的算费接口地址。
     * {@code lotCode} 为请求方车场编码（可为 null/空，此时由算费服务按全局口径统计）。
     * 响应 JSON 约定为 {"amount": 12.5}（也兼容响应体直接是金额数字）。
     *
     * @return 费用金额；响应中缺失金额时抛 FEE_API_CALL_FAILED
     */
    public BigDecimal quote(String lotCode, String plateNumber, String plateColor) {
        return quoteTimed(lotCode, plateNumber, plateColor).amount();
    }

    /**
     * 与 {@link #quote} 相同，并带上本机完成本次请求的耗时（毫秒），供节点配置页试算展示。
     */
    public FeeQuoteView quoteTimed(String lotCode, String plateNumber, String plateColor) {
        long started = System.nanoTime();
        try {
            BigDecimal amount = doQuote(lotCode, plateNumber, plateColor);
            closeCircuit();
            long elapsedMs = elapsedMs(started);
            log.info("算费结果 plate={} amount={} elapsedMs={}", plateNumber, amount, elapsedMs);
            return new FeeQuoteView(amount, elapsedMs);
        } catch (RuntimeException ex) {
            if (ex instanceof BusinessException be && be.errorCode() == ErrorCode.FEE_API_CALL_FAILED) {
                tripCircuit();
            }
            log.warn("算费失败 plate={} elapsedMs={} : {}", plateNumber, elapsedMs(started), ex.getMessage());
            throw ex;
        }
    }

    boolean isCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntilMillis.get();
    }

    private BigDecimal doQuote(String lotCode, String plateNumber, String plateColor) {
        NodeSettings settings = settingsRepository.findById(NodeSettings.SINGLETON_ID).orElse(null);
        if (settings != null && settings.isFeeMockEnabled()) {
            BigDecimal mock = settings.getFeeMockAmount();
            if (mock == null) {
                throw new BusinessException(ErrorCode.INVALID_FEE_MOCK_CONFIG);
            }
            log.info("算费请求（模拟）plate={} color={} amount={}", plateNumber, plateColor, mock);
            return mock;
        }
        if (settings == null || settings.getFeeApiUrl() == null || settings.getFeeApiUrl().isBlank()) {
            throw new BusinessException(ErrorCode.FEE_API_NOT_CONFIGURED);
        }
        noteRemoteUrl(settings.getFeeApiUrl().trim());
        return requestRemote(settings, lotCode, plateNumber, plateColor, null, adminHttpClient, ADMIN_REQUEST_TIMEOUT);
    }

    private BigDecimal requestRemote(
            NodeSettings settings,
            String lotCode,
            String plateNumber,
            String plateColor,
            String laneCode,
            HttpClient httpClient,
            Duration requestTimeout) {
        String url = settings.getFeeApiUrl().trim();

        ObjectNode body = JsonNodeFactory.instance.objectNode();
        if (lotCode != null && !lotCode.isBlank()) {
            body.put("lotCode", lotCode.trim());
        }
        body.put("plateNumber", plateNumber.trim());
        if (plateColor != null && !plateColor.isBlank()) {
            body.put("plateColor", plateColor.trim());
        }
        if (laneCode != null && !laneCode.isBlank()) {
            body.put("laneCode", laneCode.trim());
        }
        String nodeCode = settings.getNodeCode();
        if (nodeCode != null && !nodeCode.isBlank()) {
            body.put("edgeCode", nodeCode.trim());
        }
        String json = jsonMapper.writeValueAsString(body);
        log.info("算费请求 {} lot={} plate={} color={} lane={}", url, lotCode, plateNumber, plateColor, laneCode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.FEE_API_CALL_FAILED, e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FEE_API_CALL_FAILED, e.getMessage());
        }

        String responseBody = response.body() == null ? "" : response.body().trim();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String hint = responseBody.length() > 400 ? responseBody.substring(0, 400) : responseBody;
            throw new BusinessException(ErrorCode.FEE_API_CALL_FAILED,
                    "HTTP " + response.statusCode() + (hint.isBlank() ? "" : (" :: " + hint)));
        }
        try {
            return parseAmount(responseBody);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String hint = responseBody.length() > 400 ? responseBody.substring(0, 400) : responseBody;
            throw new BusinessException(ErrorCode.FEE_API_CALL_FAILED,
                    "bad amount response: " + (hint.isBlank() ? e.getMessage() : hint));
        }
    }

    private void noteRemoteUrl(String url) {
        String previous = lastRemoteUrl.getAndSet(url);
        if (previous != null && !previous.equals(url)) {
            closeCircuit();
        }
    }

    private void tripCircuit() {
        circuitOpenUntilMillis.set(System.currentTimeMillis() + circuitOpenTtl.toMillis());
    }

    private void closeCircuit() {
        circuitOpenUntilMillis.set(0);
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
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
