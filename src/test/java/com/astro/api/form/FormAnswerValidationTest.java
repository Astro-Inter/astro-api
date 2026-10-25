package com.astro.api.form;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.internal.FormImage;
import com.astro.api.form.dto.internal.FormOtherAnswer;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.mapper.*;
import com.astro.api.form.model.*;
import com.astro.api.form.service.*;
import com.astro.api.form.strategy.answer.*;
import jakarta.validation.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class FormAnswerValidationTest {
    static ValidatorFactory factory;
    FormAnswerValidationService validation;
    Form form;
    Instant now = Instant.now();
    @BeforeAll static void validatorFactory() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeFactory() { factory.close(); }
    @BeforeEach void setUp() {
        validation = new FormAnswerValidationService(List.of(new TextAnswerStrategy(), new DateAnswerStrategy(),
                new OptionAnswerStrategy(), new PhotoAnswerStrategy()), factory.getValidator());
        form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.PENDING);
        form.setQuestions(List.of());
        new FormMapper().applyContent(FormTestFixtures.content(FormStatus.PENDING), form);
    }
    FormResponseRequest request(List<FormQuestionAnswerRequest> answers) {
        return new FormResponseRequest(true, List.of(new FormLocationReadingRequest(-23.5, -46.6, 4.0, now)), answers);
    }
    List<FormQuestionAnswerRequest> answers() {
        return List.of(new FormQuestionAnswerRequest(1L, "  Condição segura  ", null, null, null, null),
                new FormQuestionAnswerRequest(2L, null, List.of(1L, 2L), "Outra condição", null, null),
                new FormQuestionAnswerRequest(3L, null, null, null, null, List.of(0)),
                new FormQuestionAnswerRequest(4L, null, null, null, LocalDate.of(2026, 10, 24), null));
    }

    @Test void validatesEveryTypeAndRecordsGpsWithoutDistanceEnforcement() {
        Map<Long, List<Object>> normalized = validation.validate(form, request(answers()),
                List.of(new MockMultipartFile("files", "photo.jpg", "image/jpeg", new byte[]{1})), now);
        assertEquals(List.of("Condição segura"), normalized.get(1L));
        assertEquals(List.of(1L, 2L, new FormOtherAnswer("Outra condição")), normalized.get(2L));
        assertEquals(List.of(0), normalized.get(3L));
        assertEquals(List.of("2026-10-24"), normalized.get(4L));
        FormResponseRequest farAway = new FormResponseRequest(true,
                List.of(new FormLocationReadingRequest(80.0, 170.0, 10.0, now)), answers());
        assertDoesNotThrow(() -> validation.validate(form, farAway,
                List.of(new MockMultipartFile("files", new byte[]{1})), now));
    }

    @Test void optionalQuestionsMayBeAbsentButRequiredOnesMayNot() {
        form.getQuestions().forEach(question -> question.setRequired(false));
        assertTrue(validation.validate(form, request(List.of()), List.of(), now).values().stream().allMatch(List::isEmpty));
        form.getQuestions().getFirst().setRequired(true);
        assertThrows(BusinessException.class, () -> validation.validate(form, request(List.of()), List.of(), now));
    }

    @Test void rejectsForgedDuplicateOrIncompatibleAnswers() {
        form.getQuestions().forEach(question -> question.setRequired(false));
        FormQuestionAnswerRequest text = new FormQuestionAnswerRequest(1L, "answer", null, null, null, null);
        for (List<FormQuestionAnswerRequest> invalid : List.of(List.of(text, text),
                List.of(new FormQuestionAnswerRequest(99L, "x", null, null, null, null)),
                List.of(new FormQuestionAnswerRequest(1L, "x", List.of(1L), null, null, null)))) {
            assertThrows(BusinessException.class, () -> validation.validate(form, request(invalid), List.of(), now));
        }
    }

    @Test void validatesOptionsOtherAndSingleSelection() {
        form.getQuestions().forEach(question -> question.setRequired(false));
        form.getQuestions().get(1).setMultiple(false);
        for (FormQuestionAnswerRequest invalid : List.of(
                new FormQuestionAnswerRequest(2L, null, List.of(99L), null, null, null),
                new FormQuestionAnswerRequest(2L, null, List.of(1L, 1L), null, null, null),
                new FormQuestionAnswerRequest(2L, null, List.of(1L, 2L), "Other", null, null),
                new FormQuestionAnswerRequest(2L, null, List.of(2L), "  ", null, null),
                new FormQuestionAnswerRequest(2L, null, List.of(1L), "Unselected other", null, null))) {
            assertThrows(BusinessException.class, () -> validation.validate(form, request(List.of(invalid)), List.of(), now));
        }
    }

    @Test void validatesPhotoReferencesAndNeverIgnoresUnreferencedFiles() {
        form.getQuestions().forEach(question -> question.setRequired(false));
        MockMultipartFile file = new MockMultipartFile("files", new byte[]{1});
        assertThrows(BusinessException.class, () -> validation.validate(form, request(List.of()), List.of(file), now));
        for (List<Integer> indexes : List.of(List.of(1), List.of(0, 0))) {
            FormQuestionAnswerRequest answer = new FormQuestionAnswerRequest(3L, null, null, null, null, indexes);
            assertThrows(BusinessException.class, () -> validation.validate(form, request(List.of(answer)), List.of(file), now));
        }
    }

    @Test void validatesConsentCoordinatesAndCaptureTimesWithPortugueseMessages() {
        form.getQuestions().forEach(question -> question.setRequired(false));
        for (FormResponseRequest invalid : List.of(new FormResponseRequest(false, request(List.of()).locations(), List.of()),
                new FormResponseRequest(true, List.of(), List.of()),
                new FormResponseRequest(true, List.of(new FormLocationReadingRequest(91.0, 0.0, 1.0, now)), List.of()),
                new FormResponseRequest(true, List.of(new FormLocationReadingRequest(0.0, 0.0, 1.0, now.plusSeconds(301))), List.of()))) {
            BusinessException failure = assertThrows(BusinessException.class, () -> validation.validate(form, invalid, List.of(), now));
            assertFalse(failure.getMessage().contains("{form."));
        }
    }

    @Test void mapperKeepsStorageNamesPrivateAndUsesPlainNumbers() {
        FormAnswerMapper mapper = new FormAnswerMapper();
        FormImage image = new FormImage("image-id", "private/key.jpg", "camera.jpg", "image/jpeg", 200);
        mapper.applyAnswers(form, request(answers()), Map.of(1L, List.of("Text"), 2L, List.of(1L),
                3L, List.of(0), 4L, List.of("2026-10-24")), Map.of(0, image));
        Map<?, ?> stored = (Map<?, ?>) form.getQuestions().get(2).getAnswer().getFirst();
        assertEquals("private/key.jpg", stored.get("chave_objeto"));
        assertInstanceOf(Integer.class, stored.get("tamanho_bytes"));
        assertInstanceOf(Integer.class, form.getQuestions().get(1).getAnswer().getFirst());
        Map<?, ?> api = (Map<?, ?>) new FormMapper().toDetails(form).questions().get(2).answer().getFirst();
        assertEquals("image-id", api.get("id")); assertFalse(api.containsKey("chave_objeto"));
        assertEquals(List.of(image), mapper.images(form));
    }
}
