package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.dto.request.UpdateWorkspaceRequest;
import com.astro.api.workspace.dto.response.WorkspaceResponse;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.repository.WorkspaceRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceSettingsServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WorkspaceRepository repository = mock(WorkspaceRepository.class);
    private final WorkspaceAccessService access = new WorkspaceAccessService(users, repository);
    private final WorkspaceSettingsService service = new WorkspaceSettingsService(access, repository, new com.astro.api.workspace.mapper.WorkspaceMapper());
    private final AuthenticatedUser principal = new AuthenticatedUser("uid", "manager@astro.com", Role.GESTOR_WORKSPACE);

    @Test
    void resolvesWorkspaceFromStoredIdentityAndPreservesOwnCnpj() {
        Workspace workspace = configure();
        when(repository.existsByCnpjAndIdNot("12345678000199", 3L)).thenReturn(false);
        when(repository.saveAndFlush(workspace)).thenReturn(workspace);
        WorkspaceResponse result = service.update(new UpdateWorkspaceRequest(" Nova empresa ", "12.345.678/0001-99"), principal);
        assertEquals(3L, result.id());
        assertEquals("Nova empresa", result.name());
        assertEquals("12345678000199", result.cnpj());
        verify(repository).findWithLockById(3L);
        verify(repository).existsByCnpjAndIdNot("12345678000199", 3L);
    }

    @Test
    void refusesConflictingCnpjBeforeChangingEntity() {
        Workspace workspace = configure();
        when(repository.existsByCnpjAndIdNot(anyString(), eq(3L))).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.update(
                new UpdateWorkspaceRequest("Outra", "12345678000199"), principal));
        assertEquals("Empresa", workspace.name);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void checksRoleAndStoredActiveStatus() {
        assertThrows(AccessDeniedException.class, () -> service.find(null));
        assertThrows(AccessDeniedException.class, () -> service.find(
                new AuthenticatedUser("uid", null, Role.GESTOR)));
        configure();
        users.findByFirebaseUid("uid").orElseThrow().setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.find(principal));
        verify(repository, never()).findById(any());
    }

    private Workspace configure() {
        Workspace workspace = new Workspace();
        workspace.id = 3L; workspace.name = "Empresa"; workspace.cnpj = "12345678000199";
        Unit unit = new Unit(); unit.workspace = workspace;
        User manager = new User(); manager.setUnit(unit);
        manager.setType(UserType.GESTOR_WORKSPACE); manager.setStatus(UserStatus.ATIVO);
        when(users.findByFirebaseUid("uid")).thenReturn(Optional.of(manager));
        when(repository.findWithLockById(3L)).thenReturn(Optional.of(workspace));
        when(repository.findById(3L)).thenReturn(Optional.of(workspace));
        return workspace;
    }
}
