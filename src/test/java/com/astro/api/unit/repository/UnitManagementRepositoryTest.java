package com.astro.api.unit.repository;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.unit.dto.response.UnitNrResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitManagementRepositoryTest {
    private final UnitRepository jpa = mock(UnitRepository.class);
    private final UnitManagementRepository repository = new UnitManagementRepository(jpa);

    @Test
    void mapsTheViewIncludingAnAbsentAddressAndZeroCollaborators() {
        UnitProjection unit = mock(UnitProjection.class);
        when(unit.getUnitId()).thenReturn(12L);
        when(unit.getWorkspaceId()).thenReturn(3L);
        when(unit.getName()).thenReturn("Matriz");
        when(unit.getStatus()).thenReturn("Inativo");
        when(unit.getCollaboratorCount()).thenReturn(0L);
        when(jpa.findAllByFirebaseUid("manager")).thenReturn(List.of(unit));
        var response = repository.findAll("manager").getFirst();
        assertEquals(12L, response.unitId());
        assertEquals(3L, response.workspaceId());
        assertEquals("Matriz", response.name());
        assertEquals("Inativo", response.status());
        assertEquals(0L, response.collaboratorCount());
        assertNull(response.postalCode());
        assertNull(response.addressLine2());
    }

    @Test
    void mapsAssociatedCodesAndDescriptionsAndPreservesEmptyResults() {
        UnitNrProjection nr = mock(UnitNrProjection.class);
        when(nr.getNrCode()).thenReturn(17);
        when(nr.getDescription()).thenReturn("Ergonomia");
        when(jpa.findNrs("manager", 12L)).thenReturn(List.of(nr));
        assertEquals(List.of(new UnitNrResponse(17, "Ergonomia")), repository.findNrs("manager", 12L));
        when(jpa.findNrs("manager", 12L)).thenReturn(List.of());
        assertEquals(List.of(), repository.findNrs("manager", 12L));
    }

    @Test
    void translatesTheSqlstatesDefinedByScrum500() {
        for (String state : List.of("22023", "42501", "P0002")) {
            reset(jpa);
            var failure = new JpaSystemException(new RuntimeException(new SQLException("private database detail", state)));
            when(jpa.toggleStatus("manager", 12L)).thenThrow(failure);
            doThrow(failure).when(jpa).updateNrs("manager", 12L, "[]");
            Class<? extends RuntimeException> expected = switch (state) {
                case "22023" -> BusinessException.class;
                case "42501" -> AccessDeniedException.class;
                default -> ResourceNotFoundException.class;
            };
            assertThrows(expected, () -> repository.toggleStatus("manager", 12L));
            assertThrows(expected, () -> repository.updateNrs("manager", 12L, "[]"));
        }
    }

    @Test
    void preservesConnectionAndUnexpectedDatabaseFailures() {
        var offline = new DataAccessResourceFailureException("offline");
        when(jpa.findNrs("manager", 12L)).thenThrow(offline);
        assertSame(offline, assertThrows(DataAccessResourceFailureException.class,
                () -> repository.findNrs("manager", 12L)));
        var unexpected = new JpaSystemException(new RuntimeException(new SQLException("unexpected", "XX000")));
        when(jpa.toggleStatus("manager", 12L)).thenThrow(unexpected);
        assertSame(unexpected, assertThrows(JpaSystemException.class, () -> repository.toggleStatus("manager", 12L)));
    }

    @Test
    void preservesFalseAndRejectsUnexpectedNullStatus() {
        when(jpa.toggleStatus("manager", 12L)).thenReturn(false);
        assertFalse(repository.toggleStatus("manager", 12L));
        when(jpa.toggleStatus("manager", 12L)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> repository.toggleStatus("manager", 12L));
    }

    @Test
    void forwardsTheNrJsonUnchangedToTheJpaProcedure() {
        String json = "[{\"nr\":17,\"ativo\":false}]";
        repository.updateNrs("manager", 12L, json);
        verify(jpa).updateNrs("manager", 12L, json);
    }
}
