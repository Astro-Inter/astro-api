package com.astro.api.manager.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.manager.mapper.ManagerMapper;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.manager.repository.ManagerSummaryProjection;
import com.astro.api.manager.repository.ManagerDetailsProjection;
import com.astro.api.user.service.UserAvatarService;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagerServiceTest {
    final ManagerRepository repository = mock(ManagerRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final com.astro.api.role.repository.RoleRepository roles = mock(com.astro.api.role.repository.RoleRepository.class);
    final com.astro.api.unit.repository.UnitRepository units = mock(com.astro.api.unit.repository.UnitRepository.class);
    final com.astro.api.auth.service.FirebaseIdentityService firebase = mock(com.astro.api.auth.service.FirebaseIdentityService.class);
    final org.springframework.transaction.PlatformTransactionManager transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
    final UserAvatarService avatars = mock(UserAvatarService.class);
    final ManagerService service = new ManagerService(repository, users, new ManagerMapper(), roles, units,
            firebase, new ManagerStatusPolicy(), transactions, avatars);
    final AuthenticatedUser principal = new AuthenticatedUser("workspace-manager", "owner@astro.com", Role.GESTOR_WORKSPACE);
    User requester;

    @BeforeEach void setUp() {
        when(transactions.getTransaction(any())).thenAnswer(call -> new org.springframework.transaction.support.SimpleTransactionStatus());
        Workspace workspace = new Workspace(); workspace.id = 1L;
        Unit unit = new Unit(); unit.id = 10L; unit.workspace = workspace;
        requester = new User(); requester.setId(100L); requester.setUnit(unit);
        requester.setType(UserType.GESTOR_WORKSPACE); requester.setStatus(UserStatus.ATIVO);
        when(users.findByFirebaseUid(principal.firebaseUid())).thenReturn(Optional.of(requester));
    }

    @Test void listsAllStatusesIncludingSeveralManagersInTheSameUnit() {
        var rows = new ArrayList<ManagerSummaryProjection>();
        for (UserStatus status : UserStatus.values()) {
            var row = mock(ManagerSummaryProjection.class);
            when(row.getId()).thenReturn((long) status.ordinal() + 1);
            when(row.getName()).thenReturn("Lucas Ribeiro"); when(row.getEmail()).thenReturn("manager@astro.com");
            when(row.getUnitId()).thenReturn(10L); when(row.getStatus()).thenReturn(status.name()); rows.add(row);
        }
        when(repository.findAllInWorkspace(1L)).thenReturn(rows);
        when(avatars.findUrls(List.of(1L, 2L, 3L))).thenReturn(Map.of(2L, "https://r2.example.com/manager"));
        var result = service.findAll(principal);
        assertEquals(List.of(UserStatus.PRE_CADASTRADO, UserStatus.ATIVO, UserStatus.DESATIVADO),
                result.stream().map(value -> value.status()).toList());
        assertEquals(3, result.stream().map(value -> value.id()).distinct().count());
        verify(repository).findAllInWorkspace(1L);
        assertNull(result.getFirst().profilePhotoUrl());
        assertEquals("https://r2.example.com/manager", result.get(1).profilePhotoUrl());
        assertEquals("LR", result.getFirst().avatarInitials());
        verify(avatars).findUrls(List.of(1L, 2L, 3L));
        verify(avatars, never()).findUrl(any());
    }

    @Test void returnsEmptyList() {
        when(repository.findAllInWorkspace(1L)).thenReturn(List.of());
        assertTrue(service.findAll(principal).isEmpty());
    }

    @Test void detailsUseTheAuthorizedWorkspaceAndHideMissingOrInaccessibleRecords() {
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.findById(8L, principal));
        verify(repository).findDetailsInWorkspace(8L, 1L);
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.findById(0L, principal));
        verifyNoInteractions(avatars);
    }

    @Test void detailsLoadThePhotoOnlyAfterFindingTheManagerInTheAuthorizedWorkspace() {
        ManagerDetailsProjection row = mock(ManagerDetailsProjection.class);
        when(row.getId()).thenReturn(4L);
        when(row.getName()).thenReturn("Lucas Ribeiro");
        when(row.getStatus()).thenReturn("ATIVO");
        when(repository.findDetailsInWorkspace(4L, 1L)).thenReturn(Optional.of(row));
        when(avatars.findUrl(4L)).thenReturn("https://r2.example.com/manager");
        var result = service.findById(4L, principal);
        assertEquals("https://r2.example.com/manager", result.profilePhotoUrl());
        assertEquals("LR", result.avatarInitials());
        var order = inOrder(repository, avatars);
        order.verify(repository).findDetailsInWorkspace(4L, 1L);
        order.verify(avatars).findUrl(4L);
    }

    @Test void promotionReusesTheSharedUserAvatarCacheWithoutSigningAnotherLink() {
        var photos = mock(com.astro.api.user.repository.UserProfilePhotoRepository.class);
        var storage = mock(com.astro.api.common.storage.R2StorageService.class);
        var redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> values =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(List.of("usuario:foto:4"))).thenReturn(List.of("https://r2.example.com/cached-collaborator"));
        UserAvatarService sharedAvatars = new UserAvatarService(photos, redis, storage);
        String selectedPhoto = sharedAvatars.findUrl(4L);
        ManagerService withSharedCache = new ManagerService(repository, users, new ManagerMapper(), roles, units,
                firebase, new ManagerStatusPolicy(), transactions, sharedAvatars);
        eligibleCollaborator();
        var result = withSharedCache.promote(new com.astro.api.manager.dto.request.PromoteManagerRequest(
                4L, com.astro.api.manager.dto.request.ManagerAccountStatus.ATIVO), principal);
        assertEquals(selectedPhoto, result.profilePhotoUrl());
        assertEquals("C", result.avatarInitials());
        verify(values, times(2)).multiGet(List.of("usuario:foto:4"));
        verifyNoInteractions(photos, storage);
        verify(redis, never()).delete(anyString());
    }

    @Test void updateAndStatusResponsesKeepThePhotoAndRefreshInitialsFromTheCurrentName() {
        User manager = editableManager();
        when(avatars.findUrl(4L)).thenReturn("https://r2.example.com/manager");
        var updated = service.update(4L, updateBody(manager.getEmail(), 2L, 10L, UserStatus.ATIVO), principal);
        assertEquals("https://r2.example.com/manager", updated.profilePhotoUrl());
        assertEquals("GE", updated.avatarInitials());
        var deactivated = service.changeStatus(4L, statusBody(
                com.astro.api.manager.dto.request.ManagerAccountStatus.DESATIVADO), principal);
        assertEquals(updated.profilePhotoUrl(), deactivated.profilePhotoUrl());
        assertEquals("GE", deactivated.avatarInitials());
        verify(avatars, never()).invalidate(any());
    }

    User eligibleCollaborator() {
        User user = new User(); user.setId(4L); user.setName("Colaborador"); user.setCpf("12345678900");
        user.setEmail("collaborator@astro.com"); user.setFirebaseUid("collaborator-uid");
        user.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        user.setType(UserType.COLABORADOR); user.setStatus(UserStatus.ATIVO); user.setWorkModel(WorkModel.REMOTO);
        var role = new com.astro.api.role.model.Role(); role.setId(2L); role.setActive(true);
        role.setWorkspace(requester.getUnit().workspace); user.setRole(role); user.setUnit(requester.getUnit());
        when(repository.findUserForUpdateInWorkspace(4L, 1L)).thenReturn(Optional.of(user));
        when(repository.lockRoleActive(2L, 1L)).thenReturn(true); when(repository.lockUnitActive(10L, 1L)).thenReturn(true);
        when(repository.saveAndFlush(user)).thenReturn(user);
        return user;
    }
    User editableManager() {
        var user = eligibleCollaborator(); user.setType(UserType.GESTOR);
        when(roles.findById(2L)).thenReturn(Optional.of(user.getRole()));
        when(units.findById(10L)).thenReturn(Optional.of(user.getUnit()));
        return user;
    }
    com.astro.api.manager.dto.request.UpdateManagerRequest updateBody(String email, Long roleId, Long unitId, UserStatus status) {
        return new com.astro.api.manager.dto.request.UpdateManagerRequest("  Gestor editado  ", "529.982.247-25",
                email, roleId, unitId, WorkModel.HIBRIDO, status);
    }
    @Test void fullUpdateNormalizesFieldsPreservesInternalIdentityAndDoesNotChangeAuthenticationWhenEmailIsUnchanged() {
        var user = editableManager(); var created = user.getCreatedAt();
        var result = service.update(4L, updateBody(" COLLABORATOR@ASTRO.COM ", 2L, 10L, UserStatus.ATIVO), principal);
        assertEquals("Gestor editado", result.name()); assertEquals("52998224725", result.cpf());
        assertEquals("collaborator@astro.com", result.email()); assertEquals(WorkModel.HIBRIDO, result.workModel());
        assertEquals(UserType.GESTOR, user.getType()); assertEquals("collaborator-uid", user.getFirebaseUid());
        assertEquals(created, user.getCreatedAt()); assertEquals(4L, user.getId());
        verifyNoInteractions(firebase);
    }
    @Test void permitsKeepingInactiveLinksButRejectsNewInactiveLinksAndOtherWorkspaces() {
        var user = editableManager(); user.getRole().setActive(false); user.getUnit().isActive = false;
        service.update(4L, updateBody(user.getEmail(), 2L, 10L, UserStatus.ATIVO), principal);
        verify(repository, never()).lockRoleActive(any(), any()); verify(repository, never()).lockUnitActive(any(), any());
        var destination = new Unit(); destination.id = 20L; destination.workspace = user.getUnit().workspace;
        when(units.findById(20L)).thenReturn(Optional.of(destination));
        when(repository.lockUnitActive(20L, 1L)).thenReturn(false);
        assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.update(4L, updateBody(user.getEmail(), 2L, 20L, UserStatus.ATIVO), principal));
        var other = new Workspace(); other.id = 9L; destination.workspace = other;
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.update(4L, updateBody(user.getEmail(), 2L, 20L, UserStatus.ATIVO), principal));
        user.getRole().setWorkspace(other);
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.update(4L, updateBody(user.getEmail(), 2L, 10L, UserStatus.ATIVO), principal));
    }
    @Test void detectsCpfAndEmailConflictsBeforeFirebaseAndProtectsOtherProfiles() {
        var user = editableManager(); var body = updateBody(user.getEmail(), 2L, 10L, UserStatus.ATIVO);
        when(repository.cpfExists(body.cpf(), 4L)).thenReturn(true);
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.update(4L, body, principal));
        when(repository.cpfExists(body.cpf(), 4L)).thenReturn(false); when(repository.emailExists(body.email(), 4L)).thenReturn(true);
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.update(4L, body, principal));
        for (UserType type : List.of(UserType.COLABORADOR, UserType.GESTOR_WORKSPACE)) {
            user.setType(type);
            assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.update(4L, body, principal));
        }
        verify(repository, never()).saveAndFlush(any()); verifyNoInteractions(firebase);
    }
    @Test void synchronizesTheExistingFirebaseAccountAndRestoresAfterCommitFailure() {
        var user = editableManager();
        var previous = new com.astro.api.auth.service.FirebaseIdentityService.EmailState(user.getEmail(), true);
        when(firebase.readEmailState(user.getFirebaseUid())).thenReturn(previous);
        service.update(4L, updateBody("new@astro.com", 2L, 10L, UserStatus.ATIVO), principal);
        verify(firebase).updateEmail("collaborator-uid", "new@astro.com");
        user.setEmail(previous.email());
        doThrow(new IllegalStateException("commit failure")).when(transactions).commit(any());
        assertThrows(IllegalStateException.class, () -> service.update(4L, updateBody("next@astro.com", 2L, 10L, UserStatus.ATIVO), principal));
        verify(firebase).restoreEmail("collaborator-uid", previous);
    }
    @Test void rejectsDivergentIdentityAndContainsFailedFirebaseCompensation() {
        var user = editableManager(); var body = updateBody("new@astro.com", 2L, 10L, UserStatus.ATIVO);
        when(firebase.readEmailState(user.getFirebaseUid())).thenReturn(new com.astro.api.auth.service.FirebaseIdentityService.EmailState("other@astro.com", false));
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.update(4L, body, principal));
        verify(firebase, never()).updateEmail(any(), any());
        user.setEmail("collaborator@astro.com");
        var previous = new com.astro.api.auth.service.FirebaseIdentityService.EmailState(user.getEmail(), true);
        when(firebase.readEmailState(user.getFirebaseUid())).thenReturn(previous);
        doThrow(new IllegalStateException("Firebase failure")).when(firebase).updateEmail(any(), any());
        doThrow(new IllegalStateException("restore failure")).when(firebase).restoreEmail(any(), any());
        when(repository.deactivateAfterEmailFailure(4L, user.getFirebaseUid())).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> service.update(4L, body, principal));
        verify(repository).deactivateAfterEmailFailure(4L, "collaborator-uid");
    }
    com.astro.api.manager.dto.request.ManagerStatusRequest statusBody(com.astro.api.manager.dto.request.ManagerAccountStatus status) {
        return new com.astro.api.manager.dto.request.ManagerStatusRequest(status);
    }
    @Test void statusIsExplicitIdempotentAndPreservesTheManagerProfile() {
        var user = editableManager();
        service.changeStatus(4L, statusBody(com.astro.api.manager.dto.request.ManagerAccountStatus.DESATIVADO), principal);
        assertEquals(UserStatus.DESATIVADO, user.getStatus()); assertEquals(UserType.GESTOR, user.getType());
        clearInvocations(repository);
        service.changeStatus(4L, statusBody(com.astro.api.manager.dto.request.ManagerAccountStatus.DESATIVADO), principal);
        verify(repository, never()).saveAndFlush(any());
        service.changeStatus(4L, statusBody(com.astro.api.manager.dto.request.ManagerAccountStatus.ATIVO), principal);
        assertEquals(UserStatus.ATIVO, user.getStatus()); assertEquals(UserType.GESTOR, user.getType());
        verify(repository).lockRoleActive(2L, 1L); verify(repository).lockUnitActive(10L, 1L);
    }
    @Test void activationRequiresCompletedAuthenticationAndActiveLinksForBothPutAndPatch() {
        var request = statusBody(com.astro.api.manager.dto.request.ManagerAccountStatus.ATIVO);
        var user = editableManager(); user.setStatus(UserStatus.PRE_CADASTRADO);
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(4L, request, principal));
        assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.update(4L, updateBody(user.getEmail(), 2L, 10L, UserStatus.ATIVO), principal));
        user.setStatus(UserStatus.DESATIVADO); user.setFirebaseUid(null);
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(4L, request, principal));
        user.setFirebaseUid("uid"); when(repository.lockRoleActive(2L, 1L)).thenReturn(false);
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(4L, request, principal));
        when(repository.lockRoleActive(2L, 1L)).thenReturn(true); when(repository.lockUnitActive(10L, 1L)).thenReturn(false);
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(4L, request, principal));
        user.setType(UserType.GESTOR_WORKSPACE);
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.changeStatus(4L, statusBody(com.astro.api.manager.dto.request.ManagerAccountStatus.DESATIVADO), principal));
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void fullUpdateCanMoveToAnActiveUnitAndRoleInTheSameWorkspace() {
        var user = editableManager();
        var role = new com.astro.api.role.model.Role(); role.setId(3L); role.setWorkspace(user.getUnit().workspace);
        var unit = new Unit(); unit.id = 11L; unit.workspace = user.getUnit().workspace;
        when(roles.findById(3L)).thenReturn(Optional.of(role)); when(units.findById(11L)).thenReturn(Optional.of(unit));
        when(repository.lockRoleActive(3L, 1L)).thenReturn(true); when(repository.lockUnitActive(11L, 1L)).thenReturn(true);
        var response = service.update(4L, updateBody(user.getEmail(), 3L, 11L, UserStatus.ATIVO), principal);
        assertEquals(3L, response.roleId()); assertEquals(11L, response.unitId()); assertSame(unit, user.getUnit());
    }
    @Test void promotesTheExistingAccountToBothRequestedStatusesPreservingIdentityAndLinks() {
        for (var status : com.astro.api.manager.dto.request.ManagerAccountStatus.values()) {
            User user = eligibleCollaborator(); var role = user.getRole(); var unit = user.getUnit(); var created = user.getCreatedAt();
            var result = service.promote(new com.astro.api.manager.dto.request.PromoteManagerRequest(4L, status), principal);
            assertEquals(4L, result.id()); assertEquals(UserType.GESTOR, user.getType()); assertEquals(status.name(), result.status().name());
            assertEquals("collaborator-uid", user.getFirebaseUid()); assertSame(role, user.getRole()); assertSame(unit, user.getUnit());
            assertEquals(created, user.getCreatedAt()); assertEquals("12345678900", user.getCpf()); assertEquals(WorkModel.REMOTO, user.getWorkModel());
        }
    }
    @Test void rejectsIneligibleCandidatesAndOtherProfiles() {
        var request = new com.astro.api.manager.dto.request.PromoteManagerRequest(4L, com.astro.api.manager.dto.request.ManagerAccountStatus.ATIVO);
        for (UserStatus status : List.of(UserStatus.PRE_CADASTRADO, UserStatus.DESATIVADO)) {
            var user = eligibleCollaborator(); user.setStatus(status);
            assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.promote(request, principal));
        }
        var user = eligibleCollaborator(); user.setFirebaseUid(" ");
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.promote(request, principal));
        for (UserType type : List.of(UserType.GESTOR, UserType.GESTOR_WORKSPACE)) {
            user = eligibleCollaborator(); user.setType(type);
            assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.promote(request, principal));
        }
        eligibleCollaborator(); when(repository.lockRoleActive(2L, 1L)).thenReturn(false);
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.promote(request, principal));
        eligibleCollaborator(); when(repository.lockUnitActive(10L, 1L)).thenReturn(false);
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.promote(request, principal));
        when(repository.findUserForUpdateInWorkspace(4L, 1L)).thenReturn(Optional.empty());
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.promote(request, principal));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void rejectsUntrustedIdentityRolesAndInactiveRequesterBeforeQuerying() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(null));
        for (Role role : Role.values()) {
            if (role != Role.GESTOR_WORKSPACE) assertThrows(AccessDeniedException.class,
                    () -> service.findAll(new AuthenticatedUser(principal.firebaseUid(), null, role)));
        }
        requester.setType(UserType.GESTOR);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        requester.setType(UserType.GESTOR_WORKSPACE); requester.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        verifyNoInteractions(repository, avatars);
    }
}
