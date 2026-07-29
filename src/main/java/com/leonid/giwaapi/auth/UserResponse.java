package com.leonid.giwaapi.auth;

public record UserResponse(Long userId, String email) {
    public static UserResponse from(User user) {
        return new UserResponse(user.userId(), user.email());
    }
}
