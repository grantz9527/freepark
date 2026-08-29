package com.freepark.local.recognition.dto;

import jakarta.validation.constraints.Size;

/**
 * 标记识别记录异常（如 exit_unmatched / not_internal_vehicle）。
 */
public record MarkAbnormalRequest(
        @Size(max = 64) String reason) {
}
