package com.freepark.local.auth;

import java.util.UUID;

import com.freepark.local.domain.LocalUser;

public record UserView(UUID id, String username, String displayName, String role) {

    public static UserView from(LocalUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name());
    }
}
