package com.astro.api.form;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.repository.FormManagementRepository;
import com.astro.api.form.repository.FormRepository;
import com.astro.api.form.service.FormAccessService;
import com.astro.api.form.service.FormService;
import com.astro.api.form.service.FormReferenceService;
import com.astro.api.form.dto.request.*;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.astro.api.form.FormTestFixtures.manager;

class FormServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final FormManagementRepository repository = mock(FormManagementRepository.class);
    final FormRepository homeRepository = mock(FormRepository.class);
    final FormAccessService access = new FormAccessService(users);
    final UnitRepository units = mock(UnitRepository.class);
    final NrCatalogRepository nrs = mock(NrCatalogRepository.class);
    final FormService service = new FormService(homeRepository, repository, access, new FormMapper(),
            FormTestFixtures.validator(), new FormReferenceService(users, units, nrs));
    final AuthenticatedUser principal = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR);
    User sender;

    @BeforeEach void setUp() {
        sender = manager(10L, 1L, UserType.GESTOR);
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(sender));
        when(users.findById(20L)).thenReturn(Optional.of(manager(20, 1, UserType.GESTOR)));
    }

    @Test void requiresActiveManagerWithValidatedRoleAndActiveUnit() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(null, null, null, 0, 20));
        sender.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal, null, null, 0, 20));
        sender.setStatus(UserStatus.ATIVO); sender.setType(UserType.GESTOR_WORKSPACE);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal, null, null, 0, 20));
        sender.setType(UserType.GESTOR); sender.getUnit().isActive = false;
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal, null, null, 0, 20));
        verifyNoInteractions(repository);
    }

    @Test void boundsPagingAndSearchBeforeQueryingMongo() {
        assertThrows(BusinessException.class, () -> service.findAll(principal, null, null, -1, 20));
        assertThrows(BusinessException.class, () -> service.findAll(principal, null, null, 0, 0));
        assertThrows(BusinessException.class, () -> service.findAll(principal, null, null, 0, 101));
        assertThrows(BusinessException.class, () -> service.findAll(principal, "a".repeat(241), null, 0, 20));
        verifyNoInteractions(repository);
    }

    @Test void returnsSummaryAndPagingWithoutAnswers() {
        Form form = new Form(); form.setId(1L); form.setTitle("Pesquisa"); form.setStatus(FormStatus.PENDENTE);
        form.setQuestions(List.of(new Form.FormQuestion(), new Form.FormQuestion()));
        var scope = new FormAccessService.Scope(10L, 1L, false);
        when(repository.findAll(scope, "Pesquisa", FormStatus.PENDENTE, 1, 4))
                .thenReturn(new FormManagementRepository.Listing(List.of(form), 9));
        var result = service.findAll(principal, "Pesquisa", FormStatus.PENDENTE, 1, 4);
        assertEquals(3, result.totalPages()); assertEquals(9, result.totalElements());
        assertEquals(2, result.items().getFirst().questionCount());
        assertEquals(FormStatus.PENDENTE, result.items().getFirst().status());
    }

    @Test void detailsRequireAnAccessibleFormAndPositiveIdentifier() {
        var scope = new FormAccessService.Scope(10L, 1L, false);
        Form form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.RASCUNHO);
        form.setDueDate(java.time.Instant.parse("2026-10-30T03:00:00Z"));
        when(repository.findById(1L, scope)).thenReturn(Optional.of(form));
        var response = service.findById(1L, principal);
        assertEquals(java.time.LocalDate.of(2026, 10, 30), response.dueDate());
        assertEquals(1L, response.questions().getFirst().id());
        assertTrue(response.questions().getFirst().answer().isEmpty());
        assertThrows(BusinessException.class, () -> service.findById(0L, principal));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.findById(99L, principal));
    }

    @Test void createsPublishedFormWithGeneratedIdentityAndEmptyEmbeddedAnswers() {
        when(repository.nextId()).thenReturn(42L);
        when(repository.insert(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(FormTestFixtures.content(FormStatus.PENDENTE), principal);
        assertEquals(42L, result.id()); assertEquals(1L, result.workspaceId()); assertEquals(10L, result.senderManagerId());
        assertEquals(20L, result.managerId()); assertEquals("Inspeção", result.title());
        assertEquals(FormStatus.PENDENTE, result.status()); assertNotNull(result.createdAt()); assertNull(result.respondedAt());
        assertEquals(0, result.createdAt().getNano() % 1_000_000);
        assertEquals(List.of(1L, 2L, 3L, 4L), result.questions().stream().map(q -> q.id()).toList());
        assertTrue(result.questions().stream().allMatch(q -> q.answer().isEmpty()));
        assertTrue(result.questions().get(1).multiple());
        assertEquals("Outros", result.questions().get(1).options().get(1).text());
        assertEquals(1, result.questions().get(2).maxFiles());
        assertEquals(10, result.questions().get(2).maxFileSizeMb());
    }

    @Test void savesEmptyDraftButDoesNotPublishItOrAcceptCompletedStatus() {
        when(repository.insert(any())).thenAnswer(call -> call.getArgument(0));
        var draft = new FormWriteRequest(null, null, null, null, null, null, FormStatus.RASCUNHO, null);
        var result = service.create(draft, principal);
        assertEquals(FormStatus.RASCUNHO, result.status()); assertNull(result.managerId()); assertTrue(result.questions().isEmpty());
        clearInvocations(repository);
        assertThrows(BusinessException.class, () -> service.create(new FormWriteRequest(null, null, null, null, null, null, FormStatus.PENDENTE, null), principal));
        assertThrows(BusinessException.class, () -> service.create(FormTestFixtures.content(FormStatus.CONCLUIDO), principal));
        verifyNoInteractions(repository);
    }

    @Test void rejectsForeignInactiveAndCollaboratorRecipientsBeforeWriting() {
        when(users.findById(20L)).thenReturn(Optional.of(manager(20, 2, UserType.GESTOR)));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.create(FormTestFixtures.content(FormStatus.PENDENTE), principal));
        User recipient = manager(20, 1, UserType.COLABORADOR);
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.create(FormTestFixtures.content(FormStatus.PENDENTE), principal));
        recipient.setType(UserType.GESTOR); recipient.setStatus(UserStatus.DESATIVADO);
        assertThrows(BusinessException.class, () -> service.create(FormTestFixtures.content(FormStatus.PENDENTE), principal));
        recipient.setStatus(UserStatus.ATIVO); recipient.getUnit().isActive = false;
        assertThrows(BusinessException.class, () -> service.create(FormTestFixtures.content(FormStatus.PENDENTE), principal));
        verifyNoInteractions(repository);
    }

    @Test void validatesOptionalUnitAndNrAgainstTheirAuthoritativeCatalogs() {
        var body = new FormWriteRequest("Título", null, 20L, 6, 3L, null, FormStatus.PENDENTE,
                List.of(FormTestFixtures.question(com.astro.api.form.model.FormQuestionType.TEXTO)));
        var unit = manager(20, 2, UserType.GESTOR).getUnit();
        when(units.findById(3L)).thenReturn(Optional.of(unit));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.create(body, principal));
        unit.workspace.id = 1L; unit.isActive = false;
        assertThrows(BusinessException.class, () -> service.create(body, principal));
        unit.isActive = true;
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.create(body, principal));
        var nr = mock(com.astro.api.conformity.model.NrCatalog.class);
        when(nrs.findById(6)).thenReturn(Optional.of(nr)); when(nr.isRevoked()).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.create(body, principal));
        verifyNoInteractions(repository);
    }

    Form existing(FormStatus status, long senderId) {
        Form form = new Form();
        new FormMapper().applyContent(FormTestFixtures.content(status), form);
        form.setId(1L); form.setWorkspaceId(1L); form.setSenderManagerId(senderId); form.setStatus(status);
        form.setVersion(0L); form.setCreatedAt(java.time.Instant.parse("2026-10-15T12:00:00Z"));
        when(repository.findById(eq(1L), any())).thenReturn(Optional.of(form));
        return form;
    }

    @Test void senderCanReorderRemoveAndDuplicateQuestionsWhilePreservingExistingIdentifiers() {
        Form form = existing(FormStatus.PENDENTE, 10);
        var kept = new FormQuestionRequest(4L, com.astro.api.form.model.FormQuestionType.DATA, "Data alterada", true, null, null, null, null);
        var choice = new FormQuestionRequest(2L, com.astro.api.form.model.FormQuestionType.ALTERNATIVA, "Escolha", false, false,
                List.of(new FormOptionRequest(2L, "", true), new FormOptionRequest(1L, "Sim", false)), null, null);
        var body = new FormWriteRequest("Novo título", null, 20L, null, null, null, null,
                List.of(kept, choice, FormTestFixtures.question(com.astro.api.form.model.FormQuestionType.DATA)));
        when(repository.update(any(), any(), eq(0L))).thenAnswer(call -> Optional.of(call.getArgument(0)));
        var result = service.update(1L, body, principal);
        assertEquals(List.of(4L, 2L, 5L), result.questions().stream().map(q -> q.id()).toList());
        assertEquals(List.of(2L, 1L), result.questions().get(1).options().stream().map(o -> o.id()).toList());
        assertEquals(10L, result.senderManagerId()); assertEquals(form.getCreatedAt(), result.createdAt());
        assertNull(result.dueDate()); assertEquals(FormStatus.PENDENTE, result.status());
    }

    @Test void recipientCannotEditButWorkspaceManagerCanManageAnotherSendersForm() {
        Form form = existing(FormStatus.PENDENTE, 99);
        form.setManagerId(10L);
        assertThrows(AccessDeniedException.class, () -> service.update(1L, FormTestFixtures.content(FormStatus.PENDENTE), principal));
        verify(repository, never()).update(any(), any(), any());
        sender.setType(UserType.GESTOR_WORKSPACE);
        var owner = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR_WORKSPACE);
        when(repository.update(any(), any(), eq(0L))).thenAnswer(call -> Optional.of(call.getArgument(0)));
        assertEquals(99L, service.update(1L, FormTestFixtures.content(FormStatus.PENDENTE), owner).senderManagerId());
    }

    @Test void editingCannotConcludePublishOrOverwriteConcurrentResponses() {
        Form form = existing(FormStatus.CONCLUIDO, 10);
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.update(1L, FormTestFixtures.content(FormStatus.PENDENTE), principal));
        form.setStatus(FormStatus.RASCUNHO);
        assertThrows(BusinessException.class, () -> service.update(1L, FormTestFixtures.content(FormStatus.PENDENTE), principal));
        form.setStatus(FormStatus.PENDENTE); form.getQuestions().getFirst().setAnswer(List.of("Respondida"));
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.update(1L, FormTestFixtures.content(FormStatus.PENDENTE), principal));
        form.getQuestions().getFirst().setAnswer(List.of());
        when(repository.update(any(), any(), eq(0L))).thenReturn(Optional.empty());
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.update(1L, FormTestFixtures.content(FormStatus.PENDENTE), principal));
    }

    @Test void rejectsUnknownQuestionAndOptionIdsRatherThanTrustingClientProvidedIds() {
        Form form = existing(FormStatus.PENDENTE, 10);
        var unknownQuestion = new FormQuestionRequest(99L, com.astro.api.form.model.FormQuestionType.TEXTO, "Texto", true, null, null, null, null);
        var body = new FormWriteRequest("Título", null, 20L, null, null, null, null, List.of(unknownQuestion));
        assertThrows(BusinessException.class, () -> service.update(1L, body, principal));
        var unknownOption = new FormQuestionRequest(2L, com.astro.api.form.model.FormQuestionType.ALTERNATIVA, "Texto", true, false,
                List.of(new FormOptionRequest(99L, "Sim", false), new FormOptionRequest(null, "Não", false)), null, null);
        var alternativeBody = new FormWriteRequest("Título", null, 20L, null, null, null, null, List.of(unknownOption));
        assertThrows(BusinessException.class, () -> service.update(1L, alternativeBody, principal));
        verify(repository, never()).update(any(), any(), any());
    }

    @Test void senderCanDeletePendingAndDraftFormsButNotAnsweredOrCompletedOnes() {
        for (var status : List.of(FormStatus.RASCUNHO, FormStatus.PENDENTE)) {
            Form form = existing(status, 10);
            when(repository.delete(eq(form), any())).thenReturn(true);
            assertDoesNotThrow(() -> service.delete(1L, principal));
        }
        clearInvocations(repository);
        Form completed = existing(FormStatus.CONCLUIDO, 10);
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.delete(1L, principal));
        completed.setStatus(FormStatus.PENDENTE); completed.getQuestions().getFirst().setAnswer(List.of("Resposta"));
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.delete(1L, principal));
        verify(repository, never()).delete(any(), any());
    }

    @Test void deleteRequiresOwnershipAndDetectsConcurrentChanges() {
        existing(FormStatus.PENDENTE, 99);
        assertThrows(AccessDeniedException.class, () -> service.delete(1L, principal));
        verify(repository, never()).delete(any(), any());
        sender.setType(UserType.GESTOR_WORKSPACE);
        var owner = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR_WORKSPACE);
        when(repository.delete(any(), any())).thenReturn(false);
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.delete(1L, owner));
        when(repository.delete(any(), any())).thenReturn(true);
        assertDoesNotThrow(() -> service.delete(1L, owner));
    }

    @Test void publicationRevalidatesTheSavedDraftAndPreservesItsEmbeddedConfiguration() {
        Form form = existing(FormStatus.RASCUNHO, 10);
        when(repository.publish(eq(form), any())).thenAnswer(call -> {
            form.setStatus(FormStatus.PENDENTE);
            return Optional.of(form);
        });
        var result = service.publish(1L, principal);
        assertEquals(FormStatus.PENDENTE, result.status());
        assertEquals(List.of(1L, 2L, 3L, 4L), result.questions().stream().map(q -> q.id()).toList());
        assertTrue(result.questions().stream().allMatch(q -> q.answer().isEmpty()));
        assertEquals(10L, result.senderManagerId()); assertNull(result.respondedAt());
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.publish(1L, principal));
    }

    @Test void publicationRejectsIncompleteDraftAndRecipientThatHasBeenDeactivated() {
        Form form = existing(FormStatus.RASCUNHO, 10); form.setTitle("");
        assertThrows(BusinessException.class, () -> service.publish(1L, principal));
        form.setTitle("Título"); form.getQuestions().getFirst().setQuestion("");
        assertThrows(BusinessException.class, () -> service.publish(1L, principal));
        form.getQuestions().getFirst().setQuestion("Pergunta");
        User recipient = manager(20, 1, UserType.GESTOR); recipient.setStatus(UserStatus.DESATIVADO);
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        assertThrows(BusinessException.class, () -> service.publish(1L, principal));
        verify(repository, never()).publish(any(), any());
    }

    @Test void onlyTheSenderOrWorkspaceManagerCanPublishAndConcurrentChangesAreNotOverwritten() {
        Form form = existing(FormStatus.RASCUNHO, 99);
        assertThrows(AccessDeniedException.class, () -> service.publish(1L, principal));
        sender.setType(UserType.GESTOR_WORKSPACE);
        var owner = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR_WORKSPACE);
        when(repository.publish(eq(form), any())).thenReturn(Optional.empty());
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.publish(1L, owner));
        when(repository.publish(eq(form), any())).thenAnswer(call -> {
            form.setStatus(FormStatus.PENDENTE); return Optional.of(form);
        });
        assertEquals(99L, service.publish(1L, owner).senderManagerId());
    }
}
