package com.freepark.local.auth;

public record LoginResponse(String token, String tokenType, long expiresIn, UserView user) {
}
