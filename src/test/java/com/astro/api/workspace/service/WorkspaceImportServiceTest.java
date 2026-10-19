package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.SpreadsheetImportException;
import com.astro.api.conformity.repository.NrDocumentRepository;
import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleManagementRepository;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.role.service.RoleService;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.mapper.UnitResponseMapper;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.repository.UnitAddressRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.unit.service.UnitSettingsService;
import com.astro.api.unit.service.UnitService;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.model.*;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.repository.*;
import com.astro.api.user.service.CollaboratorImportService;
import com.astro.api.user.service.CollaboratorSpreadsheetReader;
import com.astro.api.user.service.UserBatchService;
import com.astro.api.workspace.dto.request.WorkspaceImportRequest;
import com.astro.api.workspace.dto.response.WorkspaceImportResponse;
import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import com.astro.api.workspace.model.Workspace;
import jakarta.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceImportServiceTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final WorkspaceAccessService access = mock(WorkspaceAccessService.class);
    private final CollaboratorSpreadsheetReader reader = mock(CollaboratorSpreadsheetReader.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UnitRepository units = mock(UnitRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UnitService unitService = mock(UnitService.class);
    private final UserBatchService userBatchService = mock(UserBatchService.class);
    private final UnitSettingsService settings = new UnitSettingsService(access, units,
            mock(UnitAddressRepository.class), new UnitMapper(), new UnitResponseMapper(), unitService);
    private final RoleService roleService = new RoleService(roles, mock(RoleManagementRepository.class),
            mock(NrDocumentRepository.class), JsonMapper.builder().build());
    private final FirebaseIdentityService firebase = mock(FirebaseIdentityService.class);
    private final CollaboratorRepository repository = mock(CollaboratorRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final MultipartFile file = mock(MultipartFile.class);
    private final Workspace workspace = new Workspace();
    private final AuthenticatedUser principal = new AuthenticatedUser("uid", null,
            com.astro.api.auth.security.Role.GESTOR_WORKSPACE);
    private final WorkspaceImportService service = new WorkspaceImportService(access, reader,
            new CollaboratorImportService(users, FACTORY.getValidator(), new CollaboratorMapper(), repository, userBatchService),
            roleService, settings, firebase, events, manager);

    @BeforeEach
    void setup() {
        workspace.id = 3L;
        when(access.requireWorkspace(principal, true)).thenReturn(workspace);
        when(manager.getTransaction(any())).thenAnswer(call -> mock(TransactionStatus.class));
        when(units.findByWorkspace(workspace)).thenReturn(List.of());
        when(roles.findByWorkspace(workspace)).thenReturn(List.of());
        when(users.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of());
        when(repository.lockUnitActive(anyLong(), anyLong())).thenReturn(true);
        when(repository.lockRoleActive(anyLong(), anyLong())).thenReturn(true);
    }

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test
    void createsUnitsAndRolesBeforeBindingNewCollaboratorAndInvitesOnlyNewPeople() {
        CollaboratorImportRow row = row("ana@astro.com");
        when(reader.read(file, 10L * 1024 * 1024)).thenReturn(List.of(row));
        Unit persisted = unit(12L);
        when(unitService.createForWorkspace(eq(workspace), anyList())).thenReturn(List.of(persisted));
        when(roles.saveAll(any())).thenAnswer(call -> {
            List<Role> newRoles = call.getArgument(0);
            newRoles.forEach(role -> role.setId(9L));
            return newRoles;
        });
        WorkspaceImportResponse result = service.importFile(file, body(), principal);
        assertEquals(1, result.created());
        assertEquals(1, result.unitsCreated());
        assertEquals(1, result.rolesCreated());
        verify(userBatchService).saveAllPreRegistered(argThat(changed -> {
            User first = changed.iterator().next();
            return first.getUnit() == persisted && first.getRole().getId() == 9L;
        }));
        verify(events).publishEvent(new PreRegisteredCollaboratorsCreatedEvent(List.of("ana@astro.com")));
        verify(manager).commit(any());
        verifyNoInteractions(firebase);
    }

    @Test
    void validationFailureDoesNotCreateAnyDependencyOrSendInvites() {
        when(reader.read(file, 10L * 1024 * 1024)).thenReturn(List.of(row("ana@astro.com")));
        SpreadsheetImportException failure = assertThrows(SpreadsheetImportException.class,
                () -> service.importFile(file, new WorkspaceImportRequest(null), principal));
        assertEquals("unidade", failure.getImportErrors().getFirst().field());
        verify(unitService, never()).createForWorkspace(any(), anyList());
        verify(roles, never()).saveAll(any());
        verifyNoInteractions(events, firebase, userBatchService);
        verify(manager).rollback(any());
    }

    @Test
    void restoresFirebaseEmailEvenWhenPostgresCommitFails() {
        User existing = existing();
        when(units.findByWorkspace(workspace)).thenReturn(List.of(existing.getUnit()));
        when(roles.findByWorkspace(workspace)).thenReturn(List.of(existing.getRole()));
        when(users.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        when(reader.read(file, 10L * 1024 * 1024)).thenReturn(List.of(row("novo@astro.com")));
        FirebaseIdentityService.EmailState previous = new FirebaseIdentityService.EmailState("ana@astro.com", true);
        when(firebase.readEmailState("firebase")).thenReturn(previous);
        doThrow(new TransactionSystemException("commit failure")).when(manager).commit(any());
        assertThrows(TransactionSystemException.class,
                () -> service.importFile(file, new WorkspaceImportRequest(null), principal));
        InOrder order = inOrder(firebase);
        order.verify(firebase).readEmailState("firebase");
        order.verify(firebase).updateEmail("firebase", "novo@astro.com");
        order.verify(firebase).restoreEmail("firebase", previous);
        verifyNoInteractions(events);
    }

    @Test
    void doesNotWriteOrNotifyWhenExistingDataIsUnchanged() {
        User existing = existing();
        when(units.findByWorkspace(workspace)).thenReturn(List.of(existing.getUnit()));
        when(roles.findByWorkspace(workspace)).thenReturn(List.of(existing.getRole()));
        when(users.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        when(reader.read(file, 10L * 1024 * 1024)).thenReturn(List.of(row("ana@astro.com")));
        WorkspaceImportResponse result = service.importFile(file, new WorkspaceImportRequest(null), principal);
        assertEquals(1, result.unchanged());
        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        verify(users, never()).saveAllAndFlush(any());
        verifyNoInteractions(events, firebase, userBatchService);
        verify(unitService, never()).createForWorkspace(any(), anyList());
    }

    @Test
    void refusesAUnitDeactivatedBetweenPlanningAndWriting() {
        when(reader.read(file, 10L * 1024 * 1024)).thenReturn(List.of(row("ana@astro.com")));
        when(unitService.createForWorkspace(eq(workspace), anyList())).thenReturn(List.of(unit(12L)));
        when(roles.saveAll(any())).thenAnswer(call -> {
            List<Role> newRoles = call.getArgument(0);
            newRoles.forEach(role -> role.setId(9L));
            return newRoles;
        });
        when(repository.lockUnitActive(12L, 3L)).thenReturn(false);
        assertThrows(SpreadsheetImportException.class, () -> service.importFile(file, body(), principal));
        verify(users, never()).saveAllAndFlush(any());
        verifyNoInteractions(events, firebase, userBatchService);
        verify(manager).rollback(any());
    }

    @Test
    void containsIdentityWhenEmailRestorationFails() {
        User existing = existing();
        when(units.findByWorkspace(workspace)).thenReturn(List.of(existing.getUnit()));
        when(roles.findByWorkspace(workspace)).thenReturn(List.of(existing.getRole()));
        when(users.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        when(reader.read(file, 10L * 1024 * 1024)).thenReturn(List.of(row("novo@astro.com")));
        FirebaseIdentityService.EmailState previous = new FirebaseIdentityService.EmailState("ana@astro.com", true);
        when(firebase.readEmailState("firebase")).thenReturn(previous);
        when(users.saveAllAndFlush(any())).thenThrow(new IllegalStateException("Database unavailable"));
        doThrow(new IllegalStateException("Firebase unavailable")).when(firebase).restoreEmail("firebase", previous);
        when(repository.deactivateAfterEmailFailure(8L, "firebase")).thenReturn(1);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.importFile(file, new WorkspaceImportRequest(null), principal));
        assertTrue(failure.getMessage().contains("reconciliação"));
        verify(repository).deactivateAfterEmailFailure(8L, "firebase");
        verifyNoInteractions(events);
    }

    @Test
    void rollsBackAndRestoresExistingEmailWhenTheNewCollaboratorBatchFails() {
        User existing = existing();
        when(units.findByWorkspace(workspace)).thenReturn(List.of(existing.getUnit()));
        when(roles.findByWorkspace(workspace)).thenReturn(List.of(existing.getRole()));
        when(users.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        when(reader.read(file, 10L * 1024 * 1024)).thenReturn(List.of(row("novo@astro.com"),
                new CollaboratorImportRow(3, "Bruno", "11144477735", "bruno@astro.com", "Filial", "Dev", "Remoto")));
        FirebaseIdentityService.EmailState previousEmailState = new FirebaseIdentityService.EmailState("ana@astro.com", true);
        when(firebase.readEmailState("firebase")).thenReturn(previousEmailState);
        doThrow(new IllegalStateException("Batch unavailable")).when(userBatchService).saveAllPreRegistered(anyList());

        assertThrows(IllegalStateException.class,
                () -> service.importFile(file, new WorkspaceImportRequest(null), principal));

        verify(users).saveAllAndFlush(List.of(existing));
        verify(firebase).restoreEmail("firebase", previousEmailState);
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
        verifyNoInteractions(events);
    }

    @Test
    void restoresEarlierEmailsWhenLaterFirebaseUpdateConflicts() {
        User first = existing();
        User second = existing(); second.setId(9L); second.setCpf("11144477735");
        second.setEmail("bruno@astro.com"); second.setFirebaseUid("second");
        when(units.findByWorkspace(workspace)).thenReturn(List.of(first.getUnit()));
        when(roles.findByWorkspace(workspace)).thenReturn(List.of(first.getRole()));
        when(users.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(first, second));
        when(reader.read(file, 10L * 1024 * 1024)).thenReturn(List.of(row("novo@astro.com"),
                new CollaboratorImportRow(3, "Bruno", "11144477735", "bruno.novo@astro.com", "Filial", "Dev", "Remoto")));
        FirebaseIdentityService.EmailState previous = new FirebaseIdentityService.EmailState("ana@astro.com", true);
        FirebaseIdentityService.EmailState secondPrevious = new FirebaseIdentityService.EmailState("bruno@astro.com", false);
        when(firebase.readEmailState("firebase")).thenReturn(previous);
        when(firebase.readEmailState("second")).thenReturn(secondPrevious);
        doThrow(new com.astro.api.common.exception.ConflictException("Email ocupado"))
                .when(firebase).updateEmail("second", "bruno.novo@astro.com");
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.importFile(file, new WorkspaceImportRequest(null), principal));
        verify(firebase).restoreEmail("firebase", previous);
        verify(firebase).restoreEmail("second", secondPrevious);
        verify(users, never()).saveAllAndFlush(any());
        verify(manager).rollback(any());
    }

    private WorkspaceImportRequest body() {
        return new WorkspaceImportRequest(List.of(new UnitRequest("Filial", "01234567", "SP",
                "São Paulo", "Centro", "Rua A", "10", null)));
    }

    private CollaboratorImportRow row(String email) {
        return new CollaboratorImportRow(2, "Ana", "52998224725", email, "Filial", "Dev", "Remoto");
    }

    private Unit unit(Long id) {
        Unit unit = new Unit(); unit.id = id; unit.name = "Filial"; unit.workspace = workspace; unit.isActive = true;
        return unit;
    }

    private User existing() {
        Role role = new Role(); role.setId(9L); role.setName("Dev"); role.setWorkspace(workspace); role.setActive(true);
        User user = new User(); user.setId(8L); user.setName("Ana"); user.setCpf("52998224725");
        user.setEmail("ana@astro.com"); user.setFirebaseUid("firebase"); user.setUnit(unit(12L)); user.setRole(role);
        user.setType(UserType.COLABORADOR); user.setStatus(UserStatus.ATIVO); user.setWorkModel(WorkModel.REMOTO);
        return user;
    }
}
