package com.astro.api.unit.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.unit.dto.request.UnitNrRequest;
import com.astro.api.unit.dto.request.UpdateUnitNrsRequest;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.mapper.UnitResponseMapper;
import com.astro.api.unit.repository.UnitAddressRepository;
import com.astro.api.unit.repository.UnitNrCatalogRepository;
import com.astro.api.unit.repository.UnitNrProjection;
import com.astro.api.unit.repository.UnitProjection;
import com.astro.api.unit.repository.UnitRepository;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitDatabaseOperationsTest {
    private final UnitRepository jpa = mock(UnitRepository.class);
    private final UnitService service = new UnitService(jpa, mock(UnitAddressRepository.class),
            mock(UnitMapper.class), new UnitResponseMapper(), mock(UnitNrCatalogRepository.class),
            JsonMapper.builder().build());
    private final AuthenticatedUser user = new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR);

    @Test
    void mapsTheViewIncludingAnAbsentAddressAndZeroCollaborators() {
        UnitProjection unit = mock(UnitProjection.class);
        when(unit.getUnitId()).thenReturn(12L);
        when(unit.getWorkspaceId()).thenReturn(3L);
        when(unit.getName()).thenReturn("Matriz");
        when(unit.getStatus()).thenReturn("Inativo");
        when(unit.getCollaboratorCount()).thenReturn(0L);
        when(jpa.findAllByFirebaseUid("manager")).thenReturn(List.of(unit));
        UnitResponse response = service.findAll(user).getFirst();
        assertEquals(12L, response.id_unidade());
        assertEquals(3L, response.id_workspace());
        assertEquals("Matriz", response.nome());
        assertEquals("Inativo", response.status());
        assertEquals(0L, response.quantidade_colaboradores());
        assertNull(response.cep());
        assertNull(response.complemento());
    }

    @Test
    void mapsAssociatedCodesAndDescriptionsAndPreservesEmptyResults() {
        UnitNrProjection nr = mock(UnitNrProjection.class);
        when(nr.getNrCode()).thenReturn(17);
        when(nr.getDescription()).thenReturn("Ergonomia");
        when(jpa.findNrs("manager", 12L)).thenReturn(List.of(nr));
        assertEquals(List.of(new UnitNrResponse(17, "Ergonomia")), service.findNrs(12L, user));
        when(jpa.findNrs("manager", 12L)).thenReturn(List.of());
        assertEquals(List.of(), service.findNrs(12L, user));
    }

    @Test
    void translatesTheSqlstatesDefinedByScrum500() {
        for (String state : List.of("22023", "42501", "P0002")) {
            reset(jpa);
            JpaSystemException failure = new JpaSystemException(new RuntimeException(new SQLException("private database detail", state)));
            when(jpa.toggleStatus("manager", 12L)).thenThrow(failure);
            doThrow(failure).when(jpa).updateNrs("manager", 12L, "[]");
            Class<? extends RuntimeException> expected = switch (state) {
                case "22023" -> BusinessException.class;
                case "42501" -> AccessDeniedException.class;
                default -> ResourceNotFoundException.class;
            };
            assertThrows(expected, () -> service.toggleStatus(12L, user));
            assertThrows(expected, () -> service.updateNrs(12L, new UpdateUnitNrsRequest(List.of()), user));
        }
    }

    @Test
    void preservesConnectionAndUnexpectedDatabaseFailures() {
        DataAccessResourceFailureException offline = new DataAccessResourceFailureException("offline");
        when(jpa.findNrs("manager", 12L)).thenThrow(offline);
        assertSame(offline, assertThrows(DataAccessResourceFailureException.class,
                () -> service.findNrs(12L, user)));
        JpaSystemException unexpected = new JpaSystemException(new RuntimeException(new SQLException("unexpected", "XX000")));
        when(jpa.toggleStatus("manager", 12L)).thenThrow(unexpected);
        assertSame(unexpected, assertThrows(JpaSystemException.class, () -> service.toggleStatus(12L, user)));
    }

    @Test
    void preservesFalseAndRejectsUnexpectedNullStatus() {
        when(jpa.toggleStatus("manager", 12L)).thenReturn(false);
        assertFalse(service.toggleStatus(12L, user));
        when(jpa.toggleStatus("manager", 12L)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.toggleStatus(12L, user));
    }

    @Test
    void forwardsTheNrJsonUnchangedToTheJpaProcedure() {
        String json = "[{\"nr\":17,\"ativo\":false}]";
        service.updateNrs(12L, new UpdateUnitNrsRequest(List.of(new UnitNrRequest(17, false))), user);
        verify(jpa).updateNrs("manager", 12L, json);
    }
}
