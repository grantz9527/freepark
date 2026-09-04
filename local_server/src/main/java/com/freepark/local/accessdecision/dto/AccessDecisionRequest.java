package com.freepark.local.accessdecision.dto;

import java.math.BigDecimal;
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
    Boolean hasOpenSession,
    /** 欠费金额（算费接口返回）：>0 时且车场配置了该方向「欠费拦截」则拦截；null 表示未查询/无欠费。 */
    BigDecimal dueAmount) {}
