package com.astro.api.form;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.model.*;
import com.astro.api.form.service.FormValidationService;
import com.astro.api.form.strategy.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormValidationTest {
    final FormValidationService validator = FormTestFixtures.validator();

    FormWriteRequest body(FormQuestionRequest question) {
        return new FormWriteRequest("Título", null, 20L, null, null, null, FormStatus.PENDENTE, List.of(question));
    }

    @Test void requiresACompleteAndUnambiguousStrategyRegistry() {
        assertThrows(IllegalStateException.class, () -> new FormValidationService(List.of()));
        assertThrows(IllegalStateException.class, () -> new FormValidationService(List.of(new TextQuestionStrategy(), new TextQuestionStrategy())));
    }

    @Test void permitsIncompleteDraftsAndRequiresQuestionTextForPublication() {
        var question = new FormQuestionRequest(null, FormQuestionType.TEXTO, " ", false, null, null, null, null);
        assertDoesNotThrow(() -> validator.validate(body(question), FormValidationMode.DRAFT));
        assertThrows(BusinessException.class, () -> validator.validate(body(question), FormValidationMode.PUBLISH));
        var tooLong = new FormQuestionRequest(null, FormQuestionType.TEXTO, "a".repeat(141), false, null, null, null, null);
        assertThrows(BusinessException.class, () -> validator.validate(body(tooLong), FormValidationMode.DRAFT));
    }

    @Test void optionStrategyValidatesOtherChoicesBlankTextAndDuplicateIdentifiers() {
        var option = new FormOptionRequest(1L, "Sim", false);
        var other = new FormOptionRequest(2L, "", true);
        var valid = new FormQuestionRequest(null, FormQuestionType.ALTERNATIVA, "Título", true, true, List.of(option, other), null, null);
        assertDoesNotThrow(() -> validator.validate(body(valid), FormValidationMode.PUBLISH));
        for (var options : List.of(List.of(option), List.of(option, option), List.of(other, new FormOptionRequest(3L, "", true)),
                List.of(option, new FormOptionRequest(3L, " ", false)))) {
            var invalid = new FormQuestionRequest(null, FormQuestionType.ALTERNATIVA, "Título", false, false, options, null, null);
            assertThrows(BusinessException.class, () -> validator.validate(body(invalid), FormValidationMode.PUBLISH));
        }
    }

    @Test void photoStrategyChecksTheActualChoicesAndRejectsOptions() {
        for (var question : List.of(
                new FormQuestionRequest(null, FormQuestionType.FOTO, "Foto", false, null, null, 11, 10),
                new FormQuestionRequest(null, FormQuestionType.FOTO, "Foto", false, null, null, 1, 7),
                new FormQuestionRequest(null, FormQuestionType.FOTO, "Foto", false, true, null, 1, 10))) {
            assertThrows(BusinessException.class, () -> validator.validate(body(question), FormValidationMode.DRAFT));
        }
        for (int size : List.of(5, 10, 20, 50, 100)) {
            var question = new FormQuestionRequest(null, FormQuestionType.FOTO, "Foto", false, null, null, 10, size);
            assertDoesNotThrow(() -> validator.validate(body(question), FormValidationMode.PUBLISH));
        }
    }

    @Test void textAndDateRejectConfigurationFromOtherQuestionTypes() {
        for (var type : List.of(FormQuestionType.TEXTO, FormQuestionType.DATA)) {
            assertThrows(BusinessException.class, () -> validator.validate(body(
                    new FormQuestionRequest(null, type, "Título", false, null, null, 1, 10)), FormValidationMode.PUBLISH));
            assertThrows(BusinessException.class, () -> validator.validate(body(
                    new FormQuestionRequest(null, type, "Título", false, true, null, null, null)), FormValidationMode.PUBLISH));
        }
    }

    @Test void duplicateQuestionIdsAndExcessiveCollectionsAreRejectedEvenInDrafts() {
        var question = new FormQuestionRequest(1L, FormQuestionType.TEXTO, "Título", false, null, null, null, null);
        assertThrows(BusinessException.class, () -> validator.validate(new FormWriteRequest(null, null, null, null, null,
                null, FormStatus.RASCUNHO, List.of(question, question)), FormValidationMode.DRAFT));
        assertThrows(BusinessException.class, () -> validator.validate(new FormWriteRequest(null, null, null, null, null,
                null, FormStatus.RASCUNHO, java.util.Collections.nCopies(101, FormTestFixtures.question(FormQuestionType.TEXTO))), FormValidationMode.DRAFT));
    }
}
