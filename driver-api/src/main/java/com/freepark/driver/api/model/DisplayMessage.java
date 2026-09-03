package com.freepark.driver.api.model;

import java.util.List;

/**
 * 屏显消息：kind 为展示场景，lines 为按优先级排序的建议行，durationMs 为停留时长。
 *
 * <p>平台按场景把可展示内容拼成有序行列表（如 [车牌, 欢迎语, 收费提示]），
 * 由驱动按自身屏幕规格取舍排版：
 * <ul>
 *   <li>静态多行屏：取前 {@link DisplayCapability#rows()} 行逐行显示；</li>
 *   <li>可滚动屏：把全部行滚动播出；</li>
 *   <li>行宽/字库/每行字数限制等由驱动内部适配，平台不感知。</li>
 * </ul>
 */
public record DisplayMessage(DisplayKind kind, List<String> lines, long durationMs) {

    public DisplayMessage {
        kind = kind == null ? DisplayKind.FREE_TEXT : kind;
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** 多行展示：按行序为优先级，屏幕行数不足时由驱动截取。 */
    public static DisplayMessage of(DisplayKind kind, List<String> lines, long durationMs) {
        return new DisplayMessage(kind, lines, durationMs);
    }

    /** 便捷构造：单行内容。 */
    public static DisplayMessage of(DisplayKind kind, String text) {
        return of(kind, text, 0);
    }

    /** 便捷构造：单行内容 + 停留时长。 */
    public static DisplayMessage of(DisplayKind kind, String text, long durationMs) {
        return new DisplayMessage(kind, List.of(text), durationMs);
    }
}
