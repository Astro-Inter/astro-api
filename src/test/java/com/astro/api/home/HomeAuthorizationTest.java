package com.astro.api.home;

import com.astro.api.home.controller.HomeController;
import com.astro.api.home.service.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeAuthorizationTest {
    @Test
    void permitsOnlyManagerRolesThroughTheMethodSecurityProxy() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(HomeController.class);
            var service = context.getBean(HomeService.class);
            for (String role : List.of("GESTOR", "GESTOR_WORKSPACE")) {
                authenticate(role);
                assertEquals(200, controller.loadHome(null, new MockHttpServletRequest()).getStatusCode().value());
            }
            clearInvocations(service);
            for (String role : List.of("COLABORADOR", "ADMIN")) {
                authenticate(role);
                assertThrows(AccessDeniedException.class,
                        () -> controller.loadHome(null, new MockHttpServletRequest()));
            }
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class,
                    () -> controller.loadHome(null, new MockHttpServletRequest()));
            verifyNoInteractions(service);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "manager", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean HomeService homeService() { return mock(HomeService.class); }
        @Bean HomeController homeController(HomeService service) {
            return new HomeController(service);
        }
    }
}
