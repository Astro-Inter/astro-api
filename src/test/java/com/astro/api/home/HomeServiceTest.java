package com.astro.api.home;

import com.astro.api.home.mapper.WorkspaceHomeMapper;
import com.astro.api.home.service.WorkspaceHomeProfileService;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.home.repository.HomeRepository;
import com.astro.api.home.service.HomeService;
import com.astro.api.user.service.UserAvatarService;
import com.astro.api.common.service.ViewedService;
import com.astro.api.notification.service.NotificationService;
import com.astro.api.form.service.FormService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class HomeServiceTest {
    private final HomeRepository repository = mock(HomeRepository.class);
    private final WorkspaceHomeProfileService profiles = mock(WorkspaceHomeProfileService.class);
    private final HomeService service = new HomeService(repository, JsonMapper.builder().build(),
            mock(NotificationService.class), mock(ViewedService.class), mock(FormService.class), mock(UserAvatarService.class), new WorkspaceHomeMapper(JsonMapper.builder().build()), profiles);

    @Test
    void rejectsMissingIdentityBeforeQueryingTheDatabase() {
        assertThrows(AccessDeniedException.class, () -> service.loadHome(null));
        for (String uid : new String[]{null, "", " "}) {
            assertThrows(AccessDeniedException.class,
                    () -> service.loadHome(new AuthenticatedUser(uid, "manager@astro.com", Role.GESTOR)));
        }
        verifyNoInteractions(repository, profiles);
    }

    @Test
    void rejectsMalformedDatabasePayloadsAsServerErrors() {
        for (String json : List.of("broken", "null", "{}", "{\"nome_usuario\":\"Gestor\"}")) {
            when(repository.loadHome("manager")).thenReturn(json);
            assertThrows(IllegalStateException.class,
                    () -> service.loadHome(new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR)));
        }
    }
    @Test
    void enrichesValidatedIndicatorsWithTheOptionalPhoto() {
        when(repository.loadHome("manager")).thenReturn("""
                {"nome_usuario":"Gestor","estatisticas":{
                  "quantidade_unidades":4,"quantidade_colaboradores":0,
                  "quantidade_requisitos":0,"quantidade_conformes":0,
                  "quantidade_proximos_vencimento":0,"quantidade_vencidos":0,
                  "quantidade_pendentes":0,"percentual_conformidade_geral":0,
                  "data_referencia":"2026-10-12"}}
                """);
        when(profiles.findPhotoUrl("manager")).thenReturn("https://images.example.test/manager.jpg");
        var result = service.loadHome(new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR));
        org.junit.jupiter.api.Assertions.assertEquals(4, result.statistics().unitCount());
        org.junit.jupiter.api.Assertions.assertEquals("https://images.example.test/manager.jpg", result.profilePhotoUrl());
        verify(profiles).findPhotoUrl("manager");
    }
}
