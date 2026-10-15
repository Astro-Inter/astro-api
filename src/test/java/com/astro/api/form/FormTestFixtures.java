package com.astro.api.form;

import com.astro.api.form.dto.request.*;
import com.astro.api.form.model.*;
import com.astro.api.form.service.FormValidationService;
import com.astro.api.form.strategy.*;

import java.time.LocalDate;
import java.util.List;

final class FormTestFixtures {
    private FormTestFixtures() {}

    static FormValidationService validator() {
        return new FormValidationService(List.of(new TextQuestionStrategy(), new OptionQuestionStrategy(),
                new PhotoQuestionStrategy(), new DateQuestionStrategy()));
    }

    static FormQuestionRequest question(FormQuestionType type) {
        return new FormQuestionRequest(null, type, " Pergunta ", true,
                type == FormQuestionType.ALTERNATIVA ? true : null,
                type == FormQuestionType.ALTERNATIVA ? List.of(new FormOptionRequest(null, "Sim", false),
                        new FormOptionRequest(null, "", true)) : null, null, null);
    }

    static FormWriteRequest content(FormStatus status) {
        return new FormWriteRequest(" Inspeção ", " Descrição ", 20L, null, null,
                LocalDate.of(2026, 10, 30), status,
                List.of(question(FormQuestionType.TEXTO), question(FormQuestionType.ALTERNATIVA),
                        question(FormQuestionType.FOTO), question(FormQuestionType.DATA)));
    }
}
