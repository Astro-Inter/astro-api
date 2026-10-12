package com.astro.api.role.repository;

import com.astro.api.role.dto.response.RoleNrResponse;
import com.astro.api.role.dto.response.RoleResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleManagementRepositoryTest {
    private final RoleRepository jpa = mock(RoleRepository.class);
    private final RoleManagementRepository repository = new RoleManagementRepository(jpa);

    @Test
    void mapsJpaRoleProjectionsToTheResponse() {
        RoleProjection role = mock(RoleProjection.class);
        when(role.getRoleId()).thenReturn(12L);
        when(role.getWorkspaceId()).thenReturn(3L);
        when(role.getName()).thenReturn("Gerente");
        when(role.getCollaboratorCount()).thenReturn(25L);
        when(role.getStatus()).thenReturn("Ativo");
        when(jpa.findAllByFirebaseUid("gestor")).thenReturn(List.of(role));
        assertEquals(new RoleResponse(12L, 3L, "Gerente", 25, "Ativo"), repository.findAll("gestor").getFirst());
    }

    @Test
    void mapsJpaNrProjectionsToTheResponse() {
        RoleNrProjection nr = mock(RoleNrProjection.class);
        when(nr.getNrCode()).thenReturn(17);
        when(nr.getDescription()).thenReturn("Ergonomia");
        when(jpa.findNrs("gestor", 12L)).thenReturn(List.of(nr));
        assertEquals(List.of(new RoleNrResponse(17, "Ergonomia")), repository.findNrs("gestor", 12L));
    }

    @Test
    void translatesSqlstatesWrappedByJpa() {
        for (String state : List.of("22023", "42501", "P0002")) {
            reset(jpa);
            when(jpa.toggleStatus("gestor", 12L)).thenThrow(
                    new JpaSystemException(new RuntimeException(new SQLException("banco", state))));
            Class<? extends RuntimeException> expected = switch (state) {
                case "22023" -> BusinessException.class;
                case "42501" -> AccessDeniedException.class;
                default -> ResourceNotFoundException.class;
            };
            assertThrows(expected, () -> repository.toggleStatus("gestor", 12L));
        }
    }

    @Test
    void preservesConnectionFailuresForTheExisting503Handler() {
        var failure = new DataAccessResourceFailureException("offline");
        when(jpa.toggleStatus("gestor", 12L)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> repository.toggleStatus("gestor", 12L)));
    }

    @Test
    void passesTheSerializedNrChangesToTheJpaProcedureMethod() {
        String json = "[{\"nr\":17,\"ativo\":false}]";
        repository.updateNrs("gestor", 12L, json);
        verify(jpa).updateNrs("gestor", 12L, json);
    }

    @Test
    void preservesFalseWhenTheJpaFunctionDeactivatesTheRole() {
        when(jpa.toggleStatus("gestor", 12L)).thenReturn(false);
        assertFalse(repository.toggleStatus("gestor", 12L));
    }

    @Test
    void rejectsAnEmptyUpdateResult() {
        when(jpa.update("gestor", 12L, "Gerente", "Ativo")).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> repository.update("gestor", 12L, "Gerente", "Ativo"));
    }
}
