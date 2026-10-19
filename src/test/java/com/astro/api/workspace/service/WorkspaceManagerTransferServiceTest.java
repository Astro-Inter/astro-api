package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.dto.request.WorkspaceManagerTransferRequest;
import com.astro.api.workspace.dto.response.WorkspaceManagerTransferResponse;
import com.astro.api.workspace.mapper.WorkspaceMapper;
import com.astro.api.workspace.model.Workspace;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceManagerTransferServiceTest {
    private final WorkspaceAccessService accessService = mock(WorkspaceAccessService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ManagerRepository managers = mock(ManagerRepository.class);
    private final WorkspaceManagerTransferService service = new WorkspaceManagerTransferService(
            accessService, users, managers, new WorkspaceMapper());
    private final AuthenticatedUser principal = new AuthenticatedUser("owner", null, Role.GESTOR_WORKSPACE);
    private final Workspace workspace = new Workspace();
    private User previousManager;
    private User newManager;

    @BeforeEach
    void setup() {
        workspace.id = 3L;
        previousManager = manager(8L, "owner", UserType.GESTOR_WORKSPACE);
        newManager = manager(9L, "successor", UserType.GESTOR);
        when(accessService.requireWorkspace(principal, true)).thenReturn(workspace);
        when(users.findByFirebaseUid("owner")).thenReturn(Optional.of(previousManager));
        when(managers.findUserForUpdateInWorkspace(9L, 3L)).thenReturn(Optional.of(newManager));
        when(users.countByUnit_WorkspaceAndType(workspace, UserType.GESTOR_WORKSPACE)).thenReturn(1L);
        when(users.updateTypeIfCurrent(8L, UserType.GESTOR_WORKSPACE, UserType.GESTOR, UserStatus.ATIVO)).thenReturn(1);
        when(users.updateTypeIfCurrent(9L, UserType.GESTOR, UserType.GESTOR_WORKSPACE, UserStatus.ATIVO)).thenReturn(1);
    }

    @Test
    void demotesTheCurrentOwnerBeforePromotingTheChosenManagerWithoutChangingTheirAccounts() {
        Unit previousUnit = previousManager.getUnit();
        Unit newUnit = newManager.getUnit();
        WorkspaceManagerTransferResponse response = transfer();

        assertEquals(new WorkspaceManagerTransferResponse(3L, 8L, 9L), response);
        InOrder changes = inOrder(users);
        changes.verify(users).updateTypeIfCurrent(8L, UserType.GESTOR_WORKSPACE, UserType.GESTOR, UserStatus.ATIVO);
        changes.verify(users).updateTypeIfCurrent(9L, UserType.GESTOR, UserType.GESTOR_WORKSPACE, UserStatus.ATIVO);
        assertEquals("owner", previousManager.getFirebaseUid());
        assertEquals("successor", newManager.getFirebaseUid());
        assertEquals(UserStatus.ATIVO, previousManager.getStatus());
        assertEquals(UserStatus.ATIVO, newManager.getStatus());
        assertEquals(Instant.EPOCH, previousManager.getCreatedAt());
        assertEquals(Instant.EPOCH, newManager.getCreatedAt());
        assertSame(previousUnit, previousManager.getUnit());
        assertSame(newUnit, newManager.getUnit());
        verify(users, never()).save(any());
    }

    @Test
    void rejectsUsersOutsideTheWorkspaceAndTheCurrentOwnerAsSuccessor() {
        when(managers.findUserForUpdateInWorkspace(9L, 3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, this::transfer);
        assertThrows(BusinessException.class,
                () -> service.transfer(new WorkspaceManagerTransferRequest(8L), principal));
        verifyNoTypeChanges();
    }

    @Test
    void rejectsCollaboratorsAndInactiveOrUnregisteredManagers() {
        newManager.setType(UserType.COLABORADOR);
        assertThrows(ConflictException.class, this::transfer);
        newManager.setType(UserType.GESTOR);
        newManager.setStatus(UserStatus.DESATIVADO);
        assertThrows(BusinessException.class, this::transfer);
        newManager.setStatus(UserStatus.PRE_CADASTRADO);
        assertThrows(BusinessException.class, this::transfer);
        newManager.setStatus(UserStatus.ATIVO);
        newManager.setFirebaseUid(" ");
        assertThrows(BusinessException.class, this::transfer);
        verifyNoTypeChanges();
    }

    @Test
    void rejectsInvalidIdentifiersAndUnauthorizedCallers() {
        assertThrows(BusinessException.class, () -> service.transfer(null, principal));
        assertThrows(BusinessException.class, () -> service.transfer(new WorkspaceManagerTransferRequest(null), principal));
        assertThrows(BusinessException.class, () -> service.transfer(new WorkspaceManagerTransferRequest(0L), principal));
        when(accessService.requireWorkspace(null, true)).thenThrow(new AccessDeniedException("Sem acesso"));
        assertThrows(AccessDeniedException.class, () -> service.transfer(new WorkspaceManagerTransferRequest(9L), null));
        verifyNoTypeChanges();
    }

    @Test
    void rejectsAmbiguousWorkspaceOwnershipWithoutChangingTypes() {
        when(users.countByUnit_WorkspaceAndType(workspace, UserType.GESTOR_WORKSPACE)).thenReturn(2L);
        assertThrows(ConflictException.class, this::transfer);
        verifyNoTypeChanges();
    }

    @Test
    void rejectsAuthorizationReadBeforeAnotherTransferCompleted() {
        when(users.updateTypeIfCurrent(8L, UserType.GESTOR_WORKSPACE, UserType.GESTOR, UserStatus.ATIVO)).thenReturn(0);
        assertThrows(AccessDeniedException.class, this::transfer);
        verify(users, never()).updateTypeIfCurrent(9L, UserType.GESTOR, UserType.GESTOR_WORKSPACE, UserStatus.ATIVO);
    }

    @Test
    void propagatesAConflictWhenTheNewManagerCannotBePromoted() {
        when(users.updateTypeIfCurrent(9L, UserType.GESTOR, UserType.GESTOR_WORKSPACE, UserStatus.ATIVO)).thenReturn(0);
        assertThrows(ConflictException.class, this::transfer);
    }

    private WorkspaceManagerTransferResponse transfer() {
        return service.transfer(new WorkspaceManagerTransferRequest(9L), principal);
    }

    private void verifyNoTypeChanges() {
        verify(users, never()).updateTypeIfCurrent(anyLong(), any(), any(), any());
    }

    private User manager(Long id, String firebaseUid, UserType type) {
        Unit unit = new Unit(); unit.id = id; unit.workspace = workspace;
        User user = new User();
        user.setId(id);
        user.setType(type);
        user.setStatus(UserStatus.ATIVO);
        user.setFirebaseUid(firebaseUid);
        user.setCreatedAt(Instant.EPOCH);
        user.setUnit(unit);
        return user;
    }
}
