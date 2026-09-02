package com.freepark.driver.zhensi.transport;

/**
 * 臻识驱动内部使用的“语义动作 → 品牌命令”载体。
 *
 * <p>action 为该驱动私有的品牌命令名（不是平台统一规范的一部分），
 * 由 {@code ZhensiAIODevice} 在实现统一规范方法时翻译而来。
 */
public record ZhensiCommand(String action, String text, long durationMs) {

    public static ZhensiCommand of(String action) {
        return new ZhensiCommand(action, null, 0);
    }

    public static ZhensiCommand of(String action, String text, long durationMs) {
        return new ZhensiCommand(action, text, durationMs);
    }
}
