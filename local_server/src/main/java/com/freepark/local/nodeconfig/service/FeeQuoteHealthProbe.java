package com.freepark.local.nodeconfig.service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.freepark.local.domain.NodeSettings;
import com.freepark.local.domain.NodeSettingsRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.domain.PlateColor;

import jakarta.annotation.PreDestroy;

/**
 * 算费接口后台探活：随机取本机在停车辆（没有则随机车牌）打一次算费。
 * 15 秒无响应则让识别路径在 30 秒内跳过算费，避免云端卡死堵住开闸。
 */
@Component
public class FeeQuoteHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(FeeQuoteHealthProbe.class);

    private static final long TICK_INTERVAL_SECONDS = 10;
    private static final String PROVINCES = "京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼";
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String ALNUM = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final FeeQuoteClient feeQuoteClient;
    private final NodeSettingsRepository settingsRepository;
    private final ParkingSessionRepository sessions;
    private final ParkingLotRepository lots;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fee-quote-probe");
        t.setDaemon(true);
        return t;
    });

    record ProbeTarget(String lotCode, String plateNumber, String plateColor, boolean fromParkedSession) {
    }

    public FeeQuoteHealthProbe(
            FeeQuoteClient feeQuoteClient,
            NodeSettingsRepository settingsRepository,
            ParkingSessionRepository sessions,
            ParkingLotRepository lots) {
        this.feeQuoteClient = feeQuoteClient;
        this.settingsRepository = settingsRepository;
        this.sessions = sessions;
        this.lots = lots;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 5, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("算费接口探活已启动（在停车辆或随机车牌，超时 {} 秒，失败后识别路径 {} 秒内跳过）",
                FeeQuoteClient.PROBE_TIMEOUT.toSeconds(), 30);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    void tick() {
        try {
            NodeSettings settings = settingsRepository.findById(NodeSettings.SINGLETON_ID).orElse(null);
            if (settings == null || settings.isFeeMockEnabled()) {
                return;
            }
            String apiUrl = settings.getFeeApiUrl();
            if (apiUrl == null || apiUrl.isBlank()) {
                return;
            }
            ProbeTarget target = pickTarget();
            log.info("算费探活 {} lot={} plate={} color={}",
                    target.fromParkedSession() ? "在停车辆" : "随机车牌",
                    target.lotCode(), target.plateNumber(), target.plateColor());
            feeQuoteClient.probeHealth(target.lotCode(), target.plateNumber(), target.plateColor());
        } catch (RuntimeException ex) {
            log.warn("算费探活异常：{}", ex.getMessage());
        }
    }

    ProbeTarget pickTarget() {
        List<ParkingSession> parked = sessions.findTop50ByStatusOrderByEntryTimeDesc(ParkingSessionStatus.OPEN);
        if (!parked.isEmpty()) {
            ParkingSession session = parked.get(ThreadLocalRandom.current().nextInt(parked.size()));
            ParkingLot lot = session.getLotId() == null ? null : lots.findById(session.getLotId()).orElse(null);
            if (lot != null && session.getPlateNumber() != null && !session.getPlateNumber().isBlank()) {
                return new ProbeTarget(
                        lot.getCode(),
                        session.getPlateNumber(),
                        colorName(session.getPlateColor()),
                        true);
            }
        }
        List<ParkingLot> allLots = lots.findAll();
        String lotCode = allLots.isEmpty()
                ? null
                : allLots.get(ThreadLocalRandom.current().nextInt(allLots.size())).getCode();
        return new ProbeTarget(lotCode, randomPlate(), PlateColor.BLUE.name(), false);
    }

    static String randomPlate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder plate = new StringBuilder(7);
        plate.append(PROVINCES.charAt(random.nextInt(PROVINCES.length())));
        plate.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        for (int i = 0; i < 5; i++) {
            plate.append(ALNUM.charAt(random.nextInt(ALNUM.length())));
        }
        return plate.toString();
    }

    private static String colorName(PlateColor color) {
        return color == null ? PlateColor.BLUE.name() : color.name();
    }
}
