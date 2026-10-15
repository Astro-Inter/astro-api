package com.astro.api.form.strategy;

import com.astro.api.form.dto.request.FormQuestionRequest;
import com.astro.api.form.model.FormQuestionType;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PhotoQuestionStrategy implements FormQuestionStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.PHOTO; }
    public void validate(FormQuestionRequest question, FormValidationMode mode) {
        QuestionRules.noAlternatives(question);
        QuestionRules.require(question.maxFiles() == null || (question.maxFiles() >= 1 && question.maxFiles() <= 10),
                "O limite de fotos deve ser de 1 a 10 arquivos");
        QuestionRules.require(question.maxFileSizeMb() == null || Set.of(5, 10, 20, 50, 100).contains(question.maxFileSizeMb()),
                "O tamanho por foto deve ser 5, 10, 20, 50 ou 100 MB");
    }
}
