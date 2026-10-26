package com.astro.api.conformity;

import com.astro.api.auth.security.*;
import com.astro.api.conformity.mapper.ConformityMapper;
import com.astro.api.conformity.repository.*;
import com.astro.api.conformity.service.ConformityService;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserAvatarService;
import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.unit.model.Unit;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConformityServiceTest {
    final ConformityRepository repository = mock(ConformityRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final ConformityUserRepository collaborators = mock(ConformityUserRepository.class);
    final NrCatalogRepository nrs = mock(NrCatalogRepository.class);
    final UserAvatarService userAvatarService = mock(UserAvatarService.class);
    final ConformityService service = new ConformityService(repository, users, new ConformityMapper(), collaborators, nrs, userAvatarService);
    final AuthenticatedUser principal = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR);

    User manager() {
        User manager = new User(); manager.setType(UserType.GESTOR); manager.setStatus(UserStatus.ATIVO);
        Unit unit = new Unit(); Workspace workspace = new Workspace(); workspace.id = 8L;
        unit.workspace = workspace; manager.setUnit(unit);
        when(users.findByFirebaseUid("manager")).thenReturn(Optional.of(manager));
        return manager;
    }

    @Test void requiresActiveManagerWithMatchingValidatedRoleAndWorkspace() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(null));
        User manager = manager();
        manager.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        manager.setStatus(UserStatus.ATIVO); manager.setType(UserType.COLABORADOR);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        manager.setType(UserType.GESTOR_WORKSPACE);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        manager.setType(UserType.GESTOR); manager.setUnit(null);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal));
        verifyNoInteractions(repository, userAvatarService);
    }

    @Test void scopesListToTheAuthenticatedWorkspaceAndPreservesOriginAndStatus() {
        manager();
        ConformityProjection row = mock(ConformityProjection.class);
        when(row.getCollaboratorId()).thenReturn(2L);
        when(row.getOrigin()).thenReturn("CONCLUSAO_EVENTO"); when(row.getApplicable()).thenReturn(true);
        when(row.getStatus()).thenReturn("Sem validade"); when(row.getEventCompletionId()).thenReturn(42L);
        when(repository.findAllInScope("manager", 8L)).thenReturn(List.of(row));
        var result = service.findAll(principal).getFirst();
        assertEquals("CONCLUSAO_EVENTO", result.origin()); assertEquals(42L, result.eventCompletionId());
        assertEquals("Sem validade", result.status()); assertNull(result.expiresAt());
        when(repository.findAllInScope("manager", 8L)).thenReturn(List.of());
        assertTrue(service.findAll(principal).isEmpty());
    }

    @Test void readsAnAuthorizedNonApplicableHistoricalRecordByItsOwnId() {
        manager(); ConformityProjection row = mock(ConformityProjection.class);
        when(row.getId()).thenReturn(7L); when(row.getApplicable()).thenReturn(false);
        when(row.getCollaboratorId()).thenReturn(3L);
        when(row.getCollaboratorName()).thenReturn("Luana Souza");
        when(repository.findDetailsInScope(7L, "manager", 8L)).thenReturn(Optional.of(row));
        var result = service.findById(7L, principal);
        assertEquals(7L, result.id()); assertFalse(result.applicable());
        assertNull(result.profilePhotoUrl()); assertEquals("LS", result.avatarInitials());
        verify(userAvatarService).findUrl(3L);
        verify(repository, never()).findAllInScope(anyString(), anyLong());
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.findById(99L, principal));
        assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.findById(0L, principal));
    }

    @Test void loadsListPhotosInOneBatchAndReturnsInitialsForCollaboratorsWithoutPhoto() {
        manager();
        ConformityProjection withPhoto = mock(ConformityProjection.class);
        when(withPhoto.getCollaboratorId()).thenReturn(2L);
        when(withPhoto.getCollaboratorName()).thenReturn("Kirk Hammett");
        ConformityProjection withoutPhoto = mock(ConformityProjection.class);
        when(withoutPhoto.getCollaboratorId()).thenReturn(3L);
        when(withoutPhoto.getCollaboratorName()).thenReturn("Luana Souza");
        when(repository.findAllInScope("manager", 8L)).thenReturn(List.of(withPhoto, withPhoto, withoutPhoto));
        when(userAvatarService.findUrls(List.of(2L, 2L, 3L))).thenReturn(Map.of(2L, "https://example.com/kirk-photo"));

        List<ConformityResponse> conformities = service.findAll(principal);

        assertEquals("https://example.com/kirk-photo", conformities.getFirst().profilePhotoUrl());
        assertEquals("KH", conformities.getFirst().avatarInitials());
        assertEquals(conformities.getFirst().profilePhotoUrl(), conformities.get(1).profilePhotoUrl());
        assertNull(conformities.getLast().profilePhotoUrl());
        assertEquals("LS", conformities.getLast().avatarInitials());
        org.mockito.InOrder authorizationOrder = inOrder(repository, userAvatarService);
        authorizationOrder.verify(repository).findAllInScope("manager", 8L);
        authorizationOrder.verify(userAvatarService).findUrls(List.of(2L, 2L, 3L));
        verifyNoMoreInteractions(userAvatarService);
    }

    @Test void loadsThePhotoOnlyAfterAuthorizingTheConformity() {
        manager();
        ConformityProjection conformity = mock(ConformityProjection.class);
        when(conformity.getCollaboratorId()).thenReturn(2L);
        when(conformity.getCollaboratorName()).thenReturn("Kirk Hammett");
        when(repository.findDetailsInScope(7L, "manager", 8L)).thenReturn(Optional.of(conformity));
        when(userAvatarService.findUrl(2L)).thenReturn("https://example.com/kirk-photo");

        ConformityResponse response = service.findById(7L, principal);

        assertEquals("https://example.com/kirk-photo", response.profilePhotoUrl());
        assertEquals("KH", response.avatarInitials());
        org.mockito.InOrder authorizationOrder = inOrder(repository, userAvatarService);
        authorizationOrder.verify(repository).findDetailsInScope(7L, "manager", 8L);
        authorizationOrder.verify(userAvatarService).findUrl(2L);
        clearInvocations(userAvatarService);
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.findById(99L, principal));
        verifyNoInteractions(userAvatarService);
    }
}
