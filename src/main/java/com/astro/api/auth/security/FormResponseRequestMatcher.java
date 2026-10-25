package com.astro.api.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class FormResponseRequestMatcher implements RequestMatcher {
    public static final FormResponseRequestMatcher INSTANCE = new FormResponseRequestMatcher();

    private FormResponseRequestMatcher() {
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        if ("GET".equals(method)) {
            return path.equals("/forms/received") || path.matches("/forms/received/[^/]+")
                    || path.matches("/forms/[^/]+/responses") || path.matches("/forms/[^/]+/images/[^/]+");
        }
        return "POST".equals(method)
                && (path.matches("/forms/[^/]+/responses") || path.matches("/forms/[^/]+/review"));
    }
}
