package com.freepark.cloud.auth;

public record LoginResponse(String token, String tokenType, long expiresIn, UserView user) {
}
