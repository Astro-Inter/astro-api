package com.astro.api.workspace.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.unit.controller.UnitSettingsController;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.service.UnitSettingsService;
import com.astro.api.workspace.dto.request.*;
import com.astro.api.workspace.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceSettingsAuthorizationTest {
    @Test
    void restrictsAllWorkspaceSettingsEndpointsToWorkspaceManagers() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            WorkspaceSettingsController workspace = context.getBean(WorkspaceSettingsController.class);
            UnitSettingsController units = context.getBean(UnitSettingsController.class);
            WorkspaceSettingsService settings = context.getBean(WorkspaceSettingsService.class);
            WorkspaceImportService imports = context.getBean(WorkspaceImportService.class);
            WorkspaceManagerTransferService transfers = context.getBean(WorkspaceManagerTransferService.class);
            UnitSettingsService unitService = context.getBean(UnitSettingsService.class);
            MockHttpServletRequest request = new MockHttpServletRequest();
            UnitRequest unit = new UnitRequest("Matriz", "01234567", "SP", "São Paulo", "Centro", "Rua A", "10", null);
            UpdateWorkspaceRequest company = new UpdateWorkspaceRequest("Empresa", "12345678000199");
            WorkspaceManagerTransferRequest transfer = new WorkspaceManagerTransferRequest(9L);
            MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});
            for (String role : List.of("GESTOR", "COLABORADOR", "ADMIN")) {
                authenticate(role);
                assertThrows(AccessDeniedException.class, () -> workspace.find(null, request));
                assertThrows(AccessDeniedException.class, () -> workspace.update(company, null, request));
                assertThrows(AccessDeniedException.class, () -> workspace.importFile(file, null, null, request));
                assertThrows(AccessDeniedException.class, () -> workspace.transferManager(transfer, null, request));
                assertThrows(AccessDeniedException.class, () -> units.create(unit, null, request));
                assertThrows(AccessDeniedException.class, () -> units.update(12L, unit, null, request));
            }
            verifyNoInteractions(settings, imports, transfers, unitService);
            authenticate("GESTOR_WORKSPACE");
            when(unitService.create(eq(unit), any())).thenReturn(new UnitResponse(12L, 3L, "Matriz",
                    "Ativo", 0, "01234567", "Rua A", "São Paulo", "Centro", "SP", null, "10"));
            assertEquals(200, workspace.find(null, request).getStatusCode().value());
            assertEquals(200, workspace.update(company, null, request).getStatusCode().value());
            assertEquals(200, workspace.importFile(file, null, null, request).getStatusCode().value());
            assertEquals(200, workspace.transferManager(transfer, null, request).getStatusCode().value());
            assertEquals(201, units.create(unit, null, request).getStatusCode().value());
            assertEquals(200, units.update(12L, unit, null, request).getStatusCode().value());
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
        @Bean WorkspaceSettingsService settings() { return mock(WorkspaceSettingsService.class); }
        @Bean WorkspaceImportService imports() { return mock(WorkspaceImportService.class); }
        @Bean WorkspaceManagerTransferService transfers() { return mock(WorkspaceManagerTransferService.class); }
        @Bean UnitSettingsService units() { return mock(UnitSettingsService.class); }
        @Bean WorkspaceSettingsController workspaceController(WorkspaceSettingsService settings, WorkspaceImportService imports,
                WorkspaceManagerTransferService transfers) {
            return new WorkspaceSettingsController(settings, imports, transfers);
        }
        @Bean UnitSettingsController unitController(UnitSettingsService units) { return new UnitSettingsController(units); }
    }
}
