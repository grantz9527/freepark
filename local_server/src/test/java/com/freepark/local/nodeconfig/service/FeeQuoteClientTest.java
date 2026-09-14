package com.freepark.local.nodeconfig.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.freepark.local.domain.NodeMode;
import com.freepark.local.domain.NodeSettings;
import com.freepark.local.domain.NodeSettingsRepository;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

class FeeQuoteClientTest {

    private NodeSettingsRepository settingsRepository;
    private FeeQuoteClient client;
    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void setUp() {
        settingsRepository = mock(NodeSettingsRepository.class);
        client = new FeeQuoteClient(settingsRepository, new JsonMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void accessQuoteUsesMockWithoutCallingRemote() {
        NodeSettings settings = new NodeSettings(NodeMode.OFFLINE);
        settings.setFeeMockEnabled(true);
        settings.setFeeMockAmount(new BigDecimal("8.00"));
        settings.setFeeApiUrl("http://127.0.0.1:1/never");
        when(settingsRepository.findById(NodeSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertEquals(new BigDecimal("8.00"), client.quoteForAccess("LOT", "粤A12345", "BLUE").orElseThrow());
        assertFalse(client.isCircuitOpen());
    }

    @Test
    void accessQuoteSkipsWhenRemoteHangsAndTripsCircuit() throws Exception {
        startServer(exchange -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            writeJson(exchange, "{\"amount\":9}");
        });
        stubRemoteUrl();

        long started = System.nanoTime();
        Optional<BigDecimal> first = client.quoteForAccess("LOT", "粤A12345", "BLUE");
        long firstMs = (System.nanoTime() - started) / 1_000_000L;

        assertTrue(first.isEmpty());
        assertTrue(firstMs < 3000, "access quote must fail within 2s, was " + firstMs + "ms");
        assertTrue(client.isCircuitOpen());
        assertEquals(1, hits.get());

        started = System.nanoTime();
        Optional<BigDecimal> second = client.quoteForAccess("LOT", "粤A12345", "BLUE");
        long secondMs = (System.nanoTime() - started) / 1_000_000L;

        assertTrue(second.isEmpty());
        assertTrue(secondMs < 200, "open circuit must skip remote, was " + secondMs + "ms");
        assertEquals(1, hits.get(), "circuit should not hit hanging server again");
    }

    @Test
    void probeTimeoutOpensCircuitSoAccessQuoteSkipsImmediately() throws Exception {
        startServer(exchange -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            writeJson(exchange, "{\"amount\":1}");
        });
        stubRemoteUrl();

        client.probeHealth("LOT", "粤A12345", "BLUE", java.time.Duration.ofMillis(400));
        assertTrue(client.isCircuitOpen());
        assertEquals(1, hits.get());

        long started = System.nanoTime();
        Optional<BigDecimal> skipped = client.quoteForAccess("LOT", "粤B00001", "BLUE");
        long skippedMs = (System.nanoTime() - started) / 1_000_000L;

        assertTrue(skipped.isEmpty());
        assertTrue(skippedMs < 200, "recognition must skip during probe circuit, was " + skippedMs + "ms");
        assertEquals(1, hits.get());
    }

    @Test
    void accessQuoteReturnsAmountWhenCloudIsUp() throws Exception {
        startServer(exchange -> {
            hits.incrementAndGet();
            writeJson(exchange, "{\"amount\":12.5}");
        });
        stubRemoteUrl();

        assertEquals(new BigDecimal("12.5"), client.quoteForAccess("LOT", "粤A12345", "BLUE").orElseThrow());
        assertFalse(client.isCircuitOpen());
        assertEquals(1, hits.get());
    }

    private void stubRemoteUrl() {
        NodeSettings settings = new NodeSettings(NodeMode.EDGE);
        settings.setFeeApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/quote");
        when(settingsRepository.findById(NodeSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/quote", handler);
        server.start();
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
