package com.astro.api.form.strategy;

import com.astro.api.form.dto.request.FormQuestionDefinitionRequest;
import com.astro.api.form.model.FormQuestionType;
import org.springframework.stereotype.Component;

@Component
public class DateQuestionStrategy implements FormQuestionStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.DATE; }
    public void validate(FormQuestionDefinitionRequest question, FormValidationMode mode) {
        QuestionRules.noAlternatives(question);
        QuestionRules.noFileLimits(question);
    }
}
