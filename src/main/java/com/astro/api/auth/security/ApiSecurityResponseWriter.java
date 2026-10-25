package com.astro.api.auth.security;

import com.astro.api.common.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

final class ApiSecurityResponseWriter {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private ApiSecurityResponseWriter() {
    }

    static void write(HttpServletRequest request, HttpServletResponse response, int status,
                      String message, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        JSON_MAPPER.writeValue(response.getWriter(), ApiResult.error(message, List.of(detail), request.getRequestURI()));
    }
}
