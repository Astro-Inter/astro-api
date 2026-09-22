package com.astro.api.common.response;

import java.util.List;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<String> errors,
        String path
) {

    public static <T> ApiResponse<T> success(String message, T data, String path) {
        return new ApiResponse<>(
                true,
                message,
                data,
                null,
                path
        );
    }

    public static <T> ApiResponse<T> error(String message, List<String> errors, String path) {
        return new ApiResponse<>(
                false,
                message,
                null,
                errors,
                path
        );
    }
}
