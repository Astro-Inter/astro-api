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
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
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
    private final UnitService unitService = mock(UnitService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UnitSettingsService service = new UnitSettingsService(access, units, addresses,
            new UnitMapper(), new UnitResponseMapper(), unitService, users);
    private final AuthenticatedUser user = new AuthenticatedUser("uid", null, Role.GESTOR_WORKSPACE);
    private final Workspace workspace = new Workspace();

    @BeforeEach
    void setup() {
        workspace.id = 3L;
        when(access.requireWorkspace(user, true)).thenReturn(workspace);
        when(units.findByWorkspace(workspace)).thenReturn(List.of());
        when(units.save(any())).thenAnswer(call -> {
            Unit unit = call.getArgument(0);
            if (unit.id == null) unit.id = 12L;
            return unit;
        });
        when(addresses.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void persistsAndReturnsNumberAndNormalizedAddress() {
        UnitResponse response = service.create(body(" Matriz ", " Sala 1 "), user);
        assertEquals("Matriz", response.name());
        assertEquals("10A", response.number());
        assertEquals("01234567", response.postalCode());
        assertEquals("SP", response.state());
        assertEquals("Sala 1", response.addressLine2());
        assertEquals(0L, response.collaboratorCount());
        verify(users).countByUnit_IdAndType(12L, UserType.COLABORADOR);
        verify(addresses).save(argThat(address -> "10A".equals(address.number) && address.unit.id == 12L));
    }

    @Test
    void createsNewImportUnitsTogetherThroughTheRegistrationService() {
        UnitRequest firstRequest = body("Filial Norte", null);
        UnitRequest secondRequest = body("Filial Sul", null);
        List<UnitRequest> requests = List.of(firstRequest, secondRequest);
        Map<String, Unit> resolvedUnits = service.resolveForImport(workspace, requests);
        Unit firstSavedUnit = new UnitMapper().toUnit(firstRequest, workspace);
        firstSavedUnit.id = 20L;
        Unit secondSavedUnit = new UnitMapper().toUnit(secondRequest, workspace);
        secondSavedUnit.id = 21L;
        when(unitService.createForWorkspace(workspace, requests))
                .thenReturn(List.of(firstSavedUnit, secondSavedUnit));

        Map<Unit, Unit> persistedUnits = service.persistNewForImport(workspace, requests, resolvedUnits,
                List.of(resolvedUnits.get("filial norte")));

        assertEquals(2, persistedUnits.size());
        assertSame(firstSavedUnit, persistedUnits.get(resolvedUnits.get("filial norte")));
        assertSame(secondSavedUnit, persistedUnits.get(resolvedUnits.get("filial sul")));
        verify(unitService).createForWorkspace(workspace, requests);
        verify(units, never()).save(any());
        verifyNoInteractions(addresses);
    }

    @Test
    void updatesAddressWithoutChangingStatusOrIdentityAndClearsComplement() {
        Unit unit = unit("Matriz", false);
        unit.address = new UnitMapper().toAddress(body("Matriz", "Antigo"), unit);
        when(units.findByWorkspace(workspace)).thenReturn(List.of(unit));
        when(units.findByIdAndWorkspace_Id(12L, 3L)).thenReturn(Optional.of(unit));
        when(users.countByUnit_IdAndType(12L, UserType.COLABORADOR)).thenReturn(7L);
        UnitResponse response = service.update(12L, body("Matriz", " "), user);
        assertEquals(12L, response.unitId());
        assertEquals("Inativo", response.status());
        assertNull(response.addressLine2());
        assertEquals(7L, response.collaboratorCount());
        verify(users).countByUnit_IdAndType(12L, UserType.COLABORADOR);
        assertSame(workspace, unit.workspace);
        assertSame(unit, unit.address.unit);
    }

    @Test
    void rejectsNormalizedDuplicateAndOutOfScopeId() {
        when(units.findByWorkspace(workspace)).thenReturn(List.of(unit("MÁTRIZ", true)));
        assertThrows(ConflictException.class, () -> service.create(body("matriz", null), user));
        assertThrows(ResourceNotFoundException.class, () -> service.update(99L, body("Filial", null), user));
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
