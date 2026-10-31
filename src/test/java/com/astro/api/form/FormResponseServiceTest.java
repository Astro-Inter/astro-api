package com.astro.api.form;

import com.astro.api.auth.security.*;
import com.astro.api.common.service.ViewedService;
import com.astro.api.common.exception.*;
import com.astro.api.form.dto.internal.FormImage;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.dto.response.FormResponseDetailsResponse;
import com.astro.api.form.dto.response.FormReceivedPageResponse;
import com.astro.api.form.mapper.*;
import com.astro.api.form.model.*;
import com.astro.api.form.repository.*;
import com.astro.api.form.repository.FormReadFilter;
import com.astro.api.form.repository.FormResponseCondition;
import com.astro.api.form.repository.FormWriteCondition;
import com.astro.api.form.service.*;
import com.astro.api.form.strategy.answer.*;
import com.astro.api.unit.repository.UnitLocationProjection;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import jakarta.validation.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormResponseServiceTest {
    static ValidatorFactory factory;
    FormRepository repository;
    FormImageStorageService storage;
    UserRepository users;
    UnitRepository unitRepository;
    FormResponseService service;
    ViewedService viewedService;
    Form form;
    final AuthenticatedUser recipient = new AuthenticatedUser("recipient", "recipient@example.test", Role.GESTOR);
    final AuthenticatedUser sender = new AuthenticatedUser("sender", "sender@example.test", Role.GESTOR);
    final AuthenticatedUser owner = new AuthenticatedUser("owner", "owner@example.test", Role.GESTOR_WORKSPACE);
    @BeforeAll static void validatorFactory() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeFactory() { factory.close(); }
    @BeforeEach void setUp() {
        unitRepository = mock(UnitRepository.class);
        viewedService = mock(ViewedService.class);
        repository = mock(FormRepository.class); storage = mock(FormImageStorageService.class); users = mock(UserRepository.class);
        when(users.findByFirebaseUid("recipient")).thenReturn(Optional.of(FormTestFixtures.manager(20, 1, UserType.GESTOR)));
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(FormTestFixtures.manager(10, 1, UserType.GESTOR)));
        when(users.findByFirebaseUid("owner")).thenReturn(Optional.of(FormTestFixtures.manager(99, 1, UserType.GESTOR_WORKSPACE)));
        service = new FormResponseService(repository, new FormAccessService(users),
                new FormAnswerValidationService(List.of(new TextAnswerStrategy(), new DateAnswerStrategy(),
                        new OptionAnswerStrategy(), new PhotoAnswerStrategy()), factory.getValidator()),
                new FormAnswerMapper(), new FormResponseMapper(new FormMapper()), storage, users, unitRepository, factory.getValidator(), new FormLocationService(unitRepository), viewedService, mock(com.astro.api.conformity.service.FormConformityService.class));
        form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.PENDING); form.setVersion(0L);
        form.getQuestions().getFirst().setType("texto"); form.getQuestions().getFirst().setRequired(true);
        when(repository.findById(eq(form.getId()), any(FormReadFilter.class))).thenReturn(Optional.of(form));
    }
    FormResponseRequest answer() {
        return new FormResponseRequest(List.of(new FormQuestionAnswerRequest(1L, "Tudo certo", null, null, null, null)));
    }
    void complete() { form.setStatus(FormStatus.COMPLETED); form.setRespondedAt(Instant.now()); }
    @Test void recipientSubmissionUsesOwnIdentityAndAnAtomicCondition() {
        when(repository.submitResponse(any(), any())).thenAnswer(invocation -> {
            Form saved = invocation.getArgument(0); saved.setStatus(FormStatus.COMPLETED); saved.setVersion(1L); return Optional.of(saved);
        });
        assertEquals("awaiting_review", service.submit(form.getId(), answer(), List.of(), recipient).review().status());
        ArgumentCaptor<FormResponseCondition> condition = ArgumentCaptor.forClass(FormResponseCondition.class);
        verify(repository).submitResponse(eq(form), condition.capture());
        assertEquals(new FormResponseCondition(form.getId(), 1L, 20L, 0L, FormStatus.PENDING), condition.getValue());
        assertEquals(List.of("Tudo certo"), form.getQuestions().getFirst().getAnswer());
        assertNull(form.getLocationConsent()); assertNull(form.getLocations());
        verifyNoInteractions(storage);
        assertThrows(ConflictException.class, () -> service.submit(form.getId(), answer(), List.of(), recipient));
    }

    @Test void recipientFilterHasNoWorkspaceManagerBypass() {
        when(repository.findById(eq(form.getId()), any(FormReadFilter.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findReceivedById(form.getId(), owner));
        verify(repository).findById(form.getId(), new FormReadFilter(1L, null, 99L, List.of(FormStatus.PENDING, FormStatus.COMPLETED)));
        verifyNoInteractions(viewedService);
    }

    @Test void receivedListUsesAuthenticatedScopeAndSupportsEmptyResults() {
        when(repository.findReceived(new FormReadFilter(1L, null, 20L, List.of(FormStatus.PENDING, FormStatus.COMPLETED)), "inspeção", 6, FormStatus.PENDING, 0, 20))
                .thenReturn(new FormRepositoryCustom.Listing(List.of(), 0));
        assertTrue(service.findReceived(recipient, " inspeção ", 6, FormStatus.PENDING, 0, 20).items().isEmpty());
        assertThrows(BusinessException.class, () -> service.findReceived(recipient, null, null, FormStatus.DRAFT, 0, 20));
    }

    @Test void receivedListIncludesViewedAndCompletedRejectedFormsWithoutAStatusFilter() {
        Form rejected = FormTestFixtures.form(2, 1, 10, 20, FormStatus.COMPLETED);
        rejected.setReview(new FormReview(FormReviewStatus.REJECTED, 10L, Instant.now(), "Reprovado"));
        when(repository.findReceived(any(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new FormRepositoryCustom.Listing(List.of(form, rejected), 2));
        when(viewedService.findViewed(20L, "formularios", List.of(form.getId(), rejected.getId())))
                .thenReturn(Set.of(rejected.getId()));
        FormReceivedPageResponse page = service.findReceived(recipient, null, null, null, 0, 20);
        assertFalse(page.items().get(0).viewed()); assertTrue(page.items().get(1).viewed());
        assertEquals(FormStatus.COMPLETED, page.items().get(1).status());
        assertEquals("rejected", page.items().get(1).reviewStatus());
        verify(viewedService, never()).markViewed(any(), any(), any());
        assertFalse(service.findReceived(owner, null, null, null, 0, 20).items().get(1).viewed());
    }

    @Test void rejectedFormCannotBeSubmittedAgain() {
        complete(); form.setReview(new FormReview(FormReviewStatus.REJECTED, 10L, Instant.now(), "Reprovado"));
        assertThrows(ConflictException.class, () -> service.submit(form.getId(), answer(), List.of(), recipient));
        verify(repository, never()).submitResponse(any(), any());
    }

    @Test void onlySenderOrWorkspaceManagerCanReviewAndDecisionIsFinal() {
        complete();
        assertThrows(AccessDeniedException.class, () -> service.review(form.getId(), new FormReviewRequest(FormReviewStatus.APPROVED, null), recipient));
        when(repository.review(any(), any())).thenAnswer(invocation -> { form.setReview(invocation.getArgument(1)); return Optional.of(form); });
        assertTrue(service.findResponses(form.getId(), sender).canReview());
        assertFalse(service.findResponses(form.getId(), recipient).canReview());
        assertEquals("approved", service.review(form.getId(), new FormReviewRequest(FormReviewStatus.APPROVED, null), sender).review().status());
        assertNull(form.getReview().description());
        assertThrows(ConflictException.class, () -> service.review(form.getId(), new FormReviewRequest(FormReviewStatus.REJECTED, "Motivo"), owner));
        assertEquals(FormStatus.COMPLETED, form.getStatus());
    }

    @Test void rejectionRequiresDescriptionAndWorkspaceManagerHasAdministrativeAuthority() {
        complete();
        assertThrows(BusinessException.class, () -> service.review(form.getId(), new FormReviewRequest(FormReviewStatus.REJECTED, " "), owner));
        assertThrows(BusinessException.class, () -> service.review(form.getId(), new FormReviewRequest(FormReviewStatus.APPROVED, "Motivo"), owner));
        when(repository.review(any(), any())).thenAnswer(invocation -> { form.setReview(invocation.getArgument(1)); return Optional.of(form); });
        assertEquals("Foto ilegível", service.review(form.getId(), new FormReviewRequest(FormReviewStatus.REJECTED, " Foto ilegível "), owner).review().description());
        ArgumentCaptor<FormWriteCondition> condition = ArgumentCaptor.forClass(FormWriteCondition.class);
        verify(repository).review(condition.capture(), any()); assertNull(condition.getValue().senderManagerId());
    }

    @Test void cannotReviewBeforeReceivingAnswersAndReportsConcurrentReview() {
        assertThrows(ConflictException.class, () -> service.review(form.getId(), new FormReviewRequest(FormReviewStatus.APPROVED, null), sender));
        complete(); when(repository.review(any(), any())).thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> service.review(form.getId(), new FormReviewRequest(FormReviewStatus.APPROVED, null), sender));
    }

    @Test void removesNewImagesWhenAnotherSubmissionWins() {
        form.getQuestions().getFirst().setType("foto");
        FormImage image = new FormImage("id", "key", "photo.jpg", "image/jpeg", 100);
        when(storage.upload(any(), any(), any())).thenReturn(image);
        when(repository.submitResponse(any(), any())).thenReturn(Optional.empty());
        FormResponseRequest response = new FormResponseRequest(List.of(new FormQuestionAnswerRequest(1L, null, null, null, null, List.of(0))));
        assertThrows(ConflictException.class, () -> service.submit(form.getId(), response,
                List.of(new MockMultipartFile("files", new byte[]{1})), recipient));
        verify(storage).delete(image);
    }

    @Test void recoversACommittedResponseAfterLosingTheMongoAcknowledgement() {
        when(repository.submitResponse(any(), any())).thenAnswer(invocation -> {
            form.setStatus(FormStatus.COMPLETED);
            throw new DataAccessResourceFailureException("Lost acknowledgement");
        });
        assertEquals("awaiting_review", service.submit(form.getId(), answer(), List.of(), recipient).review().status());
        assertNotNull(form.getSubmissionId()); verifyNoInteractions(storage);
    }

    @Test void neverDeletesPossiblyCommittedImagesWhenMongoOutcomeCannotBeRecovered() {
        form.getQuestions().getFirst().setType("foto");
        FormImage image = new FormImage("id", "key", "photo.jpg", "image/jpeg", 100);
        when(storage.upload(any(), any(), any())).thenReturn(image);
        when(repository.submitResponse(any(), any())).thenThrow(new DataAccessResourceFailureException("Mongo unavailable"));
        when(repository.findById(eq(form.getId()), any(FormReadFilter.class)))
                .thenReturn(Optional.of(form)).thenThrow(new DataAccessResourceFailureException("Mongo unavailable"));
        FormResponseRequest response = new FormResponseRequest(List.of(new FormQuestionAnswerRequest(1L, null, null, null, null, List.of(0))));
        assertThrows(DataAccessResourceFailureException.class, () -> service.submit(form.getId(), response,
                List.of(new MockMultipartFile("files", new byte[]{1})), recipient));
        verify(storage, never()).delete(any());
    }

    @Test void pendingFormCanBeSubmittedAfterTheDueDateWithoutGps() {
        form.setDueDate(Instant.now().minusSeconds(86400));
        form.setUnitId(3L);
        UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
        when(unitLocation.getLatitude()).thenReturn(0.0); when(unitLocation.getLongitude()).thenReturn(0.0);
        FormResponseRequest response = new FormResponseRequest(answer().answers());
        when(repository.submitResponse(any(), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        FormResponseDetailsResponse result = service.submit(form.getId(), response, List.of(), recipient);
        verify(repository).submitResponse(any(), any());
        assertEquals(FormStatus.COMPLETED, form.getStatus());
        assertNull(form.getLocations());
        assertFalse(result.locationPolicy().captureRequired());
        assertFalse(result.locationPolicy().enforceUnitRadius());
        assertNull(result.locationPolicy().maxReadingAgeSeconds());
        assertEquals(0.0, result.form().unitLocation().latitude());
        assertNull(result.locationCheck()); assertNull(form.getLocationCheck());
    }

    @Test void photoSubmissionDoesNotRequireGpsWithALinkedUnit() {
        form.setUnitId(3L); form.getQuestions().getFirst().setType("foto");
        UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
        when(unitLocation.getLatitude()).thenReturn(0.0); when(unitLocation.getLongitude()).thenReturn(0.0);
        FormImage image = new FormImage("image-id", "private-key", "photo.jpg", "image/jpeg", 100);
        when(storage.upload(any(), any(), any())).thenReturn(image);
        when(repository.submitResponse(any(), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        FormResponseRequest response = new FormResponseRequest(List.of(new FormQuestionAnswerRequest(1L, null, null, null, null, List.of(0))));
        FormResponseDetailsResponse result = service.submit(form.getId(), response,
                List.of(new MockMultipartFile("files", new byte[]{1})), recipient);
        verify(storage).upload(eq(form.getId()), eq(1L), any()); verify(storage, never()).delete(any());
        assertEquals(FormStatus.COMPLETED, result.form().status()); assertNull(form.getLocationCheck());
    }

    @Test void missingUnitCoordinatesDoNotBlockSubmission() {
        form.setUnitId(3L);
        when(repository.submitResponse(any(), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        FormResponseDetailsResponse result = service.submit(form.getId(), answer(), List.of(), recipient);
        assertEquals(FormStatus.COMPLETED, form.getStatus());
        assertNull(result.form().unitLocation()); assertNull(form.getLocationCheck());
        verify(repository).submitResponse(any(), any());
    }

    @Test void mobileDetailsExposeUnitCoordinatesBeforeAnswering() {
        form.setUnitId(3L);
        UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
        when(unitLocation.getLatitude()).thenReturn(-23.5); when(unitLocation.getLongitude()).thenReturn(-46.6);
        FormResponseDetailsResponse result = service.findReceivedById(form.getId(), recipient);
        assertEquals(-23.5, result.form().unitLocation().latitude());
        assertEquals(-46.6, result.form().unitLocation().longitude());
        assertFalse(result.locationPolicy().enforceUnitRadius());
        verify(repository, never()).submitResponse(any(), any());
        verify(viewedService).markViewed(20L, "formularios", form.getId());
    }

    @Test void imageMustBelongToTheAuthorizedForm() {
        assertThrows(ResourceNotFoundException.class, () -> service.image(form.getId(), "forged-image", sender));
        verifyNoInteractions(storage);
    }

    @Test void inactiveAuthenticatedAccountIsBlockedBeforeReadingMongo() {
        when(users.findByFirebaseUid("recipient")).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.findReceivedById(form.getId(), recipient));
        verifyNoInteractions(repository);
    }
}
