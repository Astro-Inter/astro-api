package com.astro.api.common.response;

import java.util.List;

public record ApiResult<T>(
        boolean success,
        String message,
        T data,
        List<String> errors,
        String path
) {

    public static <T> ApiResult<T> success(String message, T data, String path) {
        return new ApiResult<>(
                true,
                message,
                data,
                null,
                path
        );
    }

    public static <T> ApiResult<T> error(String message, List<String> errors, String path) {
        return new ApiResult<>(
                false,
                message,
                null,
                errors,
                path
        );
    }
}
