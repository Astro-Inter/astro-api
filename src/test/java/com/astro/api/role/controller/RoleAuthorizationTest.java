package com.astro.api.role.controller;

import com.astro.api.role.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleAuthorizationTest {
    @Test
    void enforcesManagerRolesThroughTheRealMethodSecurityProxy() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(RoleController.class);
            var service = context.getBean(RoleService.class);
            for (String role : List.of("GESTOR", "GESTOR_WORKSPACE")) {
                authenticate(role);
                assertEquals(200, controller.findAll("gestor", null, new MockHttpServletRequest()).getStatusCode().value());
            }
            clearInvocations(service);
            authenticate("COLABORADOR");
            assertThrows(AccessDeniedException.class,
                    () -> controller.findAll("gestor", null, new MockHttpServletRequest()));
            assertThrows(AccessDeniedException.class,
                    () -> controller.toggleStatus(12L, null, null, new MockHttpServletRequest()));
            verifyNoInteractions(service);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "gestor", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean RoleService roleService() { return mock(RoleService.class); }
        @Bean RoleController roleController(RoleService service) { return new RoleController(service); }
    }
}
