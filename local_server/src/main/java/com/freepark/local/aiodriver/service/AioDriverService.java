package com.freepark.local.aiodriver.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.freepark.driver.api.AIODriverFactory;
import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.AioDriverException;
import com.freepark.driver.api.model.Capability;
import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.api.model.DeviceStatus;
import com.freepark.driver.api.model.DisplayCapability;
import com.freepark.driver.api.model.DisplayKind;
import com.freepark.driver.api.model.DisplayMessage;
import com.freepark.driver.api.model.GateState;
import com.freepark.driver.api.model.VehicleType;
import com.freepark.driver.api.model.VoiceCapability;
import com.freepark.driver.api.model.VoiceKind;
import com.freepark.driver.api.model.VoiceMessage;
import com.freepark.driver.api.registry.AioDriverRegistry;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.UserRole;

/**
 * 一体机驱动门面：平台侧唯一“设备档案 → DeviceConfig → 绑定实例”的桥。
 *
 * <p>驱动包不接触数据库/Spring；本服务负责把 parking_barrier 上的设备档案
 * （序列号/品牌/启用状态/连接地址）组装成 {@link DeviceConfig}，交给注册表按
 * deviceKey 幂等获取实例，再按统一规范
 * {@link com.freepark.driver.api.ParkingAIODevice} 下发命令。
 *
 * <p>识别推送（相机 POST /plate）与平台轮询取走识别事件（pullRecognition）
 * 的编排暂未接入，属骨架预留点。
 */
@Service
public class AioDriverService {

    /** 统一命令动作（对齐 ParkingAIODevice 对外能力）。 */
    public enum Action {
        OPEN, CLOSE, ALWAYS_ON, ALWAYS_OFF, SHOW, SPEAK, STATUS
    }

    /** 已装配的驱动工厂视图。 */
    public record FactoryView(
            String brand, String model, String displayName,
            List<String> supportedModels, Set<Capability> capabilities,
            VoiceCapability voiceCapability, DisplayCapability displayCapability) {
    }

    /** 一台绑定实例的运行时快照。 */
    public record DeviceRuntimeView(
            String deviceKey, boolean online, GateState gateState, String message, long epochMillis) {
    }

    /** 一次命令下发的结果。 */
    public record CommandResult(
            String deviceKey, String brand, String model, Action action,
            boolean success, String message, DeviceRuntimeView status) {
    }

    private final ParkingBarrierRepository barriers;
    private final LocalUserRepository users;
    private final AioDriverRegistry registry;

    private static final Logger log = LoggerFactory.getLogger(AioDriverService.class);

    public AioDriverService(
            ParkingBarrierRepository barriers,
            LocalUserRepository users,
            AioDriverRegistry registry) {
        this.barriers = barriers;
        this.users = users;
        this.registry = registry;
    }

    /** 当前依赖中已自动发现并注册的全部驱动工厂。 */
    public List<FactoryView> factories() {
        return registry.factories().stream()
                .sorted(Comparator.comparing(AIODriverFactory::brand))
                .map(f -> new FactoryView(f.brand(), f.model(), f.displayName(),
                        f.supportedModels(), f.capabilities(), f.voiceCapability(), f.displayCapability()))
                .toList();
    }

    /** 已被实例化（下发过命令/查询过）的设备运行时快照。 */
    public List<DeviceRuntimeView> devices() {
        return registry.devices().stream()
                .sorted(Comparator.comparing(ParkingAIODevice::deviceKey))
                .map(this::runtimeOf)
                .toList();
    }

    /**
     * 对指定设备档案执行统一命令（仅管理员）。
     *
     * @param code        设备序列号（parking_barrier.code）
     * @param action      命令动作
     * @param kind        SHOW 用 DisplayKind / SPEAK 用 VoiceKind，缺省 FREE_TEXT
     * @param vehicleType SPEAK 用 VehicleType（面向哪类车辆的话术），缺省 OTHER
     * @param text        SHOW 屏显内容（用换行分隔的有序建议行，驱动按屏幕行数取舍）；
     *                    SPEAK 为播报文本
     */
    public CommandResult execute(UUID requesterId, String code, Action action,
                                 String kind, String vehicleType, String text) {
        requireAdmin(requesterId);
        ParkingBarrier barrier = barriers.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        if (!barrier.isEnabled()) {
            throw new BusinessException(ErrorCode.DEVICE_DISABLED);
        }
        String brand = barrier.getBrand() == null ? "" : barrier.getBrand().trim();
        if (!registry.supports(brand, modelOf(barrier))) {
            throw new BusinessException(ErrorCode.DEVICE_NOT_FOUND);
        }
        DeviceConfig config = config(barrier, brand);
        ParkingAIODevice device = registry.deviceFor(config);
        try {
            apply(device, action, kind, vehicleType, text);
            return new CommandResult(device.deviceKey(), brand, config.model(), action,
                    true, "ok", runtimeOf(device));
        } catch (AioDriverException e) {
            // 命令通道异常（设备不在线/协议错误）：返回结果而非抛错，便于界面展示
            return new CommandResult(device.deviceKey(), brand, config.model(), action,
                    false, e.getMessage(), runtimeOf(device));
        }
    }

    /**
     * 系统内部向识别一体机主动下发开闸命令（识别放行联动等，非管理员调用）。
     *
     * <p>仅当档案具备可装配驱动（品牌有已接入工厂）与连接地址（host/port）时
     * 才会真正发起 HTTP 下发；缺少驱动/地址或命令通道异常均返回 {@code false}，
     * 由调用方决定回退到「响应带回」等其它开闸路径，本方法不抛业务异常。
     *
     * @param barrier 设备档案（parking_barrier）
     * @param source  触发来源标识（如 recognition:code），用于日志追踪
     * @return true 表示已成功向设备下发开闸指令
     */
    public boolean openGateSystem(ParkingBarrier barrier, String source) {
        return openGateSystem(barrier, source, null, null);
    }

    /**
     * 系统内部开闸，并可顺带下发语音/屏显（缴费离场：车牌 + 一路顺风/欢迎光临）。
     * 开闸成功后播报失败只记日志，不影响开闸结果。
     */
    public boolean openGateSystem(ParkingBarrier barrier, String source, String voiceText, String ledText) {
        if (barrier == null || !barrier.isEnabled()) {
            return false;
        }
        String brand = barrier.getBrand() == null ? "" : barrier.getBrand().trim();
        if (brand.isBlank() || !registry.supports(brand, modelOf(barrier))) {
            log.warn("识别联动跳过开闸：code={} 品牌='{}' 无已接入驱动（source={}）",
                    barrier.getCode(), brand, source);
            return false;
        }
        if (barrier.getHost() == null || barrier.getHost().isBlank()) {
            log.warn("识别联动跳过开闸：code={} brand={} 档案缺少连接地址 host（source={}）",
                    barrier.getCode(), brand, source);
            return false;
        }
        DeviceConfig config = config(barrier, brand);
        try {
            ParkingAIODevice device = registry.deviceFor(config);
            device.openGate();
            announceQuietly(device, voiceText, ledText);
            log.info("推送了开闸指令：code={} brand={} host={}:{}（source={}）",
                    barrier.getCode(), brand, barrier.getHost(), barrier.getPort(), source);
            return true;
        } catch (AioDriverException e) {
            log.warn("推送开闸指令失败：code={} brand={} host={}:{}：{}（source={}）",
                    barrier.getCode(), brand, barrier.getHost(), barrier.getPort(), e.getMessage(), source);
            return false;
        }
    }

    private void announceQuietly(ParkingAIODevice device, String voiceText, String ledText) {
        try {
            if (ledText != null && !ledText.isBlank()) {
                device.show(DisplayMessage.of(DisplayKind.WELCOME, splitLines(ledText), 0));
            }
            if (voiceText != null && !voiceText.isBlank()) {
                device.speak(VoiceMessage.of(VoiceKind.WELCOME_OUT, VehicleType.TEMPORARY, voiceText));
            }
        } catch (RuntimeException ex) {
            log.warn("开闸后播报失败 device={}：{}", device.deviceKey(), ex.getMessage());
        }
    }

    // ==================== 私有 ====================

    private DeviceConfig config(ParkingBarrier barrier, String brand) {
        return new DeviceConfig(
                barrier.getCode(),
                brand,
                modelOf(barrier),
                barrier.getHost(),
                barrier.getPort(),
                null,
                null,
                Map.of());
    }

    /** 档案型号未填时按整条产品线通配（factory.model() == "*"）。 */
    private String modelOf(ParkingBarrier barrier) {
        String model = barrier.getModel();
        return model == null || model.isBlank() ? "*" : model.trim();
    }

    private void apply(ParkingAIODevice device, Action action, String kind, String vehicleType, String text) {
        switch (action) {
            case OPEN -> device.openGate();
            case CLOSE -> device.closeGate();
            case ALWAYS_ON -> device.setAlwaysOpen(true);
            case ALWAYS_OFF -> device.setAlwaysOpen(false);
            case SHOW -> {
                requireText(text, action);
                device.show(DisplayMessage.of(displayKind(kind), splitLines(text), 0));
            }
            case SPEAK -> {
                requireText(text, action);
                device.speak(VoiceMessage.of(voiceKind(kind), vehicleType(vehicleType), text));
            }
            case STATUS -> {
                // 无需动作，execute 末尾会返回最新状态
            }
        }
    }

    private VehicleType vehicleType(String raw) {
        return enumValue(VehicleType.class, raw, VehicleType.OTHER);
    }

    private void requireText(String text, Action action) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    action + " 需要 text 内容");
        }
    }

    /** 屏显文本按换行拆成有序建议行（去首尾空白与空行，行序即优先级）。 */
    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String s = line.trim();
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        return lines;
    }

    private DisplayKind displayKind(String kind) {
        return enumValue(DisplayKind.class, kind, DisplayKind.FREE_TEXT);
    }

    private VoiceKind voiceKind(String kind) {
        return enumValue(VoiceKind.class, kind, VoiceKind.FREE_TEXT);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private DeviceRuntimeView runtimeOf(ParkingAIODevice device) {
        DeviceStatus s = device.status();
        return new DeviceRuntimeView(device.deviceKey(), s.online(), s.gateState(), s.message(), s.epochMillis());
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
