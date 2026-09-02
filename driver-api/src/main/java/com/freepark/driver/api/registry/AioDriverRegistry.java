package com.freepark.driver.api.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.freepark.driver.api.AIODriverFactory;
import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.DeviceConfig;

/**
 * 一体机驱动注册表（平台侧装配）。
 *
 * <p>职责：
 * <ol>
 *   <li>收集全部已引入的厂商驱动工厂（依赖式：编译期引入几个驱动模块，
 *       这里就有几个工厂）；</li>
 *   <li>按 品牌+型号 精确路由到工厂；</li>
 *   <li>按 设备实例 创建并缓存 {@link ParkingAIODevice}——同型号多台设备
 *       会得到多个互相隔离的绑定实例（各自持有自己的连接与状态）。</li>
 * </ol>
 *
 * <p>本类无框架依赖；在 Spring 项目中可作为普通 Bean 使用，
 * 通过构造器收集各驱动工厂（依赖式装配）。
 */
public final class AioDriverRegistry {

    private final Map<String, AIODriverFactory> factories = new ConcurrentHashMap<>();
    private final Map<String, ParkingAIODevice> devices = new ConcurrentHashMap<>();

    /** 注册一个驱动工厂（同品牌+型号重复注册时后者覆盖）。 */
    public void register(AIODriverFactory factory) {
        factories.put(key(factory.brand(), factory.model()), factory);
    }

    /** 注册一批（如 Spring 收集到的全部驱动工厂 Bean）。 */
    public void registerAll(Collection<? extends AIODriverFactory> list) {
        list.forEach(this::register);
    }

    /** 当前已装配的全部工厂（供配置页/诊断展示）。 */
    public List<AIODriverFactory> factories() {
        return new ArrayList<>(factories.values());
    }

    /** 当前已实例化（被平台绑定过）的全部设备实例，供诊断/监控页展示。 */
    public List<ParkingAIODevice> devices() {
        return new ArrayList<>(devices.values());
    }

    /** 按品牌+型号精确查找工厂；未找到返回 null。 */
    public AIODriverFactory factoryFor(String brand, String model) {
        AIODriverFactory exact = factories.get(key(brand, model));
        if (exact != null) {
            return exact;
        }
        return factories.get(key(brand, "*"));
    }

    /**
     * 为一台物理设备获取（或创建并缓存）绑定实例。
     *
     * <p>同一 deviceKey 幂等：后续调用直接返回已缓存实例，
     * 平台轮询/命令通道因此可安全地反复取用。
     *
     * @throws IllegalStateException 无工厂接管该品牌型号时
     */
    public ParkingAIODevice deviceFor(DeviceConfig config) {
        AIODriverFactory factory = factoryFor(config.brand(), config.model());
        if (factory == null) {
            throw new IllegalStateException(
                    "没有驱动接管设备 brand=" + config.brand() + " model=" + config.model());
        }
        return devices.computeIfAbsent(config.deviceKey(), k -> factory.create(config));
    }

    /** 是否已有工厂接管该品牌型号。 */
    public boolean supports(String brand, String model) {
        return factoryFor(brand, model) != null;
    }

    private static String key(String brand, String model) {
        return AIODriverFactory.norm(brand) + "|" + AIODriverFactory.norm(model);
    }
}
