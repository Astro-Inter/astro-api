package com.astro.api.unit.controller;

import com.astro.api.unit.dto.request.UpdateUnitNrsRequest;
import com.astro.api.unit.service.UnitService;
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

class UnitAuthorizationTest {
    @Test
    void protectsAllFiveEndpointsWithTheExistingManagerRoles() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(UnitController.class);
            var service = context.getBean(UnitService.class);
            var request = new MockHttpServletRequest();
            var body = new UpdateUnitNrsRequest(List.of());
            for (String role : List.of("GESTOR", "GESTOR_WORKSPACE")) {
                authenticate(role);
                assertEquals(200, controller.findAll(null, request).getStatusCode().value());
                assertEquals(200, controller.toggleStatus(12L, null, request).getStatusCode().value());
                assertEquals(200, controller.findNrs(12L, null, request).getStatusCode().value());
                assertEquals(200, controller.findNrOptions(12L, null, request).getStatusCode().value());
                assertEquals(200, controller.updateNrs(12L, body, null, request).getStatusCode().value());
            }
            clearInvocations(service);
            for (String role : List.of("COLABORADOR", "ADMIN")) {
                authenticate(role);
                assertThrows(AccessDeniedException.class, () -> controller.findAll(null, request));
                assertThrows(AccessDeniedException.class, () -> controller.toggleStatus(12L, null, request));
                assertThrows(AccessDeniedException.class, () -> controller.findNrs(12L, null, request));
                assertThrows(AccessDeniedException.class, () -> controller.findNrOptions(12L, null, request));
                assertThrows(AccessDeniedException.class, () -> controller.updateNrs(12L, body, null, request));
            }
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.findAll(null, request));
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
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean UnitService unitService() { return mock(UnitService.class); }
        @Bean UnitController unitController(UnitService service) { return new UnitController(service); }
    }
}
