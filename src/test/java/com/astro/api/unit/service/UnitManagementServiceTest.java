package com.astro.api.unit.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformity.model.NrDocument;
import com.astro.api.unit.dto.request.UnitNrRequest;
import com.astro.api.unit.dto.request.UpdateUnitNrsRequest;
import com.astro.api.unit.dto.response.UnitNrOptionResponse;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.mapper.UnitResponseMapper;
import com.astro.api.unit.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitManagementServiceTest {
    private final UnitRepository repository = mock(UnitRepository.class);
    private final UnitNrCatalogRepository catalog = mock(UnitNrCatalogRepository.class);
    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final UnitService service = new UnitService(repository, mock(UnitAddressRepository.class),
            mock(UnitMapper.class), new UnitResponseMapper(), catalog, objectMapper);
    private final AuthenticatedUser user = new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR);

    @Test
    void passesOnlyTheAuthenticatedUidForEveryOperation() {
        when(repository.findAllByFirebaseUid("manager")).thenReturn(List.of());
        when(repository.toggleStatus("manager", 12L)).thenReturn(true);
        when(repository.findNrs("manager", 12L)).thenReturn(List.of());
        service.findAll(user);
        service.toggleStatus(12L, user);
        service.findNrs(12L, user);
        service.updateNrs(12L, new UpdateUnitNrsRequest(List.of()), user);
        verify(repository).findAllByFirebaseUid("manager");
        verify(repository).toggleStatus("manager", 12L);
        verify(repository).findNrs("manager", 12L);
        verify(repository).updateNrs("manager", 12L, "[]");
    }

    @Test
    void combinesTheMongoCatalogWithPostgresAssociations() {
        UnitNrProjection associatedNr = mock(UnitNrProjection.class);
        when(associatedNr.getNrCode()).thenReturn(17);
        when(associatedNr.getDescription()).thenReturn("Título PostgreSQL");
        when(repository.findNrs("manager", 12L)).thenReturn(List.of(associatedNr));
        when(catalog.findCompanyNrs()).thenReturn(List.of(nr(17, "Ergonomia"), nr(1, "Disposições gerais")));
        assertEquals(List.of(new UnitNrOptionResponse(1, "Disposições gerais", false),
                new UnitNrOptionResponse(17, "Ergonomia", true)), service.findNrOptions(12L, user));
    }

    @Test
    void serializesPartialChangesWithTheRequiredPortugueseBooleanKey() {
        var body = new UpdateUnitNrsRequest(List.of(new UnitNrRequest(1, true), new UnitNrRequest(17, false)));
        service.updateNrs(12L, body, user);
        verify(repository).updateNrs("manager", 12L, "[{\"nr\":1,\"ativo\":true},{\"nr\":17,\"ativo\":false}]");
    }

    @Test
    void rejectsDuplicateCodesBeforeUpdatingAnyAssociation() {
        var body = new UpdateUnitNrsRequest(List.of(new UnitNrRequest(17, true), new UnitNrRequest(17, false)));
        assertThrows(BusinessException.class, () -> service.updateNrs(12L, body, user));
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void rejectsMissingAuthenticatedIdentityForEveryOperation() {
        for (AuthenticatedUser identity : Arrays.asList(null, new AuthenticatedUser(null, null, Role.GESTOR),
                new AuthenticatedUser(" ", null, Role.GESTOR))) {
            assertThrows(AccessDeniedException.class, () -> service.findAll(identity));
            assertThrows(AccessDeniedException.class, () -> service.toggleStatus(12L, identity));
            assertThrows(AccessDeniedException.class, () -> service.findNrs(12L, identity));
            assertThrows(AccessDeniedException.class, () -> service.findNrOptions(12L, identity));
            assertThrows(AccessDeniedException.class, () -> service.updateNrs(12L, new UpdateUnitNrsRequest(List.of()), identity));
        }
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void rejectsNonPositiveIdsBeforeCallingTheDatabases() {
        for (Long unitId : Arrays.asList(null, 0L, -1L)) {
            assertThrows(BusinessException.class, () -> service.toggleStatus(unitId, user));
            assertThrows(BusinessException.class, () -> service.findNrs(unitId, user));
            assertThrows(BusinessException.class, () -> service.findNrOptions(unitId, user));
            assertThrows(BusinessException.class, () -> service.updateNrs(unitId, new UpdateUnitNrsRequest(List.of()), user));
        }
        verifyNoInteractions(repository, catalog);
    }

    private NrDocument nr(int code, String description) {
        NrDocument document = new NrDocument();
        document.setId(code);
        document.setDescription(description);
        return document;
    }
}
