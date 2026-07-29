package com.leonid.giwaapi.auth;

public record AuthResponse(String accessToken, UserResponse user) {
}
