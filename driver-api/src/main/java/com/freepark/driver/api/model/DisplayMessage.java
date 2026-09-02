package com.freepark.driver.api.model;

/** 屏显消息：kind + text 为语义内容，durationMs 为停留时长。 */
public record DisplayMessage(DisplayKind kind, String text, long durationMs) {

    public static DisplayMessage of(DisplayKind kind, String text) {
        return new DisplayMessage(kind, text, 0);
    }

    public static DisplayMessage of(DisplayKind kind, String text, long durationMs) {
        return new DisplayMessage(kind, text, durationMs);
    }
}
