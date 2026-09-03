package com.freepark.driver.api.model;

/**
 * 一体机语音播报模式（由驱动声明，供平台编排与界面展示）。
 *
 * <p>设备差异：
 * <ul>
 *   <li>{@link #FREE_TEXT}：万能语音——可播任意合成文本（TTS），车牌/金额等
 *       变量话术可完整播出；</li>
 *   <li>{@link #PRESET_ONLY}：只能播设备出厂预置的固定句子/编号，无法实时合成
 *       任意文本（含变量的话术会播不完整，须降级）；</li>
 *   <li>{@link #HYBRID}：优先按自由文本播出，文本不属于预置集合时回退到预置句。</li>
 * </ul>
 */
public enum VoiceMode {

    /** 万能语音：任意文本可直接播出。 */
    FREE_TEXT,

    /** 仅预置语音：只能播 {@link VoiceCapability#presets()} 中的固定句/编号。 */
    PRESET_ONLY,

    /** 混合：自由文本优先，播不了时回退预置句。 */
    HYBRID
}
