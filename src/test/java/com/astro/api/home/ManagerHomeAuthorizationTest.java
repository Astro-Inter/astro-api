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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ManagerHomeAuthorizationTest {
    @Test
    void allowsOnlyManagementRoles() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            HomeController controller = context.getBean(HomeController.class);
            for (String role : List.of("GESTOR", "GESTOR_WORKSPACE")) {
                authenticate(role);
                assertDoesNotThrow(() -> controller.loadManagerHome(null, new MockHttpServletRequest()));
            }
            for (String role : List.of("COLABORADOR", "ADMIN")) {
                authenticate(role);
                assertThrows(AccessDeniedException.class,
                        () -> controller.loadManagerHome(null, new MockHttpServletRequest()));
            }
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class,
                    () -> controller.loadManagerHome(null, new MockHttpServletRequest()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "manager", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean
        HomeService homeService() {
            return mock(HomeService.class);
        }

        @Bean
        HomeController homeController(HomeService homeService) {
            return new HomeController(homeService);
        }
    }
}
