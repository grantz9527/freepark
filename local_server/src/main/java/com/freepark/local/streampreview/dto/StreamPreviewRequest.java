package com.freepark.local.streampreview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StreamPreviewRequest(@NotBlank @Size(max = 512) String streamUrl) {
}
