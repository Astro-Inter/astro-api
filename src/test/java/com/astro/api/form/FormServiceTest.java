package com.astro.api.form;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.repository.FormRepositoryCustom;
import com.astro.api.form.model.FormScope;
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
import static com.astro.api.form.FormTestFixtures.formId;

class FormServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final FormRepository repository = mock(FormRepository.class);
    final FormAccessService access = new FormAccessService(users);
    final UnitRepository units = mock(UnitRepository.class);
    final NrCatalogRepository nrs = mock(NrCatalogRepository.class);
    final FormService service = new FormService(repository, access, new FormMapper(),
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
        Form form = new Form(); form.setId(formId(1)); form.setTitle("Pesquisa"); form.setStatus(FormStatus.PENDING);
        form.setQuestions(List.of(new Form.FormQuestion(), new Form.FormQuestion()));
        var scope = new FormScope(10L, 1L, false);
        when(repository.findAll(scope, "Pesquisa", FormStatus.PENDING, 1, 4))
                .thenReturn(new FormRepositoryCustom.Listing(List.of(form), 9));
        var result = service.findAll(principal, "Pesquisa", FormStatus.PENDING, 1, 4);
        assertEquals(3, result.totalPages()); assertEquals(9, result.totalElements());
        assertEquals(2, result.items().getFirst().questionCount());
        assertEquals(FormStatus.PENDING, result.items().getFirst().status());
    }

    @Test void detailsRequireAnAccessibleFormAndValidUuid() {
        var scope = new FormScope(10L, 1L, false);
        Form form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.DRAFT);
        form.setDueDate(java.time.Instant.parse("2026-10-30T03:00:00Z"));
        when(repository.findById(formId(1), scope)).thenReturn(Optional.of(form));
        var response = service.findById(formId(1), principal);
        assertEquals(java.time.LocalDate.of(2026, 10, 30), response.dueDate());
        assertEquals(1L, response.questions().getFirst().id());
        assertTrue(response.questions().getFirst().answer().isEmpty());
        assertThrows(BusinessException.class, () -> service.findById("invalid", principal));
        for (String invalid : List.of("1", "000000000000000000000001", "1-1-1-1-1", "z".repeat(36))) {
            assertThrows(BusinessException.class, () -> service.findById(invalid, principal));
        }
        assertThrows(BusinessException.class, () -> service.findById(null, principal));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.findById(formId(99), principal));
    }

    @Test void createsPublishedFormWithGeneratedIdentityAndEmptyEmbeddedAnswers() {
        when(repository.insert(any(Form.class))).thenAnswer(call -> {
            Form inserted = call.getArgument(0);
            assertEquals(4, java.util.UUID.fromString(inserted.getId()).version());
            return inserted;
        });
        var result = service.create(FormTestFixtures.content(FormStatus.PENDING), principal);
        assertEquals(4, java.util.UUID.fromString(result.id()).version()); assertEquals(1L, result.workspaceId()); assertEquals(10L, result.senderManagerId());
        assertEquals(20L, result.managerId()); assertEquals("Inspeção", result.title());
        assertEquals(FormStatus.PENDING, result.status()); assertNotNull(result.createdAt()); assertNull(result.respondedAt());
        assertEquals(0, result.createdAt().getNano() % 1_000_000);
        assertEquals(List.of(1L, 2L, 3L, 4L), result.questions().stream().map(q -> q.id()).toList());
        assertTrue(result.questions().stream().allMatch(q -> q.answer().isEmpty()));
        assertTrue(result.questions().get(1).multiple());
        assertEquals("Outros", result.questions().get(1).options().get(1).text());
        assertEquals(1, result.questions().get(2).maxFiles());
        assertEquals(10, result.questions().get(2).maxFileSizeMb());
    }

    @Test void savesEmptyDraftButDoesNotPublishItOrAcceptCompletedStatus() {
        when(repository.insert(any(Form.class))).thenAnswer(call -> {
            Form inserted = call.getArgument(0);
            assertEquals(4, java.util.UUID.fromString(inserted.getId()).version());
            return inserted;
        });
        var draft = new FormWriteRequest(null, null, null, null, null, null, FormStatus.DRAFT, null);
        var result = service.create(draft, principal);
        assertEquals(FormStatus.DRAFT, result.status()); assertNull(result.managerId()); assertTrue(result.questions().isEmpty());
        clearInvocations(repository);
        assertThrows(BusinessException.class, () -> service.create(new FormWriteRequest(null, null, null, null, null, null, FormStatus.PENDING, null), principal));
        assertThrows(BusinessException.class, () -> service.create(FormTestFixtures.content(FormStatus.COMPLETED), principal));
        verifyNoInteractions(repository);
    }

    @Test void rejectsForeignInactiveAndCollaboratorRecipientsBeforeWriting() {
        when(users.findById(20L)).thenReturn(Optional.of(manager(20, 2, UserType.GESTOR)));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.create(FormTestFixtures.content(FormStatus.PENDING), principal));
        User recipient = manager(20, 1, UserType.COLABORADOR);
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.create(FormTestFixtures.content(FormStatus.PENDING), principal));
        recipient.setType(UserType.GESTOR); recipient.setStatus(UserStatus.DESATIVADO);
        assertThrows(BusinessException.class, () -> service.create(FormTestFixtures.content(FormStatus.PENDING), principal));
        recipient.setStatus(UserStatus.ATIVO); recipient.getUnit().isActive = false;
        assertThrows(BusinessException.class, () -> service.create(FormTestFixtures.content(FormStatus.PENDING), principal));
        verifyNoInteractions(repository);
    }

    @Test void validatesOptionalUnitAndNrAgainstTheirAuthoritativeCatalogs() {
        var body = new FormWriteRequest("Título", null, 20L, 6, 3L, null, FormStatus.PENDING,
                List.of(FormTestFixtures.question(com.astro.api.form.model.FormQuestionType.TEXT)));
        var unit = manager(20, 2, UserType.GESTOR).getUnit();
        when(units.findById(3L)).thenReturn(Optional.of(unit));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.create(body, principal));
        unit.workspace.id = 1L; unit.isActive = false;
        assertThrows(BusinessException.class, () -> service.create(body, principal));
        unit.isActive = true;
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.create(body, principal));
        var nrCatalogEntry = mock(com.astro.api.conformity.model.NrCatalog.class);
        when(nrs.findById(6)).thenReturn(Optional.of(nrCatalogEntry)); when(nrCatalogEntry.isRevoked()).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.create(body, principal));
        verifyNoInteractions(repository);
    }

    Form existing(FormStatus status, long senderId) {
        Form form = new Form();
        new FormMapper().applyContent(FormTestFixtures.content(status), form);
        form.setId(formId(1)); form.setWorkspaceId(1L); form.setSenderManagerId(senderId); form.setStatus(status);
        form.setVersion(0L); form.setCreatedAt(java.time.Instant.parse("2026-10-15T12:00:00Z"));
        when(repository.findById(eq(formId(1)), any())).thenReturn(Optional.of(form));
        return form;
    }

    @Test void senderCanReorderRemoveAndDuplicateQuestionsWhilePreservingExistingIdentifiers() {
        Form form = existing(FormStatus.PENDING, 10);
        var kept = new FormQuestionRequest(4L, com.astro.api.form.model.FormQuestionType.DATE, "Data alterada", true, null, null, null, null);
        var choice = new FormQuestionRequest(2L, com.astro.api.form.model.FormQuestionType.OPTION, "Escolha", false, false,
                List.of(new FormOptionRequest(2L, "", true), new FormOptionRequest(1L, "Sim", false)), null, null);
        var body = new FormWriteRequest("Novo título", null, 20L, null, null, null, null,
                List.of(kept, choice, FormTestFixtures.question(com.astro.api.form.model.FormQuestionType.DATE)));
        when(repository.update(any(), any(), eq(0L))).thenAnswer(call -> Optional.of(call.getArgument(0)));
        var result = service.update(formId(1), body, principal);
        assertEquals(List.of(4L, 2L, 5L), result.questions().stream().map(q -> q.id()).toList());
        assertEquals(List.of(2L, 1L), result.questions().get(1).options().stream().map(o -> o.id()).toList());
        assertEquals(10L, result.senderManagerId()); assertEquals(form.getCreatedAt(), result.createdAt());
        assertNull(result.dueDate()); assertEquals(FormStatus.PENDING, result.status());
    }

    @Test void recipientCannotEditButWorkspaceManagerCanManageAnotherSendersForm() {
        Form form = existing(FormStatus.PENDING, 99);
        form.setManagerId(10L);
        assertThrows(AccessDeniedException.class, () -> service.update(formId(1), FormTestFixtures.content(FormStatus.PENDING), principal));
        verify(repository, never()).update(any(), any(), any());
        sender.setType(UserType.GESTOR_WORKSPACE);
        var owner = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR_WORKSPACE);
        when(repository.update(any(), any(), eq(0L))).thenAnswer(call -> Optional.of(call.getArgument(0)));
        assertEquals(99L, service.update(formId(1), FormTestFixtures.content(FormStatus.PENDING), owner).senderManagerId());
    }

    @Test void editingCannotConcludePublishOrOverwriteConcurrentResponses() {
        Form form = existing(FormStatus.COMPLETED, 10);
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.update(formId(1), FormTestFixtures.content(FormStatus.PENDING), principal));
        form.setStatus(FormStatus.DRAFT);
        assertThrows(BusinessException.class, () -> service.update(formId(1), FormTestFixtures.content(FormStatus.PENDING), principal));
        form.setStatus(FormStatus.PENDING); form.getQuestions().getFirst().setAnswer(List.of("Respondida"));
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.update(formId(1), FormTestFixtures.content(FormStatus.PENDING), principal));
        form.getQuestions().getFirst().setAnswer(List.of());
        when(repository.update(any(), any(), eq(0L))).thenReturn(Optional.empty());
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.update(formId(1), FormTestFixtures.content(FormStatus.PENDING), principal));
    }

    @Test void rejectsUnknownQuestionAndOptionIdsRatherThanTrustingClientProvidedIds() {
        Form form = existing(FormStatus.PENDING, 10);
        var unknownQuestion = new FormQuestionRequest(99L, com.astro.api.form.model.FormQuestionType.TEXT, "Texto", true, null, null, null, null);
        var body = new FormWriteRequest("Título", null, 20L, null, null, null, null, List.of(unknownQuestion));
        assertThrows(BusinessException.class, () -> service.update(formId(1), body, principal));
        var unknownOption = new FormQuestionRequest(2L, com.astro.api.form.model.FormQuestionType.OPTION, "Texto", true, false,
                List.of(new FormOptionRequest(99L, "Sim", false), new FormOptionRequest(null, "Não", false)), null, null);
        var alternativeBody = new FormWriteRequest("Título", null, 20L, null, null, null, null, List.of(unknownOption));
        assertThrows(BusinessException.class, () -> service.update(formId(1), alternativeBody, principal));
        verify(repository, never()).update(any(), any(), any());
    }

    @Test void senderCanDeletePendingAndDraftFormsButNotAnsweredOrCompletedOnes() {
        for (var status : List.of(FormStatus.DRAFT, FormStatus.PENDING)) {
            Form form = existing(status, 10);
            when(repository.delete(eq(form), any())).thenReturn(true);
            assertDoesNotThrow(() -> service.delete(formId(1), principal));
        }
        clearInvocations(repository);
        Form completed = existing(FormStatus.COMPLETED, 10);
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.delete(formId(1), principal));
        completed.setStatus(FormStatus.PENDING); completed.getQuestions().getFirst().setAnswer(List.of("Resposta"));
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.delete(formId(1), principal));
        verify(repository, never()).delete(any(), any());
    }

    @Test void deleteRequiresOwnershipAndDetectsConcurrentChanges() {
        existing(FormStatus.PENDING, 99);
        assertThrows(AccessDeniedException.class, () -> service.delete(formId(1), principal));
        verify(repository, never()).delete(any(), any());
        sender.setType(UserType.GESTOR_WORKSPACE);
        var owner = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR_WORKSPACE);
        when(repository.delete(any(), any())).thenReturn(false);
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.delete(formId(1), owner));
        when(repository.delete(any(), any())).thenReturn(true);
        assertDoesNotThrow(() -> service.delete(formId(1), owner));
    }

    @Test void publicationRevalidatesTheSavedDraftAndPreservesItsEmbeddedConfiguration() {
        Form form = existing(FormStatus.DRAFT, 10);
        when(repository.publish(eq(form), any())).thenAnswer(call -> {
            form.setStatus(FormStatus.PENDING);
            return Optional.of(form);
        });
        var result = service.publish(formId(1), principal);
        assertEquals(FormStatus.PENDING, result.status());
        assertEquals(List.of(1L, 2L, 3L, 4L), result.questions().stream().map(q -> q.id()).toList());
        assertTrue(result.questions().stream().allMatch(q -> q.answer().isEmpty()));
        assertEquals(10L, result.senderManagerId()); assertNull(result.respondedAt());
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.publish(formId(1), principal));
    }

    @Test void publicationRejectsIncompleteDraftAndRecipientThatHasBeenDeactivated() {
        Form form = existing(FormStatus.DRAFT, 10); form.setTitle("");
        assertThrows(BusinessException.class, () -> service.publish(formId(1), principal));
        form.setTitle("Título"); form.getQuestions().getFirst().setQuestion("");
        assertThrows(BusinessException.class, () -> service.publish(formId(1), principal));
        form.getQuestions().getFirst().setQuestion("Pergunta");
        User recipient = manager(20, 1, UserType.GESTOR); recipient.setStatus(UserStatus.DESATIVADO);
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        assertThrows(BusinessException.class, () -> service.publish(formId(1), principal));
        verify(repository, never()).publish(any(), any());
    }

    @Test void onlyTheSenderOrWorkspaceManagerCanPublishAndConcurrentChangesAreNotOverwritten() {
        Form form = existing(FormStatus.DRAFT, 99);
        assertThrows(AccessDeniedException.class, () -> service.publish(formId(1), principal));
        sender.setType(UserType.GESTOR_WORKSPACE);
        var owner = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR_WORKSPACE);
        when(repository.publish(eq(form), any())).thenReturn(Optional.empty());
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.publish(formId(1), owner));
        when(repository.publish(eq(form), any())).thenAnswer(call -> {
            form.setStatus(FormStatus.PENDING); return Optional.of(form);
        });
        assertEquals(99L, service.publish(formId(1), owner).senderManagerId());
    }

    @Test void publicationTranslatesAnUnsupportedStoredQuestionTypeToABusinessError() {
        Form draft = existing(FormStatus.DRAFT, 10);
        draft.getQuestions().getFirst().setType("unsupported");
        var exception = assertThrows(BusinessException.class, () -> service.publish(formId(1), principal));
        assertEquals("Tipo de pergunta inválido no rascunho", exception.getMessage());
        verify(repository, never()).publish(any(), any());
    }
}
