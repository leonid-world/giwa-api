package com.leonid.giwaapi.common.error;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        int status,
        String code,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(status, code, message, path, Instant.now(), Map.of());
    }

    public static ApiError validation(
            int status,
            String code,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        return new ApiError(status, code, message, path, Instant.now(), fieldErrors);
    }
}
