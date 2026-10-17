package com.astro.api.user.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.mapper.AccountMapper;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.AccountRepository;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    final UserRepository userRepository = mock(UserRepository.class);
    final UserAvatarService userAvatarService = mock(UserAvatarService.class);
    final AccountRepository accountRepository = mock(AccountRepository.class);
    final RoleRepository roleRepository = mock(RoleRepository.class);
    final UnitRepository unitRepository = mock(UnitRepository.class);
    final FirebaseIdentityService firebaseIdentityService = mock(FirebaseIdentityService.class);
    final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    final AccountService accountService = new AccountService(userRepository, userAvatarService, new AccountMapper(), accountRepository, roleRepository, unitRepository,
            firebaseIdentityService, transactionManager);

    @BeforeEach void configureTransactionManager() {
        when(transactionManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }

    static User account(Role role) {
        Workspace workspace = new Workspace(); workspace.id = 1L;
        Unit unit = new Unit(); unit.id = 10L; unit.name = "Osasco"; unit.workspace = workspace;
        User user = new User(); user.setId(100L); user.setFirebaseUid("own-uid");
        user.setName("Maria Rosa"); user.setEmail("stored@example.com");
        user.setUnit(unit); user.setType(UserType.valueOf(role.name())); user.setStatus(UserStatus.ATIVO);
        return user;
    }

    @Test void summaryUsesTheTokenUidAndStoredIdentityForBothManagerProfiles() {
        for (Role role : new Role[]{Role.GESTOR, Role.GESTOR_WORKSPACE}) {
            User user = account(role);
            when(userRepository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(user));
            when(userAvatarService.findUrl(100L)).thenReturn("https://r2.example.com/signed");
            AccountSummaryResponse result = accountService.findSummary(new AuthenticatedUser("own-uid", "stale-token@example.com", role));
            assertEquals("Maria Rosa", result.name());
            assertEquals("stored@example.com", result.email());
            assertEquals("https://r2.example.com/signed", result.profilePhotoUrl());
        }
        verify(userRepository, times(2)).findByFirebaseUid("own-uid");
        verifyNoMoreInteractions(userRepository);
    }

    @Test void summaryKeepsMissingNameAndPhotoNull() {
        User user = account(Role.GESTOR); user.setName(null);
        when(userRepository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(user));
        AccountSummaryResponse result = accountService.findSummary(new AuthenticatedUser("own-uid", null, Role.GESTOR));
        assertNull(result.name()); assertNull(result.profilePhotoUrl());
    }

    @Test void detailsReturnRealCpfAndLinkIdsWithNamesForBothProfiles() {
        for (Role role : new Role[]{Role.GESTOR, Role.GESTOR_WORKSPACE}) {
            User user = account(role);
            user.setCpf("52998224725"); user.setWorkModel(WorkModel.HIBRIDO);
            com.astro.api.role.model.Role managerRole = new com.astro.api.role.model.Role();
            managerRole.setId(20L); managerRole.setName("Engenheira");
            managerRole.setWorkspace(user.getUnit().workspace); user.setRole(managerRole);
            when(userRepository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(user));
            AccountDetailsResponse result = accountService.findDetails(new AuthenticatedUser("own-uid", null, role));
            assertEquals("52998224725", result.cpf());
            assertEquals(20L, result.roleId()); assertEquals("Engenheira", result.roleName());
            assertEquals(10L, result.unitId()); assertEquals("Osasco", result.unitName());
            assertEquals(WorkModel.HIBRIDO, result.workModel());
        }
    }

    @Test void detailsPreserveMissingCpfRoleModalityAndPhotoWithoutInventedValues() {
        when(userRepository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(account(Role.GESTOR_WORKSPACE)));
        AccountDetailsResponse result = accountService.findDetails(new AuthenticatedUser("own-uid", null, Role.GESTOR_WORKSPACE));
        assertNull(result.cpf()); assertNull(result.workModel()); assertNull(result.roleId());
        assertNull(result.roleName()); assertNull(result.profilePhotoUrl());
    }

    @Test void detailsRejectCrossWorkspaceLinksBeforeLoadingPhotos() {
        User user = account(Role.GESTOR);
        com.astro.api.role.model.Role managerRole = new com.astro.api.role.model.Role();
        Workspace foreignWorkspace = new Workspace(); foreignWorkspace.id = 2L;
        managerRole.setWorkspace(foreignWorkspace); user.setRole(managerRole);
        when(userRepository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(user));
        assertThrows(AccessDeniedException.class,
                () -> accountService.findDetails(new AuthenticatedUser("own-uid", null, Role.GESTOR)));
        verifyNoInteractions(userAvatarService);
    }

    @Test void absentAccountIsNotFoundAndUnauthorizedOrInactiveAccountsNeverReadPhotos() {
        AuthenticatedUser principal = new AuthenticatedUser("own-uid", null, Role.GESTOR);
        assertThrows(ResourceNotFoundException.class, () -> accountService.findSummary(principal));
        assertThrows(AccessDeniedException.class, () -> accountService.findSummary(null));
        assertThrows(AccessDeniedException.class, () -> accountService.findSummary(new AuthenticatedUser("", null, Role.GESTOR)));
        assertThrows(AccessDeniedException.class, () -> accountService.findSummary(new AuthenticatedUser("own-uid", null, Role.COLABORADOR)));
        User user = account(Role.GESTOR_WORKSPACE);
        when(userRepository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(user));
        assertThrows(AccessDeniedException.class, () -> accountService.findSummary(principal));
        user.setType(UserType.GESTOR); user.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> accountService.findSummary(principal));
        user.setStatus(UserStatus.ATIVO); user.setUnit(null);
        assertThrows(AccessDeniedException.class, () -> accountService.findSummary(principal));
        verifyNoInteractions(userAvatarService);
    }
}
