package com.freepark.cloud.auth;

import java.util.UUID;

import com.freepark.cloud.domain.CloudUser;

public record UserView(UUID id, String username, String displayName, String role) {

    public static UserView from(CloudUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name());
    }
}
