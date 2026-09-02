package com.freepark.driver.api;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.freepark.driver.api.model.Capability;
import com.freepark.driver.api.model.DeviceConfig;

/**
 * 一体机驱动工厂（类型级契约）。
 *
 * <p>每个厂商一个实现类，描述自己支持哪类设备、具备哪些能力，
 * 并按物理设备配置创建绑定实例。同型号多台设备 = 多次 {@link #create}。
 *
 * <p>设计约束（可发布到 Maven 仓库作为平台依赖）：
 * <ul>
 *   <li>驱动包不允许访问数据库；一切连接/身份信息由平台从设备档案
 *       读取后经 {@link DeviceConfig} 注入；</li>
 *   <li>不依赖 Spring 等平台框架，可独立编译、测试与发布；</li>
 *   <li>一个驱动工程内可声明多个工厂类（同一厂商多产品线）。</li>
 * </ul>
 */
public interface AIODriverFactory {

    /** 品牌标识（与设备档案中的 brand 对应，大小写不敏感）。 */
    String brand();

    /**
     * 型号匹配串。支持：
     * <ul>
     *   <li>精确型号，如 {@code "HTZ-S02"}；</li>
     *   <li>{@code "*"} 表示该品牌整条产品线都由本工厂接管。</li>
     * </ul>
     */
    String model();

    /** 显示名称（如 "臻识 HTZ 系列"），用于界面展示。 */
    default String displayName() {
        return brand() + " " + model();
    }

    /** 本驱动实现的能力面。 */
    default Set<Capability> capabilities() {
        return Set.of(Capability.PLATE_RECOGNITION, Capability.GATE_CONTROL,
                Capability.DISPLAY, Capability.VOICE);
    }

    /**
     * 对外展示的受支持设备型号清单（对接页「支持哪些型号的设备」）。
     *
     * <p>约定：
     * <ul>
     *   <li>返回空列表：型号不做细分，按 {@link #model()} 接管——
     *       通常 {@code model()} 为 {@code "*"}，即覆盖该品牌整条产品线；</li>
     *   <li>返回具体型号（如 {@code "HTZ-S02"}）：多个驱动/型号并存时，
     *       卡片将逐一列出，帮助区分本驱动能接管的设备。</li>
     * </ul>
     */
    default List<String> supportedModels() {
        return List.of();
    }

    /** 判断本工厂是否接管该品牌型号。 */
    default boolean supports(String brand, String model) {
        return brand() != null && brand().equalsIgnoreCase(brand)
                && ("*".equals(model()) || model().equalsIgnoreCase(model));
    }

    /**
     * 为一台物理设备创建绑定实例。
     *
     * @param config 平台注入的设备档案（驱动不查库，只用本配置）
     * @return 绑定该设备的一体机实例
     */
    ParkingAIODevice create(DeviceConfig config);

    /** 供注册表做品牌匹配用的规范化键。 */
    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
