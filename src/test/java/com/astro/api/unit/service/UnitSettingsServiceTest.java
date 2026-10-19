package com.astro.api.unit.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.mapper.UnitResponseMapper;
import com.astro.api.unit.model.*;
import com.astro.api.unit.repository.*;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.service.WorkspaceAccessService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitSettingsServiceTest {
    private final WorkspaceAccessService access = mock(WorkspaceAccessService.class);
    private final UnitRepository units = mock(UnitRepository.class);
    private final UnitAddressRepository addresses = mock(UnitAddressRepository.class);
    private final UnitSettingsService service = new UnitSettingsService(access, units, addresses, new UnitMapper(), new UnitResponseMapper());
    private final AuthenticatedUser user = new AuthenticatedUser("uid", null, Role.GESTOR_WORKSPACE);
    private final Workspace workspace = new Workspace();

    @BeforeEach
    void setup() {
        workspace.id = 3L;
        when(access.requireWorkspace(user, true)).thenReturn(workspace);
        when(units.findByWorkspace(workspace)).thenReturn(List.of());
        when(units.saveAndFlush(any())).thenAnswer(call -> {
            Unit unit = call.getArgument(0);
            if (unit.id == null) unit.id = 12L;
            return unit;
        });
        when(addresses.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void persistsAndReturnsNumberAndNormalizedAddress() {
        UnitResponse response = service.create(body(" Matriz ", " Sala 1 "), user);
        assertEquals("Matriz", response.nome());
        assertEquals("10A", response.numero());
        assertEquals("01234567", response.cep());
        assertEquals("SP", response.estado());
        assertEquals("Sala 1", response.complemento());
        verify(addresses).save(argThat(address -> "10A".equals(address.number) && address.unit.id == 12L));
    }

    @Test
    void rejectsNormalizedDuplicateAndOutOfScopeId() {
        when(units.findByWorkspace(workspace)).thenReturn(List.of(unit("MÁTRIZ", true)));
        assertThrows(ConflictException.class, () -> service.create(body("matriz", null), user));
        verifyNoInteractions(addresses);
    }

    private Unit unit(String name, boolean active) {
        Unit unit = new Unit(); unit.id = 12L; unit.name = name;
        unit.workspace = workspace; unit.isActive = active;
        return unit;
    }

    private UnitRequest body(String name, String complement) {
        return new UnitRequest(name, "01234-567", "sp", "São Paulo", "Centro", "Rua A", "10A", complement);
    }
}
