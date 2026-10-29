package com.astro.api.user.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.ParticipationStatus;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.repository.CollaboratorRepository;
import com.astro.api.user.repository.CollaboratorEventProjection;
import com.astro.api.user.repository.CollaboratorSummaryProjection;
import com.astro.api.user.repository.CollaboratorDetailsProjection;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.dto.request.CollaboratorStatusRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.astro.api.user.dto.response.CollaboratorSummaryResponse;
import com.astro.api.user.dto.response.CollaboratorDetailsResponse;
import com.astro.api.user.dto.response.CollaboratorProfileResponse;
import com.astro.api.user.dto.response.CollaboratorEventResponse;
import com.astro.api.user.dto.response.CollaboratorEventsResponse;
import com.astro.api.user.dto.response.UserProfileResponse;
import org.mockito.InOrder;
import org.hibernate.exception.ConstraintViolationException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollaboratorServiceTest {
    final CollaboratorRepository repository = mock(CollaboratorRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final UnitRepository units = mock(UnitRepository.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final FirebaseIdentityService identities = mock(FirebaseIdentityService.class);
    final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    final UserAvatarService avatars = mock(UserAvatarService.class);
    final UserService userService = mock(UserService.class);
    final CollaboratorService service = new CollaboratorService(repository, users, new CollaboratorMapper(), roles, units,
            events, identities, new CollaboratorStatusPolicy(), transactionManager, avatars, userService);
    final AuthenticatedUser principal = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR);
    User manager;
    Workspace workspace;
    com.astro.api.role.model.Role role;
    Unit destination;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
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
        role = new com.astro.api.role.model.Role();
        role.setId(5L);
        role.setName("Operador");
        role.setActive(true);
        role.setWorkspace(workspace);
        destination = manager.getUnit();
        destination.name = "Matriz";
        destination.isActive = true;
        when(roles.findById(5L)).thenReturn(Optional.of(role));
        when(units.findById(12L)).thenReturn(Optional.of(destination));
        when(repository.canAccessUnit("manager", 12L)).thenReturn(true);
    }

    @Test
    void listsAllStatusesUsingOnlyTheAuthenticatedManagersScope() {
        List<CollaboratorSummaryProjection> rows = List.of(row(2L, "PRE_CADASTRADO"), row(3L, "ATIVO"), row(4L, "DESATIVADO"));
        when(repository.findAllInScope("manager", 3L)).thenReturn(rows);
        List<CollaboratorSummaryResponse> result = service.findAll(principal);
        assertEquals(List.of(UserStatus.PRE_CADASTRADO, UserStatus.ATIVO, UserStatus.DESATIVADO),
                result.stream().map(response -> response.status()).toList());
        assertEquals(3, result.stream().map(response -> response.id()).distinct().count());
        assertEquals("Operador", result.getFirst().roleName());
        verify(repository).findAllInScope("manager", 3L);
        verify(avatars).findUrls(List.of(2L, 3L, 4L));
    }

    @Test
    void includesPhotosAndFallbackInitialsOnlyForTheAuthorizedList() {
        CollaboratorSummaryProjection first = row(2L, "ATIVO");
        CollaboratorSummaryProjection second = row(3L, "PRE_CADASTRADO");
        when(first.getName()).thenReturn("Bruno Martins");
        when(second.getName()).thenReturn("Ana Silva");
        when(repository.findAllInScope("manager", 3L)).thenReturn(List.of(first, second));
        when(avatars.findUrls(List.of(2L, 3L))).thenReturn(java.util.Map.of(2L, "https://r2.example.com/photo"));
        List<CollaboratorSummaryResponse> result = service.findAll(principal);
        assertEquals("https://r2.example.com/photo", result.getFirst().profilePhotoUrl());
        assertEquals("BM", result.getFirst().avatarInitials());
        assertNull(result.get(1).profilePhotoUrl());
        assertEquals("AS", result.get(1).avatarInitials());
        verify(avatars, never()).findUrl(any());
    }

    @Test
    void hidesSelfOnlyOnRequestBeforeLoadingAvatarsAndPreservesOtherEmployees() {
        CollaboratorSummaryProjection self = row(1L, "ATIVO");
        CollaboratorSummaryProjection other = row(2L, "ATIVO");
        when(repository.findAllInScope("manager", 3L)).thenReturn(List.of(self, other));
        assertEquals(List.of(2L), service.findAll(principal, true).stream().map(value -> value.id()).toList());
        verify(avatars).findUrls(List.of(2L));
        assertEquals(List.of(1L, 2L), service.findAll(principal).stream().map(value -> value.id()).toList());
        verify(avatars).findUrls(List.of(1L, 2L));
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
        verifyNoInteractions(repository, avatars);
    }

    private CollaboratorSummaryProjection row(Long id, String status) {
        CollaboratorSummaryProjection row = mock(CollaboratorSummaryProjection.class);
        when(row.getId()).thenReturn(id);
        when(row.getName()).thenReturn("Colaborador " + id);
        when(row.getEmail()).thenReturn("test" + id + "@example.com");
        when(row.getRoleId()).thenReturn(5L);
        when(row.getRoleName()).thenReturn("Operador");
        when(row.getUnitId()).thenReturn(12L);
        when(row.getUnitName()).thenReturn("Matriz");
        when(row.getStatus()).thenReturn(status);
        when(row.getUserType()).thenReturn("COLABORADOR");
        return row;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = UserType.class, names = {"GESTOR", "GESTOR_WORKSPACE"})
    void managersCannotEditOrChangeTheStatusOfOtherManagers(UserType targetType) {
        User target = collaborator(UserStatus.ATIVO, "completed");
        target.setType(targetType);
        assertThrows(AccessDeniedException.class, () -> service.update(2L, updateBody(UserStatus.ATIVO), principal));
        assertThrows(AccessDeniedException.class, () -> service.changeStatus(2L,
                new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.DESATIVADO), principal));
        assertEquals("Nome anterior", target.getName());
        assertEquals(UserStatus.ATIVO, target.getStatus());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(identities, avatars);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(UserType.class)
    void workspaceManagersCanEditAndChangeStatusOfEveryEmployeeProfileWithoutChangingItsType(UserType targetType) {
        manager.setType(UserType.GESTOR_WORKSPACE);
        AuthenticatedUser owner = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR_WORKSPACE);
        User target = collaborator(UserStatus.ATIVO, "completed");
        target.setType(targetType);
        assertTrue(service.update(2L, updateBody(UserStatus.ATIVO), owner).canManage());
        assertEquals(targetType, target.getType());
        assertEquals("completed", target.getFirebaseUid());
        assertEquals(UserStatus.DESATIVADO, service.changeStatus(2L,
                new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.DESATIVADO), owner).status());
        assertEquals(targetType, target.getType());
    }

    @Test
    void readPermissionsReflectTheAuthenticatedActorWhileEveryProfileRemainsVisible() {
        CollaboratorSummaryProjection self = row(1L, "ATIVO"); when(self.getUserType()).thenReturn("GESTOR");
        CollaboratorSummaryProjection employee = row(2L, "ATIVO");
        CollaboratorSummaryProjection peer = row(3L, "ATIVO"); when(peer.getUserType()).thenReturn("GESTOR");
        CollaboratorSummaryProjection owner = row(4L, "ATIVO"); when(owner.getUserType()).thenReturn("GESTOR_WORKSPACE");
        when(repository.findAllInScope("manager", 3L)).thenReturn(List.of(self, employee, peer, owner));
        assertEquals(List.of(true, true, false, false), service.findAll(principal).stream().map(value -> value.canManage()).toList());
        manager.setType(UserType.GESTOR_WORKSPACE);
        AuthenticatedUser ownerPrincipal = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR_WORKSPACE);
        assertTrue(service.findAll(ownerPrincipal).stream().allMatch(value -> value.canManage()));
    }

    @Test
    void workspaceManagerStillCannotWriteOutsideItsWorkspaceOrAuthorizedUnits() {
        manager.setType(UserType.GESTOR_WORKSPACE);
        AuthenticatedUser owner = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR_WORKSPACE);
        assertThrows(ResourceNotFoundException.class, () -> service.update(99L, updateBody(UserStatus.ATIVO), owner));
        assertThrows(ResourceNotFoundException.class, () -> service.changeStatus(99L,
                new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.DESATIVADO), owner));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(identities);
    }

    @Test
    void loadsDetailsOnlyFromTheAuthorizedViewAndMapsNullableWorkModel() {
        CollaboratorDetailsProjection row = mock(CollaboratorDetailsProjection.class);
        when(row.getId()).thenReturn(2L);
        when(row.getCpf()).thenReturn("52998224725");
        when(row.getStatus()).thenReturn("PRE_CADASTRADO");
        when(row.getWorkspaceId()).thenReturn(3L);
        when(repository.findDetailsInScope(2L, "manager", 3L)).thenReturn(Optional.of(row));
        CollaboratorDetailsResponse response = service.findById(2L, principal);
        assertEquals("52998224725", response.cpf());
        assertEquals(3L, response.workspaceId());
        assertNull(response.workModel());
        when(row.getWorkModel()).thenReturn("HIBRIDO");
        assertEquals(WorkModel.HIBRIDO, service.findById(2L, principal).workModel());
    }

    @Test
    void returnsScopedProfileWithPendingEventsAvatarAndTheSameNrFieldsAsOwnProfile() {
        CollaboratorDetailsProjection row = mock(CollaboratorDetailsProjection.class);
        when(row.getId()).thenReturn(2L);
        when(row.getName()).thenReturn("Ana Silva");
        when(row.getEmail()).thenReturn("ana@example.com");
        when(row.getRoleName()).thenReturn("Operadora");
        when(row.getUnitName()).thenReturn("Matriz");
        when(row.getWorkModel()).thenReturn("HIBRIDO");
        when(repository.findDetailsInScope(2L, "manager", 3L)).thenReturn(Optional.of(row));
        when(avatars.findUrl(2L)).thenReturn("https://r2.example.com/ana.jpg");
        when(repository.findPendingCountById("manager", 2L)).thenReturn(3L);
        List<UserProfileResponse.NrProfileResponse> nrs = List.of(new UserProfileResponse.NrProfileResponse(
                10, LocalDate.of(2027, 4, 30), "Segurança", "Prevenir acidentes", "Trabalho industrial"));
        when(userService.findNrsByUserId(2L)).thenReturn(nrs);

        CollaboratorProfileResponse response = service.findProfileById(2L, principal);

        assertEquals(2L, response.id());
        assertEquals("Ana Silva", response.name());
        assertEquals("Operadora", response.roleName());
        assertEquals("https://r2.example.com/ana.jpg", response.profilePhotoUrl());
        assertEquals("Matriz", response.unitName());
        assertEquals(WorkModel.HIBRIDO, response.workModel());
        assertEquals("ana@example.com", response.email());
        assertEquals(3L, response.pendingCount());
        assertEquals(nrs, response.nrs());
        verify(repository).findDetailsInScope(2L, "manager", 3L);
        verify(repository).findPendingCountById("manager", 2L);
        verify(userService).findNrsByUserId(2L);
    }

    @Test
    void returnsAllScopedEventsAndMapsCompletionAndEventStatuses() {
        CollaboratorDetailsProjection collaborator = mock(CollaboratorDetailsProjection.class);
        when(collaborator.getId()).thenReturn(2L);
        when(collaborator.getName()).thenReturn("Ana Silva");
        when(repository.findDetailsInScope(2L, "manager", 3L)).thenReturn(Optional.of(collaborator));
        LocalDateTime classLimitDate = LocalDateTime.of(2026, 11, 12, 17, 30);
        List<CollaboratorEventProjection> rows = List.of(
                eventRow(10L, "Integração", classLimitDate, null, "ATIVO"),
                eventRow(11L, "NR-10", classLimitDate, "PENDENTE", "CANCELADO"),
                eventRow(12L, "NR-12", classLimitDate, "CONCLUIDO", "CONCLUIDO"),
                eventRow(13L, "NR-35", classLimitDate, "REJEITADO", "ATIVO"));
        when(repository.findEventsInScope(2L, "manager")).thenReturn(rows);

        CollaboratorEventsResponse result = service.findEventsById(2L, principal);

        assertEquals(2L, result.collaboratorId());
        assertEquals("Ana Silva", result.collaboratorName());
        assertEquals(List.of(10L, 11L, 12L, 13L), result.events().stream().map(CollaboratorEventResponse::eventId).toList());
        assertEquals(List.of(ParticipationStatus.PENDENTE, ParticipationStatus.EM_ANALISE,
                        ParticipationStatus.CONCLUIDO, ParticipationStatus.REJEITADO),
                result.events().stream().map(CollaboratorEventResponse::completionStatus).toList());
        assertEquals(List.of(EventStatus.ATIVO, EventStatus.CANCELADO, EventStatus.CONCLUIDO, EventStatus.ATIVO),
                result.events().stream().map(CollaboratorEventResponse::eventStatus).toList());
        assertEquals(List.of(CompletionMode.GESTOR, CompletionMode.COLABORADOR,
                        CompletionMode.GESTOR, CompletionMode.COLABORADOR),
                result.events().stream().map(CollaboratorEventResponse::completionMode).toList());
        assertEquals(classLimitDate, result.events().getFirst().classLimitDate());
        verify(repository).findDetailsInScope(2L, "manager", 3L);
        verify(repository).findEventsInScope(2L, "manager");
    }

    @Test
    void returnsEmptyEventListForAnAuthorizedCollaboratorWithoutParticipations() {
        CollaboratorDetailsProjection collaborator = mock(CollaboratorDetailsProjection.class);
        when(collaborator.getId()).thenReturn(2L);
        when(collaborator.getName()).thenReturn("Ana Silva");
        when(repository.findDetailsInScope(2L, "manager", 3L)).thenReturn(Optional.of(collaborator));
        when(repository.findEventsInScope(2L, "manager")).thenReturn(List.of());

        CollaboratorEventsResponse result = service.findEventsById(2L, principal);
        assertEquals(2L, result.collaboratorId());
        assertEquals("Ana Silva", result.collaboratorName());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void rejectsInvalidOrOutOfScopeCollaboratorIdsBeforeLoadingEvents() {
        assertThrows(BusinessException.class, () -> service.findEventsById(0L, principal));
        when(repository.findDetailsInScope(999L, "manager", 3L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findEventsById(999L, principal));
        verify(repository, never()).findEventsInScope(anyLong(), anyString());
    }

    private CollaboratorEventProjection eventRow(Long eventId, String eventTitle, LocalDateTime classLimitDate,
                                                  String completionStatus, String eventStatus) {
        CollaboratorEventProjection row = mock(CollaboratorEventProjection.class);
        when(row.getEventId()).thenReturn(eventId);
        when(row.getEventTitle()).thenReturn(eventTitle);
        when(row.getClassLimitDate()).thenReturn(classLimitDate);
        when(row.getCompletionStatus()).thenReturn(completionStatus);
        when(row.getEventStatus()).thenReturn(eventStatus);
        when(row.getCompletionMode()).thenReturn(eventId % 2 == 0 ? "GESTOR" : "COLABORADOR");
        return row;
    }

    @Test
    void doesNotLoadProfileDataForInvalidOrOutOfScopeCollaboratorIds() {
        assertThrows(BusinessException.class, () -> service.findProfileById(0L, principal));
        when(repository.findDetailsInScope(999L, "manager", 3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findProfileById(999L, principal));
        verify(repository, never()).findPendingCountById(anyString(), anyLong());
        verify(userService, never()).findNrsByUserId(anyLong());
        verifyNoInteractions(avatars);
    }

    @Test
    void givesTheSameNotFoundResponseForMissingAndOutOfScopeDetails() {
        for (Long id : List.of(2L, 999L)) {
            when(repository.findDetailsInScope(id, "manager", 3L)).thenReturn(Optional.empty());
            ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class, () -> service.findById(id, principal));
            assertEquals("Colaborador não encontrado", error.getMessage());
        }
        assertThrows(BusinessException.class, () -> service.findById(0L, principal));
        verifyNoInteractions(avatars);
    }

    CreateCollaboratorRequest createBody() {
        return new CreateCollaboratorRequest("  Colaborador teste  ", "529.982.247-25", " TEST@EXAMPLE.COM ", 5L, 12L, WorkModel.HIBRIDO);
    }

    @Test
    void createsPreRegistrationAndPublishesTheExistingActivationQueueEvent() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            assertEquals(UserType.COLABORADOR, user.getType());
            assertEquals(UserStatus.PRE_CADASTRADO, user.getStatus());
            assertNull(user.getFirebaseUid());
            assertNotNull(user.getCreatedAt());
            return user;
        });
        CollaboratorDetailsResponse response = service.create(createBody(), principal);
        assertEquals("52998224725", response.cpf());
        assertEquals("test@example.com", response.email());
        assertEquals("Colaborador teste", response.name());
        assertEquals(UserStatus.PRE_CADASTRADO, response.status());
        verify(events).publishEvent(new PreRegisteredCollaboratorsCreatedEvent(List.of("test@example.com")));
    }

    @Test
    void rejectsCpfAndEmailConflictsBeforePersistence() {
        when(repository.cpfExists("52998224725", null)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(createBody(), principal));
        when(repository.cpfExists("52998224725", null)).thenReturn(false);
        when(repository.emailExists("test@example.com", null)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(createBody(), principal));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void rejectsMissingInactiveOrForeignRolesAndUnitsAndDeniedDestinations() {
        when(roles.findById(5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(createBody(), principal));
        when(roles.findById(5L)).thenReturn(Optional.of(role));
        role.setActive(false);
        assertThrows(BusinessException.class, () -> service.create(createBody(), principal));
        role.setActive(true);
        Workspace foreign = new Workspace(); foreign.id = 999L;
        role.setWorkspace(foreign);
        assertThrows(ResourceNotFoundException.class, () -> service.create(createBody(), principal));
        role.setWorkspace(workspace);
        when(units.findById(12L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(createBody(), principal));
        when(units.findById(12L)).thenReturn(Optional.of(destination));
        destination.isActive = false;
        assertThrows(BusinessException.class, () -> service.create(createBody(), principal));
        destination.isActive = true;
        destination.workspace = foreign;
        assertThrows(ResourceNotFoundException.class, () -> service.create(createBody(), principal));
        destination.workspace = workspace;
        when(repository.canAccessUnit("manager", 12L)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.create(createBody(), principal));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void translatesKnownDatabaseConflictsAndDoesNotQueueFailedWrites() {
        for (String constraint : List.of("uq_usuario_cpf", "uq_usuario_email")) {
            SQLException sqlError = new SQLException("unique conflict", "23505");
            ConstraintViolationException violation = new ConstraintViolationException("unique conflict", sqlError, constraint);
            doThrow(new org.springframework.dao.DataIntegrityViolationException("write failed", violation)).when(repository).saveAndFlush(any());
            ConflictException error = assertThrows(ConflictException.class, () -> service.create(createBody(), principal));
            assertTrue(error.getMessage().contains(constraint.endsWith("cpf") ? "CPF" : "e-mail"));
        }
        verifyNoInteractions(events);
    }

    User collaborator(UserStatus status, String firebaseUid) {
        User user = new User();
        user.setId(2L);
        user.setName("Nome anterior");
        user.setCpf("52998224725");
        user.setEmail("old@example.com");
        user.setType(UserType.COLABORADOR);
        user.setRole(role);
        user.setUnit(destination);
        user.setStatus(status);
        user.setFirebaseUid(firebaseUid);
        user.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(repository.findForUpdateInScope(2L, "manager", 3L)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.readEmailState("completed")).thenReturn(new FirebaseIdentityService.EmailState("old@example.com", true));
        return user;
    }

    UpdateCollaboratorRequest updateBody(UserStatus status) {
        return new UpdateCollaboratorRequest("Novo nome", "529.982.247-25", " NEW@EXAMPLE.COM ", 5L, 12L, WorkModel.REMOTO, status);
    }

    @Test
    void updatesOnlyEditableFieldsAndCoordinatesFirebaseEmailBeforeCommit() {
        User user = collaborator(UserStatus.ATIVO, "completed");
        Instant createdAt = user.getCreatedAt();
        CollaboratorDetailsResponse response = service.update(2L, updateBody(UserStatus.ATIVO), principal);
        assertEquals("Novo nome", response.name());
        assertEquals("new@example.com", response.email());
        assertEquals(WorkModel.REMOTO, response.workModel());
        assertEquals(2L, user.getId());
        assertEquals(UserType.COLABORADOR, user.getType());
        assertEquals("completed", user.getFirebaseUid());
        assertEquals(createdAt, user.getCreatedAt());
        InOrder order = inOrder(repository, identities, transactionManager);
        order.verify(repository).saveAndFlush(user);
        order.verify(identities).readEmailState("completed");
        order.verify(identities).updateEmail("completed", "new@example.com");
        order.verify(transactionManager).commit(any());
        verify(identities, never()).restoreEmail(any(), any());
    }

    @Test
    void keepsInactiveLinksWhenUnchangedAndDoesNotUseFirebaseForPreRegistration() {
        collaborator(UserStatus.PRE_CADASTRADO, null);
        role.setActive(false);
        destination.isActive = false;
        CollaboratorDetailsResponse response = service.update(2L, updateBody(UserStatus.PRE_CADASTRADO), principal);
        assertEquals(UserStatus.PRE_CADASTRADO, response.status());
        verifyNoInteractions(identities);
    }

    @Test
    void validatesNewLinksAndDestinationPermissionsBeforeWriting() {
        collaborator(UserStatus.DESATIVADO, "completed");
        Unit other = new Unit(); other.id = 13L; other.workspace = workspace; other.isActive = true;
        when(units.findById(13L)).thenReturn(Optional.of(other));
        UpdateCollaboratorRequest request = new UpdateCollaboratorRequest("Novo nome", "52998224725", "old@example.com", 5L, 13L, WorkModel.HIBRIDO, UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.update(2L, request, principal));
        when(repository.canAccessUnit("manager", 13L)).thenReturn(true);
        other.isActive = false;
        assertThrows(BusinessException.class, () -> service.update(2L, request, principal));
        other.isActive = true;
        assertEquals(13L, service.update(2L, request, principal).unitId());
        verifyNoInteractions(identities);
    }

    @Test
    void rejectsMissingTargetsConflictsAndInvalidStatusTransitions() {
        when(repository.findForUpdateInScope(2L, "manager", 3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(2L, updateBody(UserStatus.ATIVO), principal));
        collaborator(UserStatus.PRE_CADASTRADO, null);
        assertThrows(BusinessException.class, () -> service.update(2L, updateBody(UserStatus.ATIVO), principal));
        when(repository.emailExists("new@example.com", 2L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.update(2L, updateBody(UserStatus.PRE_CADASTRADO), principal));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rollsBackAndRestoresFirebaseForUncertainUpdateFailures() {
        collaborator(UserStatus.ATIVO, "completed");
        doThrow(new IllegalStateException("Firebase unavailable")).when(identities).updateEmail("completed", "new@example.com");
        assertThrows(IllegalStateException.class, () -> service.update(2L, updateBody(UserStatus.ATIVO), principal));
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(identities).restoreEmail("completed", new FirebaseIdentityService.EmailState("old@example.com", true));
    }

    @Test
    void compensatesFirebaseEvenWhenTheDatabaseCommitFails() {
        collaborator(UserStatus.ATIVO, "completed");
        doThrow(new IllegalStateException("Commit failed")).when(transactionManager).commit(any());
        assertThrows(IllegalStateException.class, () -> service.update(2L, updateBody(UserStatus.ATIVO), principal));
        verify(identities).restoreEmail("completed", new FirebaseIdentityService.EmailState("old@example.com", true));
    }

    @Test
    void containsFailedCompensationByDeactivatingOnlyTheAffectedCollaborator() {
        collaborator(UserStatus.ATIVO, "completed");
        doThrow(new IllegalStateException("Firebase unavailable")).when(identities).updateEmail(any(), any());
        doThrow(new IllegalStateException("Compensation unavailable")).when(identities).restoreEmail(any(), any());
        when(repository.deactivateAfterEmailFailure(2L, "completed")).thenReturn(1);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.update(2L, updateBody(UserStatus.ATIVO), principal));
        assertEquals("Falha de sincronização da autenticação do colaborador", error.getMessage());
        verify(repository).deactivateAfterEmailFailure(2L, "completed");
        verify(transactionManager).commit(any());
    }

    @Test
    void appliesExplicitStatusIdempotentlyWithoutChangingOtherFields() {
        User user = collaborator(UserStatus.ATIVO, "completed");
        CollaboratorStatusRequest body = new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.DESATIVADO);
        assertEquals(UserStatus.DESATIVADO, service.changeStatus(2L, body, principal).status());
        assertEquals(UserStatus.DESATIVADO, service.changeStatus(2L, body, principal).status());
        assertEquals("old@example.com", user.getEmail());
        assertEquals("completed", user.getFirebaseUid());
        assertEquals(UserType.COLABORADOR, user.getType());
        verify(repository, times(1)).saveAndFlush(user);
        verifyNoInteractions(identities);
    }

    @Test
    void activatesCompletedUsersOnlyAfterFreshLockedChecksOfTheirLinks() {
        User user = collaborator(UserStatus.DESATIVADO, "completed");
        when(repository.lockRoleActive(5L, 3L)).thenReturn(true);
        when(repository.lockUnitActive(12L, 3L)).thenReturn(false);
        CollaboratorStatusRequest body = new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.ATIVO);
        assertThrows(BusinessException.class, () -> service.changeStatus(2L, body, principal));
        assertEquals(UserStatus.DESATIVADO, user.getStatus());
        when(repository.lockUnitActive(12L, 3L)).thenReturn(true);
        assertEquals(UserStatus.ATIVO, service.changeStatus(2L, body, principal).status());
        verify(repository, times(2)).lockRoleActive(5L, 3L);
        verify(repository, times(2)).lockUnitActive(12L, 3L);
    }

    @Test
    void rejectsAdministrativeActivationForIncompleteAccountsAndOutOfScopeUsers() {
        CollaboratorStatusRequest body = new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.ATIVO);
        collaborator(UserStatus.DESATIVADO, null);
        assertThrows(BusinessException.class, () -> service.changeStatus(2L, body, principal));
        collaborator(UserStatus.PRE_CADASTRADO, "completed");
        assertThrows(BusinessException.class, () -> service.changeStatus(2L, body, principal));
        when(repository.findForUpdateInScope(2L, "manager", 3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.changeStatus(2L, body, principal));
        verify(repository, never()).saveAndFlush(any());
    }
}
