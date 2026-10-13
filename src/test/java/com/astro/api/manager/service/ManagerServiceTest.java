package com.astro.api.manager.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.manager.mapper.ManagerMapper;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.manager.repository.ManagerSummaryProjection;
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
    final ManagerService service = new ManagerService(repository, users, new ManagerMapper());
    final AuthenticatedUser principal = new AuthenticatedUser("workspace-manager", "owner@astro.com", Role.GESTOR_WORKSPACE);
    User requester;

    @BeforeEach void setUp() {
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
            when(row.getName()).thenReturn("Gestor"); when(row.getEmail()).thenReturn("manager@astro.com");
            when(row.getUnitId()).thenReturn(10L); when(row.getStatus()).thenReturn(status.name()); rows.add(row);
        }
        when(repository.findAllInWorkspace(1L)).thenReturn(rows);
        var result = service.findAll(principal);
        assertEquals(List.of(UserStatus.PRE_CADASTRADO, UserStatus.ATIVO, UserStatus.DESATIVADO),
                result.stream().map(value -> value.status()).toList());
        assertEquals(3, result.stream().map(value -> value.id()).distinct().count());
        verify(repository).findAllInWorkspace(1L);
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
        verifyNoInteractions(repository);
    }
}
