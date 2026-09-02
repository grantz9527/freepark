package com.freepark.driver.api.model;

/** 语音播报消息。 */
public record VoiceMessage(VoiceKind kind, String text) {

    public static VoiceMessage of(VoiceKind kind, String text) {
        return new VoiceMessage(kind, text);
    }
}
