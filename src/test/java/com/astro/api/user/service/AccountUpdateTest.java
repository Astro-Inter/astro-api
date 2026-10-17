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
    final UserRepository userRepository = mock(UserRepository.class);
    final AccountRepository accountRepository = mock(AccountRepository.class);
    final RoleRepository roleRepository = mock(RoleRepository.class);
    final UnitRepository unitRepository = mock(UnitRepository.class);
    final UserAvatarService userAvatarService = mock(UserAvatarService.class);
    final FirebaseIdentityService firebaseIdentityService = mock(FirebaseIdentityService.class);
    final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    final AccountService accountService = new AccountService(userRepository, userAvatarService, new AccountMapper(), accountRepository, roleRepository, unitRepository, firebaseIdentityService, transactionManager);
    User user;
    com.astro.api.role.model.Role managerRole;

    @BeforeEach void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        user = AccountServiceTest.account(Role.GESTOR_WORKSPACE);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setCpf("11144477735");
        managerRole = new com.astro.api.role.model.Role();
        managerRole.setId(20L); managerRole.setName("Engenheira"); managerRole.setWorkspace(user.getUnit().workspace);
        user.setRole(managerRole);
        when(accountRepository.findForUpdateByFirebaseUid("own-uid")).thenReturn(Optional.of(user));
        when(accountRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(roleRepository.findById(20L)).thenReturn(Optional.of(managerRole));
        when(unitRepository.findById(10L)).thenReturn(Optional.of(user.getUnit()));
    }

    AuthenticatedUser principal() { return new AuthenticatedUser("own-uid", "stale-token@example.com", Role.valueOf(user.getType().name())); }
    UpdateAccountDetailsRequest updateAccountDetailsRequest(String email, Long roleId, Long unitId) {
        return new UpdateAccountDetailsRequest(" Maria Rosa editada ", "529.982.247-25", email, roleId, unitId, WorkModel.REMOTO);
    }
    UpdateAccountDetailsRequest updateAccountDetailsRequest() { return updateAccountDetailsRequest(" STORED@EXAMPLE.COM ", 20L, 10L); }

    @Test void fullUpdatePreservesIdentityScopeAndUnchangedInactiveLinksForBothProfiles() {
        for (UserType type : new UserType[]{UserType.GESTOR, UserType.GESTOR_WORKSPACE}) {
            user.setType(type);
            Unit oldUnit = user.getUnit(); Instant createdAt = user.getCreatedAt();
            AccountDetailsResponse result = accountService.update(updateAccountDetailsRequest(), principal());
            assertEquals("Maria Rosa editada", result.name()); assertEquals("52998224725", result.cpf());
            assertEquals("stored@example.com", result.email()); assertEquals(WorkModel.REMOTO, result.workModel());
            assertEquals(20L, result.roleId()); assertEquals(10L, result.unitId());
            assertEquals(type, user.getType()); assertEquals(UserStatus.ATIVO, user.getStatus());
            assertEquals("own-uid", user.getFirebaseUid()); assertEquals(100L, user.getId());
            assertEquals(createdAt, user.getCreatedAt()); assertSame(oldUnit.workspace, user.getUnit().workspace);
        }
        verifyNoInteractions(firebaseIdentityService, userRepository);
        verify(accountRepository, never()).lockRoleActive(any(), any());
        verify(accountRepository, never()).lockUnitActive(any(), any());
        verify(accountRepository, times(2)).findForUpdateByFirebaseUid("own-uid");
    }

    @Test void managerCannotChangeOwnRoleOrUnitEvenIfDropdownContainsIt() {
        user.setType(UserType.GESTOR);
        assertThrows(AccessDeniedException.class, () -> accountService.update(updateAccountDetailsRequest(user.getEmail(), 21L, 10L), principal()));
        assertThrows(AccessDeniedException.class, () -> accountService.update(updateAccountDetailsRequest(user.getEmail(), 20L, 11L), principal()));
        verify(accountRepository, never()).saveAndFlush(any()); verifyNoInteractions(firebaseIdentityService, userAvatarService);
    }

    Unit newUnit(Long workspaceId) {
        Workspace workspace = new Workspace(); workspace.id = workspaceId;
        Unit unit = new Unit(); unit.id = 11L; unit.name = "São Paulo"; unit.workspace = workspace; unit.isActive = true;
        when(unitRepository.findById(11L)).thenReturn(Optional.of(unit));
        return unit;
    }

    com.astro.api.role.model.Role newRole(Long workspaceId) {
        com.astro.api.role.model.Role role = new com.astro.api.role.model.Role();
        Workspace workspace = new Workspace(); workspace.id = workspaceId;
        role.setId(21L); role.setName("Supervisora"); role.setWorkspace(workspace); role.setActive(true);
        when(roleRepository.findById(21L)).thenReturn(Optional.of(role)); return role;
    }

    @Test void workspaceManagerMayChooseOnlyActiveLinksInTheSameWorkspace() {
        newRole(1L); newUnit(1L);
        when(accountRepository.lockRoleActive(21L, 1L)).thenReturn(true);
        when(accountRepository.lockUnitActive(11L, 1L)).thenReturn(true);
        AccountDetailsResponse result = accountService.update(updateAccountDetailsRequest(user.getEmail(), 21L, 11L), principal());
        assertEquals(21L, result.roleId()); assertEquals("Supervisora", result.roleName());
        assertEquals(11L, result.unitId()); assertEquals("São Paulo", result.unitName());
        assertEquals(1L, user.getUnit().workspace.id); assertEquals(UserType.GESTOR_WORKSPACE, user.getType());
    }

    @Test void rejectsMissingAndForeignLinksWithoutWritingOrCallingFirebase() {
        assertThrows(ResourceNotFoundException.class, () -> accountService.update(updateAccountDetailsRequest(user.getEmail(), 21L, 10L), principal()));
        assertThrows(ResourceNotFoundException.class, () -> accountService.update(updateAccountDetailsRequest(user.getEmail(), 20L, 11L), principal()));
        newRole(2L); newUnit(2L);
        assertThrows(ResourceNotFoundException.class, () -> accountService.update(updateAccountDetailsRequest(user.getEmail(), 21L, 10L), principal()));
        assertThrows(ResourceNotFoundException.class, () -> accountService.update(updateAccountDetailsRequest(user.getEmail(), 20L, 11L), principal()));
        verify(accountRepository, never()).saveAndFlush(any()); verifyNoInteractions(firebaseIdentityService, userAvatarService);
    }

    @Test void rejectsInactiveNewLinksUsingFreshLockedStatusReads() {
        newRole(1L); newUnit(1L);
        when(accountRepository.lockRoleActive(21L, 1L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> accountService.update(updateAccountDetailsRequest(user.getEmail(), 21L, 10L), principal()));
        when(accountRepository.lockUnitActive(11L, 1L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> accountService.update(updateAccountDetailsRequest(user.getEmail(), 20L, 11L), principal()));
        verify(accountRepository, never()).saveAndFlush(any()); verifyNoInteractions(firebaseIdentityService);
    }

    @Test void rejectsCpfAndEmailConflictsBeforeFirebase() {
        when(accountRepository.existsByCpfAndIdNot("52998224725", 100L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> accountService.update(updateAccountDetailsRequest(), principal()));
        when(accountRepository.existsByCpfAndIdNot("52998224725", 100L)).thenReturn(false);
        when(accountRepository.existsByEmailIgnoreCaseAndIdNot("stored@example.com", 100L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> accountService.update(updateAccountDetailsRequest(), principal()));
        verify(accountRepository, never()).saveAndFlush(any()); verifyNoInteractions(firebaseIdentityService);
    }

    FirebaseIdentityService.EmailState emailState() {
        FirebaseIdentityService.EmailState previousEmailState = new FirebaseIdentityService.EmailState("stored@example.com", true);
        when(firebaseIdentityService.readEmailState("own-uid")).thenReturn(previousEmailState); return previousEmailState;
    }

    @Test void emailChangesReuseTheExistingIdentityAndOnlySucceedAfterCommit() {
        emailState();
        AccountDetailsResponse result = accountService.update(updateAccountDetailsRequest("new@example.com", 20L, 10L), principal());
        assertEquals("new@example.com", result.email());
        InOrder invocationOrder = inOrder(accountRepository, firebaseIdentityService, transactionManager);
        invocationOrder.verify(accountRepository).saveAndFlush(user);
        invocationOrder.verify(firebaseIdentityService).readEmailState("own-uid");
        invocationOrder.verify(firebaseIdentityService).updateEmail("own-uid", "new@example.com");
        invocationOrder.verify(transactionManager).commit(any());
        verify(firebaseIdentityService, never()).restoreEmail(any(), any());
        verify(firebaseIdentityService, never()).createUser(any(), any(), any());
    }

    @Test void databaseFlushFailureNeverChangesFirebase() {
        when(accountRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique email"));
        assertThrows(DataIntegrityViolationException.class, () -> accountService.update(updateAccountDetailsRequest("new@example.com", 20L, 10L), principal()));
        verifyNoInteractions(firebaseIdentityService); verify(transactionManager).rollback(any());
    }

    @Test void databaseCommitFailureRestoresPreviousFirebaseEmailAndVerifiedState() {
        FirebaseIdentityService.EmailState previousEmailState = emailState();
        doThrow(new IllegalStateException("commit failure")).when(transactionManager).commit(any());
        assertThrows(IllegalStateException.class, () -> accountService.update(updateAccountDetailsRequest("new@example.com", 20L, 10L), principal()));
        verify(firebaseIdentityService).restoreEmail("own-uid", previousEmailState);
    }

    @Test void firebaseTimeoutRollsBackAndCompensatesAPotentialRemoteWrite() {
        FirebaseIdentityService.EmailState previousEmailState = emailState();
        doThrow(new IllegalStateException("timeout")).when(firebaseIdentityService).updateEmail(any(), any());
        assertThrows(IllegalStateException.class, () -> accountService.update(updateAccountDetailsRequest("new@example.com", 20L, 10L), principal()));
        verify(transactionManager).rollback(any()); verify(firebaseIdentityService).restoreEmail("own-uid", previousEmailState);
    }

    @Test void explicitFirebaseDuplicateIsAConflictWithNoRemoteCompensation() {
        emailState();
        doThrow(new ConflictException("Já existe uma conta Firebase para este e-mail")).when(firebaseIdentityService).updateEmail(any(), any());
        assertThrows(ConflictException.class, () -> accountService.update(updateAccountDetailsRequest("new@example.com", 20L, 10L), principal()));
        verify(transactionManager).rollback(any()); verify(firebaseIdentityService, never()).restoreEmail(any(), any());
    }

    @Test void identityReadFailureAndDivergenceNeverUpdateFirebase() {
        when(firebaseIdentityService.readEmailState("own-uid")).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> accountService.update(updateAccountDetailsRequest("new@example.com", 20L, 10L), principal()));
        user.setEmail("stored@example.com");
        doReturn(new FirebaseIdentityService.EmailState("foreign@example.com", false)).when(firebaseIdentityService).readEmailState("own-uid");
        assertThrows(ConflictException.class, () -> accountService.update(updateAccountDetailsRequest("new@example.com", 20L, 10L), principal()));
        verify(firebaseIdentityService, never()).updateEmail(any(), any()); verify(firebaseIdentityService, never()).restoreEmail(any(), any());
    }

    @Test void compensationFailureContainsBothManagerProfilesAndReturnsAnError() {
        for (UserType type : new UserType[]{UserType.GESTOR, UserType.GESTOR_WORKSPACE}) {
            user.setType(type); user.setEmail("stored@example.com"); emailState();
            doThrow(new IllegalStateException("timeout")).when(firebaseIdentityService).updateEmail(any(), any());
            doThrow(new IllegalStateException("restore failed")).when(firebaseIdentityService).restoreEmail(any(), any());
            when(accountRepository.deactivateAfterEmailFailure(100L, "own-uid")).thenReturn(1);
            assertThrows(IllegalStateException.class, () -> accountService.update(updateAccountDetailsRequest("new@example.com", 20L, 10L), principal()));
        }
        verify(accountRepository, times(2)).deactivateAfterEmailFailure(100L, "own-uid");
    }

    @Test void staleProfileInactiveAccountOrMissingIdentityCannotBeUpdated() {
        assertThrows(AccessDeniedException.class, () -> accountService.update(updateAccountDetailsRequest(), null));
        assertThrows(AccessDeniedException.class, () -> accountService.update(updateAccountDetailsRequest(), new AuthenticatedUser("own-uid", null, Role.COLABORADOR)));
        assertThrows(AccessDeniedException.class, () -> accountService.update(updateAccountDetailsRequest(), new AuthenticatedUser("own-uid", null, Role.GESTOR)));
        user.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> accountService.update(updateAccountDetailsRequest(), principal()));
        when(accountRepository.findForUpdateByFirebaseUid("own-uid")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> accountService.update(updateAccountDetailsRequest(), principal()));
        verify(accountRepository, never()).saveAndFlush(any()); verifyNoInteractions(firebaseIdentityService, userAvatarService);
    }
}
