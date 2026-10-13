package com.astro.api.collaborator.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.collaborator.mapper.CollaboratorMapper;
import com.astro.api.collaborator.repository.CollaboratorRepository;
import com.astro.api.collaborator.repository.CollaboratorSummaryProjection;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollaboratorServiceTest {
    final CollaboratorRepository repository = mock(CollaboratorRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final CollaboratorService service = new CollaboratorService(repository, users, new CollaboratorMapper());
    final AuthenticatedUser principal = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR);
    User manager;
    Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.id = 3L;
        Unit unit = new Unit();
        unit.id = 12L;
        unit.workspace = workspace;
        manager = new User();
        manager.setId(1L);
        manager.setUnit(unit);
        manager.setType(UserType.GESTOR);
        manager.setStatus(UserStatus.ATIVO);
        when(users.findByFirebaseUid("manager")).thenReturn(Optional.of(manager));
    }

    @Test
    void listsAllStatusesUsingOnlyTheAuthenticatedManagersScope() {
        var rows = List.of(row(2L, "PRE_CADASTRADO"), row(3L, "ATIVO"), row(4L, "DESATIVADO"));
        when(repository.findAllInScope("manager", 3L)).thenReturn(rows);
        var result = service.findAll(principal);
        assertEquals(List.of(UserStatus.PRE_CADASTRADO, UserStatus.ATIVO, UserStatus.DESATIVADO),
                result.stream().map(response -> response.status()).toList());
        assertEquals(3, result.stream().map(response -> response.id()).distinct().count());
        assertEquals("Operador", result.getFirst().roleName());
        verify(repository).findAllInScope("manager", 3L);
    }

    @Test
    void returnsAnEmptyListWhenNoCollaboratorsAreAuthorized() {
        when(repository.findAllInScope("manager", 3L)).thenReturn(List.of());
        assertTrue(service.findAll(principal).isEmpty());
    }

    @Test
    void rejectsMissingUnknownInactiveOrMismatchedManagersBeforeQuerying() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(null));
        assertThrows(AccessDeniedException.class, () -> service.findAll(new AuthenticatedUser("unknown", "", Role.GESTOR)));
        manager.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        manager.setStatus(UserStatus.ATIVO);
        manager.setType(UserType.COLABORADOR);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        manager.setType(UserType.GESTOR_WORKSPACE);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        verifyNoInteractions(repository);
    }

    private CollaboratorSummaryProjection row(Long id, String status) {
        var row = mock(CollaboratorSummaryProjection.class);
        when(row.getId()).thenReturn(id);
        when(row.getName()).thenReturn("Colaborador " + id);
        when(row.getEmail()).thenReturn("test" + id + "@example.com");
        when(row.getRoleId()).thenReturn(5L);
        when(row.getRoleName()).thenReturn("Operador");
        when(row.getUnitId()).thenReturn(12L);
        when(row.getUnitName()).thenReturn("Matriz");
        when(row.getStatus()).thenReturn(status);
        return row;
    }
}
