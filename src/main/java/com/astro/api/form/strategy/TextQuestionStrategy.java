package com.astro.api.form.strategy;

import com.astro.api.form.dto.request.FormQuestionRequest;
import com.astro.api.form.model.FormQuestionType;
import org.springframework.stereotype.Component;

@Component
public class TextQuestionStrategy implements FormQuestionStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.TEXT; }
    public void validate(FormQuestionRequest question, FormValidationMode mode) {
        QuestionRules.noAlternatives(question);
        QuestionRules.noFileLimits(question);
    }
}
