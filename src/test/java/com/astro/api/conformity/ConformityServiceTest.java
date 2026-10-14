package com.astro.api.conformity;

import com.astro.api.auth.security.*;
import com.astro.api.conformity.mapper.ConformityMapper;
import com.astro.api.conformity.repository.*;
import com.astro.api.conformity.service.ConformityService;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
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
    final ConformityService service = new ConformityService(repository, users, new ConformityMapper());
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
        verifyNoInteractions(repository);
    }

    @Test void scopesListToTheAuthenticatedWorkspaceAndPreservesOriginAndStatus() {
        manager();
        ConformityProjection row = mock(ConformityProjection.class);
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
        when(repository.findDetailsInScope(7L, "manager", 8L)).thenReturn(Optional.of(row));
        var result = service.findById(7L, principal);
        assertEquals(7L, result.id()); assertFalse(result.applicable());
        verify(repository, never()).findAllInScope(anyString(), anyLong());
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.findById(99L, principal));
        assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.findById(0L, principal));
    }
}
