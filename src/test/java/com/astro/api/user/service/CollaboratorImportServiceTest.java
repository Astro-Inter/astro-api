package com.astro.api.user.service;

import com.astro.api.common.exception.SpreadsheetImportException;
import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.model.*;
import com.astro.api.user.model.CollaboratorImportPlan;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.repository.CollaboratorRepository;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import jakarta.validation.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollaboratorImportServiceTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final UserRepository repository = mock(UserRepository.class);
    private final UserBatchService userBatchService = mock(UserBatchService.class);
    private final CollaboratorImportService service = new CollaboratorImportService(repository, new CollaboratorSpreadsheetValidator(FACTORY.getValidator()),
            new CollaboratorMapper(), mock(CollaboratorRepository.class), userBatchService);
    private final Workspace workspace = new Workspace();
    private final Unit unit = new Unit();
    private final Role role = new Role();

    @BeforeEach
    void setup() {
        workspace.id = 3L;
        unit.id = 12L; unit.name = "Matriz"; unit.workspace = workspace; unit.isActive = true;
        role.setId(9L); role.setName("Dev"); role.setWorkspace(workspace); role.setActive(true);
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of());
    }

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test
    void createsUpdatesAndIgnoresInOnePlanWithoutDeletingAbsentPeople() {
        User existing = existing();
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        List<CollaboratorImportRow> rows = List.of(row(2, "Ana", "529.982.247-25", "ana@astro.com"),
                row(3, "Bruno", "11144477735", "bruno@astro.com"));
        CollaboratorImportPlan plan = plan(rows);
        assertEquals(1, plan.unchanged());
        assertEquals(1, plan.changes().size());
        assertNull(plan.changes().getFirst().existingCollaborator());
        verify(repository, never()).saveAllAndFlush(any());
        List<User> persisted = service.persist(plan);
        assertEquals(UserStatus.PRE_CADASTRADO, persisted.getFirst().getStatus());
        assertNull(persisted.getFirst().getFirebaseUid());
        verify(userBatchService).saveAllPreRegistered(persisted);
        verify(repository, never()).saveAllAndFlush(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void usesTheRegistrationBatchOnlyForNewCollaboratorsInAMixedImport() {
        User existing = existing();
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        CollaboratorImportPlan plan = plan(List.of(
                row(2, "Ana Atualizada", "52998224725", "novo@astro.com"),
                row(3, "Bruno", "11144477735", "bruno@astro.com")));

        List<User> changedUsers = service.persist(plan);

        assertEquals(2, changedUsers.size());
        assertSame(existing, changedUsers.getFirst());
        assertEquals(8L, existing.getId());
        assertEquals("firebase", existing.getFirebaseUid());
        assertEquals(UserStatus.ATIVO, existing.getStatus());
        verify(repository).saveAllAndFlush(List.of(existing));
        User newCollaborator = changedUsers.getLast();
        assertEquals(UserStatus.PRE_CADASTRADO, newCollaborator.getStatus());
        assertNull(newCollaborator.getFirebaseUid());
        verify(userBatchService).saveAllPreRegistered(List.of(newCollaborator));
    }

    @Test
    void matchesByCpfEvenWhenEmailChangesAndPreservesIdentityAndStatus() {
        User existing = existing(); existing.setStatus(UserStatus.DESATIVADO);
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        CollaboratorImportPlan plan = plan(List.of(row(2, "Ana Nova", "52998224725", "novo@astro.com")));
        assertSame(existing, plan.changes().getFirst().existingCollaborator());
        service.persist(plan);
        assertEquals("novo@astro.com", existing.getEmail());
        assertEquals("firebase", existing.getFirebaseUid());
        assertEquals(8L, existing.getId());
        assertEquals(UserStatus.DESATIVADO, existing.getStatus());
        assertEquals(Instant.EPOCH, existing.getCreatedAt());
        assertEquals(UserType.COLABORADOR, existing.getType());
    }

    @Test
    void repeatingAnIdenticalFileDoesNotWriteOrCreateInvites() {
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing()));
        CollaboratorImportPlan plan = plan(List.of(row(2, "Ana", "52998224725", "ANA@astro.com")));
        assertEquals(1, plan.unchanged());
        assertTrue(plan.changes().isEmpty());
        service.persist(plan);
        verify(repository, never()).saveAllAndFlush(any());
        verifyNoInteractions(userBatchService);
    }

    @Test
    void detectsCrossWorkspaceCpfAndEmailOwnershipConflicts() {
        User foreign = existing(); foreign.getUnit().workspace = new Workspace(); foreign.getUnit().workspace.id = 99L;
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(foreign));
        assertThrows(SpreadsheetImportException.class, () -> plan(List.of(row(2, "Ana", "52998224725", "novo@astro.com"))));
        assertThrows(SpreadsheetImportException.class, () -> plan(List.of(row(2, "Bruno", "11144477735", "ana@astro.com"))));
        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void distinguishesIdenticalDuplicatesFromConflictingRows() {
        CollaboratorImportRow row = row(2, "Ana", "52998224725", "ana@astro.com");
        assertEquals(1, plan(List.of(row, row)).ignored().size());
        assertThrows(SpreadsheetImportException.class, () -> plan(List.of(row,
                row(3, "Ana Nova", "52998224725", "ana@astro.com"))));
    }

    @Test
    void requiresNewUnitAddressAndPreservesExistingInactiveLinks() {
        CollaboratorImportRow missing = new CollaboratorImportRow(2, "Ana", "52998224725", "ana@astro.com", "Filial", "Dev", "Remoto");
        SpreadsheetImportException failure = assertThrows(SpreadsheetImportException.class, () -> plan(List.of(missing)));
        assertEquals("unidade", failure.getImportErrors().getFirst().field());
        unit.isActive = false; role.setActive(false);
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing()));
        assertEquals(1, plan(List.of(row(2, "Ana", "52998224725", "ana@astro.com"))).unchanged());
    }

    @Test
    void changesUnitAndRoleWhileKeepingUserIdentity() {
        User existing = existing();
        Unit destination = new Unit(); destination.id = 13L; destination.name = "Filial";
        destination.workspace = workspace; destination.isActive = true;
        Role destinationRole = new Role(); destinationRole.setId(10L); destinationRole.setName("Analista");
        destinationRole.setWorkspace(workspace); destinationRole.setActive(true);
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        CollaboratorImportRow row = new CollaboratorImportRow(2, "Ana", "52998224725", "ana@astro.com", "Filial", "Analista", "Híbrido");
        CollaboratorImportPlan plan = service.plan(workspace, List.of(row), Map.of("filial", destination),
                Map.of("analista", destinationRole));
        service.persist(plan);
        assertSame(destination, existing.getUnit());
        assertSame(destinationRole, existing.getRole());
        assertEquals(WorkModel.HIBRIDO, existing.getWorkModel());
        assertEquals(8L, existing.getId());
        assertEquals("firebase", existing.getFirebaseUid());
    }

    @Test
    void neverRecreatesOrDemotesManagerFromInitialSpreadsheet() {
        User manager = existing(); manager.setType(UserType.GESTOR_WORKSPACE); manager.setCpf(null);
        when(repository.findImportIdentitiesForUpdate(anyCollection(), anyCollection())).thenReturn(List.of(manager));
        CollaboratorImportPlan plan = plan(List.of(row(2, "Ana", "", "ana@astro.com")));
        assertTrue(plan.changes().isEmpty());
        assertEquals(1, plan.ignored().size());
    }

    private CollaboratorImportPlan plan(List<CollaboratorImportRow> rows) {
        return service.plan(workspace, rows, Map.of("matriz", unit), Map.of("dev", role));
    }

    private CollaboratorImportRow row(int number, String name, String cpf, String email) {
        return new CollaboratorImportRow(number, name, cpf, email, "Matriz", "Dev", "Remoto");
    }

    private User existing() {
        User user = new User(); user.setId(8L); user.setName("Ana"); user.setCpf("52998224725");
        user.setEmail("ana@astro.com"); user.setFirebaseUid("firebase"); user.setUnit(unit); user.setRole(role);
        user.setWorkModel(WorkModel.REMOTO); user.setType(UserType.COLABORADOR);
        user.setStatus(UserStatus.ATIVO); user.setCreatedAt(Instant.EPOCH);
        return user;
    }
}
