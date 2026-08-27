package com.freepark.local.auth.dto;

public record LoginResponse(String token, String tokenType, long expiresIn, UserView user) {
}
