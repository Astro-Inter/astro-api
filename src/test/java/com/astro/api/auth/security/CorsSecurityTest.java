package com.astro.api.auth.security;

import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import jakarta.servlet.Filter;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsSecurityTest {
    private static final String FRONTEND_ORIGIN = "https://astro-inter.github.io";

    @Test
    void allowsRolePreflightsWithoutAuthenticatingWithFirebase() throws Exception {
        try (var context = context(null)) {
            MockMvc mvc = mvc(context);
            for (String method : new String[]{"GET", "POST", "PUT", "PATCH"}) {
                mvc.perform(options("/roles/12/nrs")
                                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                        .andExpect(status().isOk())
                        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(method)))
                        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")))
                        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")))
                        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
            }
            verifyNoInteractions(context.getBean(FirebaseTokenService.class), context.getBean(AuthorizationService.class));
        }
    }

    @Test
    void rejectsUnlistedOrigins() throws Exception {
        try (var context = context(null)) {
            mvc(context).perform(options("/roles")
                            .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Test
    void keepsAuthenticationRequiredAndAddsCorsHeadersToTheErrorResponse() throws Exception {
        try (var context = context(null)) {
            mvc(context).perform(get("/roles").param("firebase_uid", "gestor")
                            .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401
                            || result.getResponse().getStatus() == 403))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
        }
    }

    @Test
    void allowsConfiguredOriginsWithoutOpeningAccessToEveryOrigin() throws Exception {
        try (var context = context("http://localhost:5173,https://astro-inter.github.io")) {
            mvc(context).perform(options("/roles")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        }
    }

    @Test
    void rejectsUnsupportedPreflightHeaders() throws Exception {
        try (var context = context(null)) {
            mvc(context).perform(options("/roles")
                            .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-untrusted-header"))
                    .andExpect(status().isForbidden());
        }
    }

    private AnnotationConfigWebApplicationContext context(String origins) throws Exception {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        if (origins != null) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "CORS_ALLOWED_ORIGINS=" + origins);
        }
        context.register(TestConfig.class);
        context.refresh();
        return context;
    }

    private MockMvc mvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Configuration
    @EnableWebSecurity
    @Import({SecurityConfig.class, CorsConfig.class})
    static class TestConfig {
        @Bean FirebaseTokenService firebaseTokenService() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorizationService() { return mock(AuthorizationService.class); }
        @Bean FirebaseAuthenticationFilter firebaseAuthenticationFilter(FirebaseTokenService tokens,
                                                                         AuthorizationService authorization) {
            return new FirebaseAuthenticationFilter(tokens, authorization);
        }
    }
}
