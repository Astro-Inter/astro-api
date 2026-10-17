package com.astro.api.user.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.dto.request.UpdateAccountDetailsRequest;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.mapper.AccountMapper;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.AccountRepository;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountUpdateTest {
    final UserRepository users = mock(UserRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final UnitRepository units = mock(UnitRepository.class);
    final UserAvatarService avatars = mock(UserAvatarService.class);
    final FirebaseIdentityService identities = mock(FirebaseIdentityService.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final AccountService service = new AccountService(users, avatars, new AccountMapper(), accounts, roles, units, identities, transactions);
    User user;
    com.astro.api.role.model.Role managerRole;

    @BeforeEach void setUp() {
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        user = AccountServiceTest.account(Role.GESTOR_WORKSPACE);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setCpf("11144477735");
        managerRole = new com.astro.api.role.model.Role();
        managerRole.setId(20L); managerRole.setName("Engenheira"); managerRole.setWorkspace(user.getUnit().workspace);
        user.setRole(managerRole);
        when(accounts.findForUpdateByFirebaseUid("own-uid")).thenReturn(Optional.of(user));
        when(accounts.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(roles.findById(20L)).thenReturn(Optional.of(managerRole));
        when(units.findById(10L)).thenReturn(Optional.of(user.getUnit()));
    }

    AuthenticatedUser principal() { return new AuthenticatedUser("own-uid", "stale-token@example.com", Role.valueOf(user.getType().name())); }
    UpdateAccountDetailsRequest body(String email, Long roleId, Long unitId) {
        return new UpdateAccountDetailsRequest(" Maria Rosa editada ", "529.982.247-25", email, roleId, unitId, WorkModel.REMOTO);
    }
    UpdateAccountDetailsRequest body() { return body(" STORED@EXAMPLE.COM ", 20L, 10L); }

    @Test void fullUpdatePreservesIdentityScopeAndUnchangedInactiveLinksForBothProfiles() {
        for (UserType type : new UserType[]{UserType.GESTOR, UserType.GESTOR_WORKSPACE}) {
            user.setType(type);
            Unit oldUnit = user.getUnit(); Instant createdAt = user.getCreatedAt();
            AccountDetailsResponse result = service.update(body(), principal());
            assertEquals("Maria Rosa editada", result.name()); assertEquals("52998224725", result.cpf());
            assertEquals("stored@example.com", result.email()); assertEquals(WorkModel.REMOTO, result.workModel());
            assertEquals(20L, result.roleId()); assertEquals(10L, result.unitId());
            assertEquals(type, user.getType()); assertEquals(UserStatus.ATIVO, user.getStatus());
            assertEquals("own-uid", user.getFirebaseUid()); assertEquals(100L, user.getId());
            assertEquals(createdAt, user.getCreatedAt()); assertSame(oldUnit.workspace, user.getUnit().workspace);
        }
        verifyNoInteractions(identities, users);
        verify(accounts, never()).lockRoleActive(any(), any());
        verify(accounts, never()).lockUnitActive(any(), any());
        verify(accounts, times(2)).findForUpdateByFirebaseUid("own-uid");
    }

    @Test void managerCannotChangeOwnRoleOrUnitEvenIfDropdownContainsIt() {
        user.setType(UserType.GESTOR);
        assertThrows(AccessDeniedException.class, () -> service.update(body(user.getEmail(), 21L, 10L), principal()));
        assertThrows(AccessDeniedException.class, () -> service.update(body(user.getEmail(), 20L, 11L), principal()));
        verify(accounts, never()).saveAndFlush(any()); verifyNoInteractions(identities, avatars);
    }

    Unit newUnit(Long workspaceId) {
        Workspace workspace = new Workspace(); workspace.id = workspaceId;
        Unit unit = new Unit(); unit.id = 11L; unit.name = "São Paulo"; unit.workspace = workspace; unit.isActive = true;
        when(units.findById(11L)).thenReturn(Optional.of(unit));
        return unit;
    }

    com.astro.api.role.model.Role newRole(Long workspaceId) {
        com.astro.api.role.model.Role role = new com.astro.api.role.model.Role();
        Workspace workspace = new Workspace(); workspace.id = workspaceId;
        role.setId(21L); role.setName("Supervisora"); role.setWorkspace(workspace); role.setActive(true);
        when(roles.findById(21L)).thenReturn(Optional.of(role)); return role;
    }

    @Test void workspaceManagerMayChooseOnlyActiveLinksInTheSameWorkspace() {
        newRole(1L); newUnit(1L);
        when(accounts.lockRoleActive(21L, 1L)).thenReturn(true);
        when(accounts.lockUnitActive(11L, 1L)).thenReturn(true);
        AccountDetailsResponse result = service.update(body(user.getEmail(), 21L, 11L), principal());
        assertEquals(21L, result.roleId()); assertEquals("Supervisora", result.roleName());
        assertEquals(11L, result.unitId()); assertEquals("São Paulo", result.unitName());
        assertEquals(1L, user.getUnit().workspace.id); assertEquals(UserType.GESTOR_WORKSPACE, user.getType());
    }

    @Test void rejectsMissingAndForeignLinksWithoutWritingOrCallingFirebase() {
        assertThrows(ResourceNotFoundException.class, () -> service.update(body(user.getEmail(), 21L, 10L), principal()));
        assertThrows(ResourceNotFoundException.class, () -> service.update(body(user.getEmail(), 20L, 11L), principal()));
        newRole(2L); newUnit(2L);
        assertThrows(ResourceNotFoundException.class, () -> service.update(body(user.getEmail(), 21L, 10L), principal()));
        assertThrows(ResourceNotFoundException.class, () -> service.update(body(user.getEmail(), 20L, 11L), principal()));
        verify(accounts, never()).saveAndFlush(any()); verifyNoInteractions(identities, avatars);
    }

    @Test void rejectsInactiveNewLinksUsingFreshLockedStatusReads() {
        newRole(1L); newUnit(1L);
        when(accounts.lockRoleActive(21L, 1L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.update(body(user.getEmail(), 21L, 10L), principal()));
        when(accounts.lockUnitActive(11L, 1L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.update(body(user.getEmail(), 20L, 11L), principal()));
        verify(accounts, never()).saveAndFlush(any()); verifyNoInteractions(identities);
    }

    @Test void rejectsCpfAndEmailConflictsBeforeFirebase() {
        when(accounts.cpfExists("52998224725", 100L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.update(body(), principal()));
        when(accounts.cpfExists("52998224725", 100L)).thenReturn(false);
        when(accounts.emailExists("stored@example.com", 100L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.update(body(), principal()));
        verify(accounts, never()).saveAndFlush(any()); verifyNoInteractions(identities);
    }

    FirebaseIdentityService.EmailState emailState() {
        FirebaseIdentityService.EmailState state = new FirebaseIdentityService.EmailState("stored@example.com", true);
        when(identities.readEmailState("own-uid")).thenReturn(state); return state;
    }

    @Test void emailChangesReuseTheExistingIdentityAndOnlySucceedAfterCommit() {
        emailState();
        AccountDetailsResponse result = service.update(body("new@example.com", 20L, 10L), principal());
        assertEquals("new@example.com", result.email());
        InOrder order = inOrder(accounts, identities, transactions);
        order.verify(accounts).saveAndFlush(user);
        order.verify(identities).readEmailState("own-uid");
        order.verify(identities).updateEmail("own-uid", "new@example.com");
        order.verify(transactions).commit(any());
        verify(identities, never()).restoreEmail(any(), any());
        verify(identities, never()).createUser(any(), any(), any());
    }

    @Test void databaseFlushFailureNeverChangesFirebase() {
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique email"));
        assertThrows(DataIntegrityViolationException.class, () -> service.update(body("new@example.com", 20L, 10L), principal()));
        verifyNoInteractions(identities); verify(transactions).rollback(any());
    }

    @Test void databaseCommitFailureRestoresPreviousFirebaseEmailAndVerifiedState() {
        FirebaseIdentityService.EmailState previous = emailState();
        doThrow(new IllegalStateException("commit failure")).when(transactions).commit(any());
        assertThrows(IllegalStateException.class, () -> service.update(body("new@example.com", 20L, 10L), principal()));
        verify(identities).restoreEmail("own-uid", previous);
    }

    @Test void firebaseTimeoutRollsBackAndCompensatesAPotentialRemoteWrite() {
        FirebaseIdentityService.EmailState previous = emailState();
        doThrow(new IllegalStateException("timeout")).when(identities).updateEmail(any(), any());
        assertThrows(IllegalStateException.class, () -> service.update(body("new@example.com", 20L, 10L), principal()));
        verify(transactions).rollback(any()); verify(identities).restoreEmail("own-uid", previous);
    }

    @Test void explicitFirebaseDuplicateIsAConflictWithNoRemoteCompensation() {
        emailState();
        doThrow(new ConflictException("Já existe uma conta Firebase para este e-mail")).when(identities).updateEmail(any(), any());
        assertThrows(ConflictException.class, () -> service.update(body("new@example.com", 20L, 10L), principal()));
        verify(transactions).rollback(any()); verify(identities, never()).restoreEmail(any(), any());
    }

    @Test void identityReadFailureAndDivergenceNeverUpdateFirebase() {
        when(identities.readEmailState("own-uid")).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> service.update(body("new@example.com", 20L, 10L), principal()));
        user.setEmail("stored@example.com");
        doReturn(new FirebaseIdentityService.EmailState("foreign@example.com", false)).when(identities).readEmailState("own-uid");
        assertThrows(ConflictException.class, () -> service.update(body("new@example.com", 20L, 10L), principal()));
        verify(identities, never()).updateEmail(any(), any()); verify(identities, never()).restoreEmail(any(), any());
    }

    @Test void compensationFailureContainsBothManagerProfilesAndReturnsAnError() {
        for (UserType type : new UserType[]{UserType.GESTOR, UserType.GESTOR_WORKSPACE}) {
            user.setType(type); user.setEmail("stored@example.com"); emailState();
            doThrow(new IllegalStateException("timeout")).when(identities).updateEmail(any(), any());
            doThrow(new IllegalStateException("restore failed")).when(identities).restoreEmail(any(), any());
            when(accounts.deactivateAfterEmailFailure(100L, "own-uid")).thenReturn(1);
            assertThrows(IllegalStateException.class, () -> service.update(body("new@example.com", 20L, 10L), principal()));
        }
        verify(accounts, times(2)).deactivateAfterEmailFailure(100L, "own-uid");
    }

    @Test void staleProfileInactiveAccountOrMissingIdentityCannotBeUpdated() {
        assertThrows(AccessDeniedException.class, () -> service.update(body(), null));
        assertThrows(AccessDeniedException.class, () -> service.update(body(), new AuthenticatedUser("own-uid", null, Role.COLABORADOR)));
        assertThrows(AccessDeniedException.class, () -> service.update(body(), new AuthenticatedUser("own-uid", null, Role.GESTOR)));
        user.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.update(body(), principal()));
        when(accounts.findForUpdateByFirebaseUid("own-uid")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(body(), principal()));
        verify(accounts, never()).saveAndFlush(any()); verifyNoInteractions(identities, avatars);
    }
}
