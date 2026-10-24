package com.astro.api.form;

import com.astro.api.auth.security.*;
import com.astro.api.common.exception.*;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.mapper.*;
import com.astro.api.form.model.*;
import com.astro.api.form.repository.*;
import com.astro.api.form.service.*;
import com.astro.api.form.strategy.answer.*;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.unit.repository.UnitLocationProjection;
import com.astro.api.form.dto.response.FormResponseDetailsResponse;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormResponseServiceTest {
    static ValidatorFactory factory;
    FormRepository repository;
    FormImageStorageService storage;
    UserRepository users;
    UnitRepository unitRepository;
    FormResponseService service;
    Form form;
    final AuthenticatedUser recipient = new AuthenticatedUser("recipient", "recipient@example.test", Role.GESTOR);
    final AuthenticatedUser sender = new AuthenticatedUser("sender", "sender@example.test", Role.GESTOR);
    final AuthenticatedUser owner = new AuthenticatedUser("owner", "owner@example.test", Role.GESTOR_WORKSPACE);
    @BeforeAll static void validatorFactory() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeFactory() { factory.close(); }
    @BeforeEach void setUp() {
        unitRepository = mock(UnitRepository.class);
        repository = mock(FormRepository.class); storage = mock(FormImageStorageService.class); users = mock(UserRepository.class);
        when(users.findByFirebaseUid("recipient")).thenReturn(Optional.of(FormTestFixtures.manager(20, 1, UserType.GESTOR)));
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(FormTestFixtures.manager(10, 1, UserType.GESTOR)));
        when(users.findByFirebaseUid("owner")).thenReturn(Optional.of(FormTestFixtures.manager(99, 1, UserType.GESTOR_WORKSPACE)));
        service = new FormResponseService(repository, new FormAccessService(users),
                new FormAnswerValidationService(List.of(new TextAnswerStrategy(), new DateAnswerStrategy(),
                        new OptionAnswerStrategy(), new PhotoAnswerStrategy()), factory.getValidator()),
                new FormAnswerMapper(), new FormResponseMapper(new FormMapper()), storage, users, unitRepository, factory.getValidator(), new FormLocationService(unitRepository));
        form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.PENDING); form.setVersion(0L);
        form.getQuestions().getFirst().setType("texto"); form.getQuestions().getFirst().setRequired(true);
        when(repository.findById(eq(form.getId()), any(FormReadFilter.class))).thenReturn(Optional.of(form));
    }
    FormResponseRequest answer() {
        return new FormResponseRequest(true, List.of(new FormLocationReadingRequest(-23.0, -46.0, 4.0, Instant.now())),
                List.of(new FormQuestionAnswerRequest(1L, "Tudo certo", null, null, null, null)));
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
        assertEquals(true, form.getLocationConsent()); assertEquals(1, form.getLocations().size());
        verifyNoInteractions(storage);
        assertThrows(ConflictException.class, () -> service.submit(form.getId(), answer(), List.of(), recipient));
    }
    @Test void recipientFilterHasNoWorkspaceManagerBypass() {
        when(repository.findById(eq(form.getId()), any(FormReadFilter.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findReceivedById(form.getId(), owner));
        verify(repository).findById(form.getId(), new FormReadFilter(1L, null, 99L, List.of(FormStatus.PENDING, FormStatus.COMPLETED)));
    }
    @Test void receivedListUsesAuthenticatedScopeAndSupportsEmptyResults() {
        when(repository.findReceived(new FormReadFilter(1L, null, 20L, List.of(FormStatus.PENDING, FormStatus.COMPLETED)), "inspeção", 6, FormStatus.PENDING, 0, 20))
                .thenReturn(new FormRepositoryCustom.Listing(List.of(), 0));
        assertTrue(service.findReceived(recipient, " inspeção ", 6, FormStatus.PENDING, 0, 20).items().isEmpty());
        assertThrows(BusinessException.class, () -> service.findReceived(recipient, null, null, FormStatus.DRAFT, 0, 20));
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
        FormResponseRequest response = new FormResponseRequest(true, answer().locations(),
                List.of(new FormQuestionAnswerRequest(1L, null, null, null, null, List.of(0))));
        assertThrows(ConflictException.class, () -> service.submit(form.getId(), response,
                List.of(new MockMultipartFile("files", new byte[]{1})), recipient));
        verify(storage).delete(image);
    }
    @Test void recoversACommittedResponseAfterLosingTheMongoAcknowledgement() {
        when(repository.submitResponse(any(), any())).thenAnswer(invocation -> {
            form.setStatus(FormStatus.COMPLETED);
            throw new org.springframework.dao.DataAccessResourceFailureException("Lost acknowledgement");
        });
        assertEquals("awaiting_review", service.submit(form.getId(), answer(), List.of(), recipient).review().status());
        assertNotNull(form.getSubmissionId()); verifyNoInteractions(storage);
    }
    @Test void neverDeletesPossiblyCommittedImagesWhenMongoOutcomeCannotBeRecovered() {
        form.getQuestions().getFirst().setType("foto");
        FormImage image = new FormImage("id", "key", "photo.jpg", "image/jpeg", 100);
        when(storage.upload(any(), any(), any())).thenReturn(image);
        when(repository.submitResponse(any(), any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Mongo unavailable"));
        when(repository.findById(eq(form.getId()), any(FormReadFilter.class)))
                .thenReturn(Optional.of(form)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Mongo unavailable"));
        FormResponseRequest response = new FormResponseRequest(true, answer().locations(),
                List.of(new FormQuestionAnswerRequest(1L, null, null, null, null, List.of(0))));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class, () -> service.submit(form.getId(), response,
                List.of(new MockMultipartFile("files", new byte[]{1})), recipient));
        verify(storage, never()).delete(any());
    }
    @Test void checksTheBoundUnitBeforeAnyUploadOrMongoWrite() {
        form.setUnitId(3L);
        UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
        when(unitLocation.getActive()).thenReturn(true); when(unitLocation.getUnitId()).thenReturn(3L);
        when(unitLocation.getLatitude()).thenReturn(0.0); when(unitLocation.getLongitude()).thenReturn(0.0);
        assertThrows(BusinessException.class, () -> service.submit(form.getId(), answer(), List.of(), recipient));
        verify(repository, never()).submitResponse(any(), any()); verifyNoInteractions(storage);
        assertEquals(FormStatus.PENDING, form.getStatus()); assertTrue(form.getQuestions().getFirst().getAnswer().isEmpty());
        FormResponseRequest nearResponse = new FormResponseRequest(true,
                List.of(new FormLocationReadingRequest(0.0, 0.0, 5.0, Instant.now())), answer().answers());
        when(repository.submitResponse(any(), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        FormResponseDetailsResponse result = service.submit(form.getId(), nearResponse, List.of(), recipient);
        assertTrue(result.locationPolicy().enforceUnitRadius()); assertEquals(100, result.locationPolicy().radiusMeters());
        assertEquals(0.0, result.locationCheck().distanceMeters()); assertEquals(3L, form.getLocationCheck().unitId());
    }

    @Test void missingUnitCoordinatesCannotSilentlyDisableTheRadius() {
        form.setUnitId(3L);
        UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
        when(unitLocation.getActive()).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.submit(form.getId(), answer(), List.of(), recipient));
        verify(repository, never()).submitResponse(any(), any()); verifyNoInteractions(storage);
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
