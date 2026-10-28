package com.astro.api.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.servlet.HandlerMapping;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class SafeExceptionLogging {
    private SafeExceptionLogging() {}

    public static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? "unmatched" : pattern.toString().replaceAll("[\\r\\n\\t]", "_");
    }

    public static void log(Logger logger, String code, String operation, Exception error,
                           HttpServletRequest request, boolean warning) {
        LoggingEventBuilder event = warning ? logger.atWarn() : logger.atError();
        event.addKeyValue("event", "operation_failed")
                .addKeyValue("error_code", code)
                .addKeyValue("operation", operation)
                .addKeyValue("request_id", MDC.get("request_id"))
                .addKeyValue("http_route", route(request))
                .addKeyValue("http_method", request.getMethod())
                .addKeyValue("exception_type", error.getClass().getName())
                .setCause(safe(error, Collections.newSetFromMap(new IdentityHashMap<>()), 0))
                .log("Falha na operação {} código={} request_id={}", operation, code, MDC.get("request_id"));
    }

    static Throwable safe(Throwable error, Set<Throwable> seen, int depth) {
        if (error == null || depth >= 6 || !seen.add(error)) return null;
        String code = error instanceof SQLException sql && sql.getSQLState() != null
                && sql.getSQLState().matches("[A-Z0-9]{5}") ? " sqlstate=" + sql.getSQLState() : "";
        RuntimeException result = new RuntimeException(error.getClass().getName() + code,
                safe(error.getCause(), seen, depth + 1));
        result.setStackTrace(error.getStackTrace());
        return result;
    }
}
