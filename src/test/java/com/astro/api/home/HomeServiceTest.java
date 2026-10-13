package com.astro.api.home;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.home.repository.HomeRepository;
import com.astro.api.home.service.HomeService;
import com.astro.api.common.viewed.ViewedService;
import com.astro.api.form.service.FormService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class HomeServiceTest {
    private final HomeRepository repository = mock(HomeRepository.class);
    private final HomeService service = new HomeService(repository, JsonMapper.builder().build(),
            mock(FormService.class), mock(ViewedService.class));

    @Test
    void rejectsMissingIdentityBeforeQueryingTheDatabase() {
        assertThrows(AccessDeniedException.class, () -> service.loadHome(null));
        for (String uid : new String[]{null, "", " "}) {
            assertThrows(AccessDeniedException.class,
                    () -> service.loadHome(new AuthenticatedUser(uid, "manager@astro.com", Role.GESTOR)));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMalformedDatabasePayloadsAsServerErrors() {
        for (String json : List.of("broken", "null", "{}", "{\"nome_usuario\":\"Gestor\"}")) {
            when(repository.loadHome("manager")).thenReturn(json);
            assertThrows(IllegalStateException.class,
                    () -> service.loadHome(new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR)));
        }
    }
}
