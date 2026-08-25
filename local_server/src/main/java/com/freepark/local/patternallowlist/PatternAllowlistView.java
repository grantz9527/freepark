package com.freepark.local.patternallowlist;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.PatternAllowlist;

public record PatternAllowlistView(
        UUID id,
        UUID lotId,
        String name,
        String pattern,
        String remark,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static PatternAllowlistView from(PatternAllowlist entry) {
        return new PatternAllowlistView(
                entry.getId(),
                entry.getLot().getId(),
                entry.getName(),
                entry.getPattern(),
                entry.getRemark(),
                entry.isEnabled(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
