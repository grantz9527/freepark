package com.freepark.driver.api.model;

/**
 * 屏显能力（能力面 {@link Capability#DISPLAY} 的细化描述）。
 *
 * <p>驱动在工厂上声明设备屏幕规格：静态屏（如 2 行 / 4 行）逐行显示
 * {@link DisplayMessage#lines()} 中前 {@link #rows()} 行；可滚动屏（单行 LED
 * 滚动条等）把全部内容滚动播出。行宽/字库/协议字段由驱动内部处理，
 * 平台只按行序提供内容。
 *
 * @param rows      静态屏可同时显示的行数（&lt; 1 按 1 处理）
 * @param scrollable 内容超出行数时是否可滚动显示（如单行滚动条）
 */
public record DisplayCapability(int rows, boolean scrollable) {

    public DisplayCapability {
        rows = rows < 1 ? 1 : rows;
    }

    /** 静态多行屏：只显示前 {@code rows} 行。 */
    public static DisplayCapability fixed(int rows) {
        return new DisplayCapability(rows, false);
    }

    /** 可滚动屏：内容超出可见行数时滚动播出。 */
    public static DisplayCapability scrolling() {
        return new DisplayCapability(1, true);
    }
}
