package com.freepark.local.space;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLocationRequest(@NotBlank @Size(max = 80) String name) {
}
