package com.freepark.local.patternallowlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePatternAllowlistRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 255) String pattern,
        @Size(max = 255) String remark,
        Boolean enabled) {
}
