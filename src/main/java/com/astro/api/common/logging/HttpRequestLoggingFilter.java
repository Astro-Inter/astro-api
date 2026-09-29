package com.astro.api.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-ID", requestId);
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        }
        catch (ServletException | IOException | RuntimeException ex) {
            failed = true;
            throw ex;
        }
        finally {
            int status = failed ? 500 : response.getStatus();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = pattern != null ? pattern.toString() : request.getRequestURI();
            // Remove path parameters (such as jsessionid) and prevent multiline log injection.
            route = route.replaceAll(";[^/]*", "").replaceAll("[\\r\\n\\t]", "_");
            var event = status >= 500 ? LOGGER.atError() : status >= 400 ? LOGGER.atWarn() : LOGGER.atInfo();
            event.addKeyValue("event", "http_request")
                    .addKeyValue("http_method", request.getMethod())
                    .addKeyValue("http_route", route)
                    .addKeyValue("http_status", status)
                    .addKeyValue("duration_ms", durationMs)
                    .addKeyValue("request_id", requestId)
                    .log("HTTP request method={} route={} status={} duration_ms={} request_id={}",
                            request.getMethod(), route, status, durationMs, requestId);
        }
    }
}
