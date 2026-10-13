package com.astro.api.home;

import com.astro.api.home.controller.HomeController;
import com.astro.api.home.service.HomeService;
import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class HomeAuthorizationTest {
    @Test
    void permitsOnlyHomeRolesThroughTheMethodSecurityProxy() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(HomeController.class);
            for (String role : List.of("COLABORADOR", "GESTOR", "GESTOR_WORKSPACE")) {
                authenticate(role);
                assertDoesNotThrow(() -> controller.collaboratorHome(principal(), new MockHttpServletRequest()));
            }
            authenticate("ADMIN");
            assertThrows(AccessDeniedException.class,
                    () -> controller.collaboratorHome(principal(), new MockHttpServletRequest()));
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class,
                    () -> controller.collaboratorHome(principal(), new MockHttpServletRequest()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser("uid", "user@astro.com", Role.COLABORADOR);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean HomeService homeService() { return mock(HomeService.class); }
        @Bean HomeController homeController(HomeService service) { return new HomeController(service); }
    }
}
