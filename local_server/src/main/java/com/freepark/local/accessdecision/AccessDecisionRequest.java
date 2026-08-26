package com.freepark.local.accessdecision;

import java.util.List;
import java.util.UUID;

import com.freepark.local.domain.PlateColor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccessDecisionRequest(
    @NotNull UUID laneId,
    @NotBlank String plateNumber,
    @NotNull PlateColor plateColor,
    @NotNull AccessDirection direction,
    /** Lane plate colors configured to intercept; provided by the caller when available. */
    List<PlateColor> interceptColors,
    /** Whether an open in-lot session exists; only meaningful for EXIT. */
    Boolean hasOpenSession) {}
