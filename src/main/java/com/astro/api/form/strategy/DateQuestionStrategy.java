package com.astro.api.form.strategy;

import com.astro.api.form.dto.request.FormQuestionRequest;
import com.astro.api.form.model.FormQuestionType;
import org.springframework.stereotype.Component;

@Component
public class DateQuestionStrategy implements FormQuestionStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.DATE; }
    public void validate(FormQuestionRequest question, FormValidationMode mode) {
        QuestionRules.noAlternatives(question);
        QuestionRules.noFileLimits(question);
    }
}
