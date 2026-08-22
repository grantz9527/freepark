package com.freepark.local.operator;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.LocalUser;

public record OperatorView(
        UUID id,
        String username,
        String displayName,
        String role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static OperatorView from(LocalUser user) {
        return new OperatorView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
